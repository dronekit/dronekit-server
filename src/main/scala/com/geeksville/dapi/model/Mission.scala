package com.geeksville.dapi.model

import com.github.aselab.activerecord.Datestamps
import com.github.aselab.activerecord.annotations._
import org.squeryl.annotations.Transient
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import com.github.aselab.activerecord.dsl._
import com.geeksville.dapi.AccessCode
import java.util.Date
import java.text.SimpleDateFormat
import com.geeksville.util.CacheUtil._
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.cache.Cache
import com.geeksville.util.Using
import com.google.common.io.ByteStreams
import java.io.InputStream
import grizzled.slf4j.Logging
import com.geeksville.dapi.TLOGPlaybackModel
import com.amazonaws.services.s3.model.AmazonS3Exception
import java.io.ByteArrayInputStream
import com.github.aselab.activerecord.ActiveRecord
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s._
import com.geeksville.mapbox.MapboxClient
import java.text.DecimalFormat
import com.geeksville.dapi.LiveVehicleActor
import scala.concurrent.duration._
import akka.pattern.ask
import scala.concurrent.Await
import com.geeksville.dapi.GetTLogMessage
import akka.util.Timeout
import com.geeksville.dapi.Global
import com.geeksville.apiproxy.APIConstants
import java.sql.Timestamp
import com.geeksville.dapi.SpaceSupervisor
import com.geeksville.dapi.MissionDelete
import com.geeksville.util.AnalyticsService
import com.geeksville.dapi.PlaybackModel
import com.geeksville.dapi.DataflashPlaybackModel
import com.github.aselab.activerecord.RecordNotFoundException
import com.geeksville.doarama.DoaramaClient
import java.io.ByteArrayOutputStream
import com.geeksville.util.ThreadTools
import org.squeryl.SquerylSQLException
import com.geeksville.dapi.ApiController

/**
 * Stats which cover an entire flight (may span multiple tlog chunks)
 *
 * We keep our summaries in a separate table because we will nuke and reformat this table frequently as we decide to precalc more data
 */
case class MissionSummary(
                           var startTime: Option[Timestamp] = None,
                           var endTime: Option[Timestamp] = None,
                           var maxAlt: Double = 0.0,
                           var maxGroundSpeed: Double = 0.0,
                           var maxAirSpeed: Double = 0.0,
                           var maxG: Double = 0.0,
                           var flightDuration: Option[Double] = None,
                           var latitude: Option[Double] = None,
                           var longitude: Option[Double] = None,

                           /**
                            * How many parameter records did we find?
                            */
                           var numParameters: Int = 0,

                           // Autopilot software version #
                           var softwareVersion: Option[String] = None,
                           // Autopilot software version #
                           var softwareGit: Option[String] = None,

                           // How many records in this flight? (either tlog or dataflash)
                           var numRecords: Option[Long] = Some(0L)) extends DapiRecord with Logging {

  val missionId: Option[Long] = None
  lazy val mission = belongsTo[Mission]

  /**
   * A heristically generated user friendly string describing this flight (shown in GUI)
   */
  var text: Option[String] = None

  /**
   * We support the notion of a summary version, so if we make our parser better we can regen old summaries only as needed
   */
  var summaryVersion: Int = MissionSummary.currentVersion

  def isSummaryValid = startTime.isDefined

  def minutes = for {
    s <- startTime
    e <- endTime
  } yield (e.getTime - s.getTime) / 1000.0 / 60

  override def toString = {
    val mins = minutes.map(_.toString).getOrElse("unknown")
    val loc = text.getOrElse("unknown")

    s"MissionSummary(at ${startTime.getOrElse("no-date")}, alt=$maxAlt, mins=$mins, flt=$flightDuration, loc=$loc, params=$numParameters)"
  }

  /**
   * Regenerate the summary text
   */
  def createText() = {
    try {
      // unused
      //def vehicle: Vehicle = mission.vehicle
      //def user: User = vehicle.user
      //def username = user.login

      val unknown = "at an unknown location"
      val needGeocoding = !text.isDefined || text.get == unknown

      // We only attempt geocoding for the first lat/lng we find (because geocoding cost money)
      if (needGeocoding) {
        val locstr =
          (for {
            lat <- latitude
            lon <- longitude
          } yield {
            try {
              var geo = MissionSummary.mapboxClient.geocode(lat, lon)

              info("Geocoded to " + geo.mkString(":"))
              // Some locations (like the pacific ocean) return empty geo locations (no government name)
              // http://api.tiles.mapbox.com/v3/examples.map-zr0njcqy/geocode/-158.2276141,21.0933198.json
              if (geo.isEmpty)
                "international waters"
              else
              // Street addresses are too identifying, skip them - postcodes are boring to users
                geo = geo.filter { case (typ, v) => typ != "street" && typ != "postcode" }
              geo.map(_._2).mkString(", ")

            } catch {
              case ex: Exception =>
                error(s"Geocoding failed for $id ($lat, $lon)", ex)
                unknown
            }
          }).getOrElse(unknown)

        // Pulled out so that nice links can be generated by the frontend instead
        // $username flew their
        // s"${vehicle.text}
        // $timestr
        Some(s"$locstr")
      } else
        None
    } catch {
      case ex: Exception =>
        error("TextRegen failed", ex)
        None
    }
  }
}

