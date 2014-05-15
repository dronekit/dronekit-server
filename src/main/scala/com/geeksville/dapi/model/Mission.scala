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
import com.geeksville.dapi.PlaybackModel
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

/**
 * Stats which cover an entire flight (may span multiple tlog chunks)
 *
 * We keep our summaries in a separate table because we will nuke and reformat this table frequently as we decide to precalc more data
 */
case class MissionSummary(
  var startTime: Option[Date] = None,
  var endTime: Option[Date] = None,
  var maxAlt: Double = 0.0,
  var maxGroundSpeed: Double = 0.0,
  var maxAirSpeed: Double = 0.0,
  var maxG: Double = 0.0,
  var flightDuration: Option[Double] = None,
  var latitude: Option[Double] = None,
  var longitude: Option[Double] = None,
  // Autopilot software version #
  var softwareVersion: Option[String] = None,
  // Autopilot software version #
  var softwareGit: Option[String] = None) extends DapiRecord with Logging {

  val missionId: Option[Long] = None
  lazy val mission = belongsTo[Mission]

  /**
   * A heristically generated user friendly string describing this flight (shown in GUI)
   */
  var text: Option[String] = None

  def isSummaryValid = startTime.isDefined

  def minutes = for {
    s <- startTime
    e <- endTime
  } yield (e.getTime - s.getTime) / 1000.0 / 60

  override def toString = {
    val mins = minutes.map(_.toString).getOrElse("unknown")
    val loc = text.getOrElse("unknown")

    s"MissionSummary($maxAlt alt, $mins mins, $loc location)"
  }

  /**
   * Regenerate the summary text
   */
  def regenText() {
    try {
      // unused
      def vehicle: Vehicle = mission.vehicle
      def user: User = vehicle.user
      def username = user.login

      val unknown = "at an unknown location"
      val needGeocoding = !text.isDefined || text.get == unknown

      // We only attempt geocoding for the first lat/lng we find (because geocoding cost money)
      if (needGeocoding) {
        val locstr = try {
          (for {
            lat <- latitude
            lon <- longitude
          } yield {
            val geo = MissionSummary.mapboxClient.geocode(lat, lon)

            // Some locations (like the pacific ocean) return empty geo locations (no government name)
            // http://api.tiles.mapbox.com/v3/examples.map-zr0njcqy/geocode/-158.2276141,21.0933198.json
            if (geo.isEmpty)
              "international waters"
            else
              // First line might be too identifying - skip it
              geo.tail.map(_._2).mkString(", ")
          }).getOrElse(unknown)
        } catch {
          case ex: Exception =>
            error(s"Geocoding failed for $id", ex)
            unknown
        }
        // Pulled out so that nice links can be generated by the frontend instead
        // $username flew their 
        // s"${vehicle.text} 
        // $timestr
        text = Some(s"$locstr")
      }
    } catch {
      case ex: Exception =>
        error("TextRegen failed", ex)
    }
  }
}

object MissionSummary extends DapiRecordCompanion[MissionSummary] {
  val mapboxClient = new MapboxClient()

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
   * If the tlog is stored to s3 this is the ID
   * Normally this is a UUID, but for old droneshare records it might be some other sort of unique string
   */
  @Unique
  @Length(max = 40)
  var tlogId: Option[String] = None

  /**
   * A reconstructed playback model for this vehicle - note: calling this function is _expensive_
   * CPU and ongoing memory
   */
  def model = tlogBytes.map { bytes =>
    warn(s"Regenerating model for $this, numBytes=${bytes.size}")
    PlaybackModel.fromBytes(bytes, false)
  }