object MissionSummary extends DapiRecordCompanion[MissionSummary] {
  val mapboxClient = new MapboxClient()

  val currentVersion = 15
}

/**
 * A mission recorded from a vehicle
 */
case class Mission(
                    // As specified by the user
                    var notes: Option[String] = None,
                    // Is the client currently uploading data to this mission
                    var isLive: Boolean = false,
                    var viewPrivacy: Int = AccessCode.DEFAULT_VALUE) extends DapiRecord with Logging {
  /**
   * What vehicle made me?
   */
  lazy val vehicle = belongsTo[Vehicle]
  val vehicleId: Option[Long] = None

  @Transient
  var keep: Boolean = true

  /**
   * For testing airspace approval ideas
   */
  var approval: Option[String] = None

  /**
   * Note: this is no longer just for tlogs.  The rules are as follows:
   *
   * If the string ends with a suffix (.log or .bog) then they are data flash (or some other data file format).  If
   * no suffix is used a tlog is assumed.
   *
   * If the tlog is stored to s3 this is the ID
   * Normally this is a UUID, but for old droneshare records it might be some other sort of unique string
   */
  @Unique
  @Length(max = 40)
  var tlogId: Option[String] = None

  var doaramaId: Option[Long] = None

  def isDataflashText = tlogId.isDefined && tlogId.get.endsWith(APIConstants.flogExtension)

  def isDataflashBinary = tlogId.isDefined && tlogId.get.endsWith(APIConstants.blogExtension)

  /// Return the logfile with a suitable extension
  def logfileName = if (isDataflashText || isDataflashBinary)
    tlogId // These files _do_ have a suffix already
  else
    tlogId.map(_ + ".tlog") // Due to an accident of history we don't include a suffix on tlog ids

  /**
   * A reconstructed playback model for this vehicle - note: calling this function is _expensive_
   * CPU and ongoing memory
   */
  def model: Option[PlaybackModel] =
    if (isDataflashText || isDataflashBinary) {
      dataflashModel
    } else {
      tlogModel
    }

  /// Get a doarama URL for this flight.  Uploading flight to doarama if needed
  def doaramaURL: Option[String] = {
    // If we fail doing all this doarama stuff - just return None
    ThreadTools.catchOrElse(None: Option[String]) {
      val login = vehicle.user.login
      Using.using(new DoaramaClient(login)) { client =>
        // Ask doarama for an idea if necessary
        if (!doaramaId.isDefined) {
          warn(s"No doarama ID for $this")

          model.foreach { model =>
            val out = new ByteArrayOutputStream()
            val igc = model.toIGC(out)

            doaramaId = Some(client.createAnonymousVisualization(out.toByteArray))
            debug(s"Success!  DoaramaID $doaramaId")
            save
          }
        }

        // Generate a new URL
        val r = doaramaId.map(client.getDisplayURL)
        debug(s"Doarama URL is $r")
        r
      }
    }
  }

  private def dataflashModel = try {
    tlogBytes.flatMap { bytes =>
      warn(s"Regenerating dataflash model for $this, numBytes=${bytes.size}")

      val defaultTime = Option(createdAt).map(_.getTime).getOrElse(System.currentTimeMillis)
      Some(DataflashPlaybackModel.fromBytes(bytes, isDataflashText, defaultTime))
    }
  } catch {
    case ex: Exception =>
      error(s"Unparsable flash log, not generating model: $ex")
      None
  }

  /// Return a TLOG backed model if we have one
  def tlogModel = try {
    if (isDataflashText || isDataflashBinary) {
      warn(s"We don't have a TLOG for $this")
      None
    } else
      tlogBytes.map { bytes =>
        warn(s"Regenerating TLOG model for $this, numBytes=${bytes.size}")
        TLOGPlaybackModel.fromBytes(bytes, false)
      }
  } catch {
    case ex: Exception =>
      error(s"Unparsable TLOG, not generating model: $ex")
      None
  }

  def numParameters = {
    regenSummary()
    summary.numParameters
  }

  /// The user of this mission
  @Transient
  lazy val userId = for {
    v <- vehicle
    uid <- v.userId
  } yield {
    uid
  }

  /**
   * We allow reading vehicles if the vehicle is not protected or the user has suitable permissions
   *
   * This method should eventually replace MissonController.isAccessAllowed
   */
  def isReadAccessAllowed(u: Option[User], isSharedLink: Boolean = false) = {

    // Use the privacy setting from the vehicle if the mission specifies default sharing
    var vehiclePrivacy = vehicle.viewPrivacy

    val debugmsg = s"access check for $this, userId=$userId, privCode=${viewPrivacy}, vehiclePriv=$vehiclePrivacy, isShared=$isSharedLink"

    if (vehiclePrivacy == AccessCode.DEFAULT_VALUE)
      vehiclePrivacy = ApiController.defaultVehicleViewAccess

    val isOwner = u.map(_.id == userId.getOrElse(-1L)).getOrElse(false)
    val isResearcher = u.map(_.isResearcher).getOrElse(false)
    val isAdmin = u.map(_.isAdmin).getOrElse(false)

    val ok = ApiController.isAccessAllowed(viewPrivacy, isOwner || isAdmin, isResearcher, vehiclePrivacy, isSharedLink)
    // debug(debugmsg + ": ok=" + ok)
    ok
  }

  /**
   * Do we think this mission is higher quality than another possibly duplicated copy of the mission?
   * @param other
   */
  def isBetterThan(other: Mission) = {
    // We can only consider 'better' for possibly identical missions (need a start time)
    other.regenSummary()

    assert(summary.startTime.isDefined)
    assert(other.summary.startTime.isDefined)
    assert(summary.startTime.equals(other.summary.startTime))

    summary.numRecords.getOrElse(0L) >= other.summary.numRecords.getOrElse(0L)
  }

  /**
   * Look in the database for other missions that have the same start time and vehicle, but are smaller.  If
   * such missions are found then it means the GCS made a mistake and sent up multiple tlog files.
   *
   * @return true if we should keep this mission (and implicitly this method will delete the other bad missions)
   */
  def keepIfBestVersion() = {
    regenSummary()

    if (!vehicleId.isDefined) {
      warn(s"Can not keep $this - we have no vehicle")
      false
    } else if (!summary.startTime.isDefined) {
      warn(s"Can not keep $this - we have no start time")
      false
    } else {
      val others = Mission.findByVehicleAndTime(vehicleId.get, summary.startTime.get)

      debug(s"Considering other missions: " + others.mkString(", "))
      assert(!others.isEmpty) // At the very least we should have found our mission
      val best = others.reduce { (a: Mission, b: Mission) =>
          if (a.isBetterThan(b))
            a
          else
            b
        }

      debug(s"Best mission was $best")
      if (best.id == id) {
        others.filter(_.id != id).foreach { m =>
          warn(s"Would have deleted $m")
          // m.delete()
        }

        true // keep this mission
      }
      else
        false
    }
  }

  /**
   * Create summary data for the mission
   */
  def regenSummary() {
    try {
      if (!summary.headOption.isDefined || summary.summaryVersion < MissionSummary.currentVersion || !summary.text.isDefined) {
        warn("Recreating mission summary")

        // Get the new summary
        val s = model match {
          case None =>
            error(s"Model generation failed, creating invalid summary.")
            MissionSummary()

          case Some(m) =>

            warn(s"Updating vehicle from new summary: ${m.summary}")
            vehicle.updateFromMission(m)

            // Set our record creation time based on the mavlink data - note: start time is in uSecs!!!
            m.startTime.foreach { date => createdAt = new Timestamp(date / 1000L)}

            m.summary
        }

        // Install the new summary instead of the old one
        if (summary.headOption.isDefined)
          try {
            // If the old summary had text - just use that (user might have edited it)
            s.text = summary.text.orElse(s.createText())
            summary.delete() // Get rid of the old summary record
          } catch {
            case ex: RecordNotFoundException =>
              warn("No previous summary - skipping delete")
          }
        s.create

        s.mission := this
        summary := s
        save

        warn(s"Summary regened: $this")
      }
    } catch {
      case ex: RecordNotFoundException =>
        warn(s"Ignoring record not found for $this - it has probably been deleted")
      case ex: SquerylSQLException =>
        warn(s"Ignoring save error for $this - it has probably been deleted")
    }
  }

  /**
   * Does this mission contain interesting bits?
   */
  def isInteresting = {
    regenSummary() // Make sure we have a summary if we can
    summary.headOption match {
      case Some(s) =>
        val interesting = s.latitude != None && s.longitude != None && s.startTime != None && s.endTime != None
        if (!interesting)
          warn(s"Flight is not interesting summary is: $s")
        interesting

      case None =>
        warn(s"Could not generate summary, therefore not interesting")
        false
    }
  }

  /**
   * If this mission is 'boring' (no position data etc... delete from db and return true
   *
   * @return true if this flight was uninteresting
   */
  def deleteIfUninteresting() = {
    regenSummary() // Make sure we have a summary if we can

    if (!isInteresting) {
      warn(s"Deleting uninteresting mission $this")
      this.delete()
      true
    } else {
      //       if (keepIfBestVersion()) {
      if (true) {
        debug(s"Keeping interesting mission $this")
        false
      }
      else {
        warn(s"FIXME Deleting this mission - we have better versions: $this")
        // this.delete()
        false // FIXME - should be true
      }
    }
  }

  /**
   * The server generated summary of the flight
   */
  lazy val summary = hasOne[MissionSummary]

  /**
   * A nice small map thumbnail suitable for showing on a map
   */
  def mapThumbnailURL = try {
    for {
      latIn <- summary.latitude
      lonIn <- summary.longitude
    } yield {
      val zoom = 2
      val width = 140
      val height = 100

      val isPlane = vehicle.vehicleType.map(_ == "fixed-wing").getOrElse(false)
      val icon = if (isPlane) "airport" else "heliport"

      MapboxClient.staticMapURL(latIn, lonIn, zoom, width, height, icon)
    }
  } catch {
    case ex: Exception =>
      error(s"Can't generate thumbnail for $this due to $ex")
      None
  }

  /**
   * @return true if we had to delete the mission
   */
  def cleanupOrphan() = {
    if (isLive) {
      // This can happen if server is killed while a mission was getting uploaded

      isLive = false
      if (!this.keep) {
        warn(s"Supposedly live mission doesn't have an actor: $this - deleting")
        delete()
        true
      } else {
        warn(s"Supposedly live mission doesn't have an actor: $this - fixing")
        try {
          // Hmm - sometimes we get a strange SquerylSQLException: failed to update.  Expected 1 row, got 0 during initial space
          // seeding when deleting abandoned live missions
          save()
        } catch {
          case ex: Exception =>
            AnalyticsService.reportException("Mystery bug during cleanup", ex)
        }
        false
      }
    } else
      false
  }

  /**
   * this function is potentially expensive - it will read from S3 (subject to a small shared cache)
   */
  def tlogBytes = try {
    if (!isLive)
      tlogId.flatMap { s => Mission.getBytes(s)}
    else {
      info(s"Asking live actor for tlog $this")
      // FIXME - kinda yucky way to ask the live vehicle for a tracklog
      val actor = LiveVehicleActor.find(vehicle)
      actor match {
        case None =>
          if (cleanupOrphan())
            throw new Exception("Forced discard of old mission")

          None

        case Some(a) =>
          implicit val timeout = Timeout(60 seconds)
          val f = (a ? GetTLogMessage)
          val b = Await.result(f, 60 seconds).asInstanceOf[Option[Array[Byte]]]
          b
      }
    }
  } catch {
    case ex: Exception =>
      error(s"S3 can't find tlog ${tlogId.get} due to $ex")
      None
  }

  // If we delete a mission tell the space supervisor
  override def delete() = {
    val r = super.delete()

    // FIXME - it is yucky to have the model reaching 'up' into the land of space supervisors
    val space = SpaceSupervisor.find()
    space ! MissionDelete(id)
    r
  }

  /// A user visible URL that can be used to view this mission
  def viewURL = {
    import Global._

    s"$scheme://$hostname/mission/$id"
  }

  override def toString = s"Mission id=$id, tlog=$tlogId, summary=${summary.getOrElse("(No summary)")}"
}