  /// FIXME - figure out when to call this
  def regenSummary() {
    if (!summary.headOption.isDefined) {
      // warn("Mission summary missing")
      model.foreach { m =>
        val s = m.summary
        s.create
        s.mission := this
        s.regenText()
        s.save
        vehicle.updateFromMission(m)

        // Set our record creation time based on the mavlink data - note: start time is in uSecs!!!
        m.startTime.foreach { date => createdOn = new Date(date / 1000L) }
        this.save

        warn(s"Summary regened: $this")
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

      // Four digits is pretty accurate and increases the chance of cache reuse
      val fmt = new DecimalFormat("##.0000")
      val lat = fmt.format(latIn)
      val lon = fmt.format(lonIn)

      val isPlane = vehicle.vehicleType.map(_ == "fixed-wing").getOrElse(false)
      val icon = if (isPlane) "airport" else "heliport"

      val mapBoxURL = s"http://api.tiles.mapbox.com/v3/***REMOVED***/pin-s-$icon+f44($lon,$lat,$zoom)/$lon,$lat,$zoom/${width}x$height.png"

      mapBoxURL
    }
  } catch {
    case ex: Exception =>
      error(s"Can't generate thumbnail for $this due to $ex")
      None
  }

  /**
   * this function is potentially expensive - it will read from S3 (subject to a small shared cache)
   */
  def tlogBytes = try {
    if (!isLive)
      tlogId.flatMap { s => Mission.getBytes(s) }
    else {
      info(s"Asking live actor for tlog $this")
      // FIXME - kinda yucky way to ask the live vehicle for a tracklog
      val actor = LiveVehicleActor.find(vehicle)
      actor match {
        case None =>
          error(s"Supposedly live mission doesn't have an actor: $this")
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

  override def toString = s"Mission id=$id, tlog=$tlogId, summary=${summary.getOrElse("(No summary)")}"
}

/// Don't show the clients that we are keeping some stuff in 'summary'
case class MissionJson(
  id: Long,
  notes: Option[String],
  isLive: Boolean,
  viewPrivacy: AccessCode.EnumVal,
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
  createdOn: Date,
  updatedOn: Date,
  summaryText: Option[String],
  mapThumbnailURL: Option[String],

  // The following information comes from vehicle/user - might be expensive,
  vehicleText: Option[String],
  userName: Option[String])

/// We provide an initionally restricted view of users
object MissionSerializer extends CustomSerializer[Mission](implicit format => (
  {
    // more elegant to just make a throw away case class object and use it for the decoding
    //case JObject(JField("login", JString(s)) :: JField("fullName", JString(e)) :: Nil) =>
    case x: JValue =>
      throw new Exception("not yet implemented")
  },
  {
    case u: Mission =>
      val s = u.summary.headOption
      val m = MissionJson(u.id, u.notes, u.isLive, AccessCode.valueOf(u.viewPrivacy), u.vehicleId,
        s.map(_.maxAlt),
        s.map(_.maxGroundSpeed),
        s.map(_.maxAirSpeed),
        s.map(_.maxG),
        s.flatMap(_.flightDuration),
        s.flatMap(_.latitude),
        s.flatMap(_.longitude),
        s.flatMap(_.softwareVersion),
        s.flatMap(_.softwareGit),
        u.createdOn, u.updatedOn,
        s.flatMap(_.text),
        u.mapThumbnailURL,
        Some(u.vehicle.text),
        Some(u.vehicle.user.login))
      Extraction.decompose(m)
  }))

object Mission extends DapiRecordCompanion[Mission] with Logging {
  val mimeType = "application/vnd.mavlink.tlog"

  // We use a cache to avoid (slow) rereading of s3 data if we can help it
  private val bytesCache = CacheBuilder.newBuilder.maximumSize(5).build { (key: String) => readBytesByPath(S3Client.tlogPrefix + key) }

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
   * Get bytes from the cache or S3
   */
  private def getBytes(id: String) = {
    Option(bytesCache.getUnchecked(id))
  }

  def putBytes(id: String, src: InputStream, srcLen: Long) {
    info(s"Uploading to s3: $id (numBytes=$srcLen)")
    S3Client.tlogBucket.uploadStream(S3Client.tlogPrefix + id, src, mimeType, srcLen)
  }

  /**
   * Put tlog data into the cache and s3
   */
  def putBytes(id: String, bytes: Array[Byte]) {
    val src = new ByteArrayInputStream(bytes)
    putBytes(id, src, bytes.length)
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

  def findByTlogId(id: String): Option[Mission] = {
    collection.where(_.tlogId === id).headOption
  }

  override def collection = super.collection.includes(_.summary)
}