/// Don't show the clients that we are keeping some stuff in 'summary'
case class MissionJson(
                        id: Long,
                        notes: Option[String],
                        isLive: Boolean,
                        viewPrivacy: Option[AccessCode.EnumVal],
                        vehicleId: Option[Long],

                        // From summary
                        maxAlt: Option[Double],
                        maxGroundspeed: Option[Double],
                        maxAirspeed: Option[Double],
                        maxG: Option[Double],
                        flightDuration: Option[Double],
                        latitude: Option[Double],
                        longitude: Option[Double],
                        softwareVersion: Option[String],
                        softwareGit: Option[String],
                        createdOn: Option[Date],
                        updatedOn: Option[Date],
                        summaryText: Option[String],
                        mapThumbnailURL: Option[String],
                        viewURL: Option[String], // A user visible URL that can be used to view this mission

                        // The following information comes from vehicle/user - might be expensive,
                        vehicleText: Option[String],
                        userName: Option[String],
                        userAvatarImage: Option[String])

/// We provide an initionally restricted view of users
class MissionSerializer(useDoarama: Boolean) extends CustomSerializer[Mission](implicit format => ( {
  // more elegant to just make a throw away case class object and use it for the decoding
  //case JObject(JField("login", JString(s)) :: JField("fullName", JString(e)) :: Nil) =>
  case x: JValue =>
    throw new Exception("not yet implemented")
}, {
  case u: Mission =>
    u.regenSummary() // Make sure the summary has the latest representation

    val s = u.summary.headOption

    // Instead of DB creation time we prefer to use the time from the summary (from tlog data)
    val flightDate = s.flatMap(_.startTime).getOrElse(u.createdAt)
    val m = MissionJson(u.id, u.notes, u.isLive, Some(AccessCode.valueOf(u.viewPrivacy)), u.vehicleId,
      s.map(_.maxAlt),
      s.map(_.maxGroundSpeed),
      s.map(_.maxAirSpeed),
      s.map(_.maxG),
      s.flatMap(_.flightDuration),
      s.flatMap(_.latitude),
      s.flatMap(_.longitude),
      s.flatMap(_.softwareVersion),
      s.flatMap(_.softwareGit),
      Some(flightDate),
      Some(u.updatedAt),
      s.flatMap(_.text),
      u.mapThumbnailURL,
      Some(u.viewURL),
      Some(u.vehicle.text),
      Some(u.vehicle.user.login),
      u.vehicle.user.avatarImageURL)
    var r = Extraction.decompose(m).asInstanceOf[JObject]

    if (useDoarama)
      r = r ~ ("doaramaURL" -> u.doaramaURL)

    r ~ ("numParameters" -> u.numParameters) ~ ("vehicleType" -> u.vehicle.vehicleType) ~ ("approval" -> u.approval)
}))

object Mission extends DapiRecordCompanion[Mission] with Logging {
  // We use a cache to avoid (slow) rereading of s3 data if we can help it
  private val bytesCache = CacheBuilder.newBuilder.maximumSize(5).build { (key: String) => readBytesByPath(S3Client.tlogPrefix + key)}

  private def readBytesByPath(id: String): Array[Byte] = {
    logger.debug("Asking S3 for " + id)
    Using.using(S3Client.tlogBucket.downloadStream(id)) { s =>
      //logger.debug("Reading bytes from S3")
      val r = ByteStreams.toByteArray(s)
      logger.debug("Done reading S3 bytes, size = " + r.length)
      r
    }
  }

  /**
   * Note, that due to an accident of history tlogs are stored without a .tlog extension.  All other files receive an extension
   * Get bytes from the cache or S3
   */
  private def getBytes(id: String) = {
    Option(bytesCache.getUnchecked(id))
  }

  // Note, that due to an accident of history tlogs are stored without a .tlog extension.  All other files receive an extension
  def putBytes(id: String, src: InputStream, srcLen: Long, mimeType: String) {
    info(s"Uploading to s3: $id (numBytes=$srcLen)")

    S3Client.tlogBucket.uploadStream(S3Client.tlogPrefix + id, src, mimeType, srcLen)
  }

  /**
   * Note, that due to an accident of history tlogs are stored without a .tlog extension.  All other files receive an extension
   * Put tlog data into the cache and s3
   */
  def putBytes(id: String, bytes: Array[Byte], mimeType: String) {
    val src = new ByteArrayInputStream(bytes)
    putBytes(id, src, bytes.length, mimeType)
    // Go ahead and update our cache
    bytesCache.put(id, bytes)
  }

  def create(vehicle: Vehicle) = {
    val r = Mission().create
    vehicle.missions += r
    r.save
    vehicle.save // FIXME - do I need to explicitly save?
    r
  }

  // This is only used by the nestor importer
  def findByTlogId(id: String): Option[Mission] = {
    collection.where(_.tlogId === id).headOption
  }

  def findByVehicleAndTime(vehicleId: Long, startTime: Timestamp): Iterable[Mission] = {
    // FIXME - this results in two queries and might be slightly slow
    collection.where(_.vehicleId === Some(vehicleId)).toSeq.filter(_.summary.startTime == Some(startTime))
  }


  override def collection = super.collection.includes(_.summary)

  /**
   * Look in the database for other missions that have the same start time and vehicle, but are smaller.  If
   * such missions are found then it means the GCS made a mistake and sent up multiple tlog files.
   *
   * @param others the set of missions to consider (they all better have the same start time)
   * @return number of deleted missions
   */
  def deleteWorstMissions(others: Iterable[Mission]) = {
    debug(s"Considering missions: " + others.mkString(", "))
    assert(!others.isEmpty) // At the very least we should have found our mission
    val best = others.reduce { (a: Mission, b: Mission) =>
        if (a.isBetterThan(b))
          a
        else
          b
      }

    debug(s"Best mission was $best")
    val todelete = others.filter(_.id != best.id)
    todelete.foreach { m =>
      warn(s"Would have deleted $m")
      // m.delete()
    }

    todelete.size
  }
}
