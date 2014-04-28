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

/**
 * Stats which cover an entire flight (may span multiple tlog chunks)
 *
 * We keep our summaries in a separate table because we will nuke and reformat this table frequently as we decide to precalc more data
 */
case class MissionSummary(
  startTime: Option[Date] = None,
  endTime: Option[Date] = None,
  maxAlt: Double = 0.0,
  maxGroundSpeed: Double = 0.0,
  maxAirSpeed: Double = 0.0,
  maxG: Double = 0.0,
  flightDuration: Option[Double] = None,
  latitude: Option[Double] = None,
  longitude: Option[Double] = None,
  // Autopilot software version #
  var softwareVersion: Option[String] = None,
  // Autopilot software version #
  var softwareGit: Option[String] = None) extends DapiRecord {

  val missionId: Option[Long] = None
  lazy val mission = belongsTo[Mission]

  def isSummaryValid = startTime.isDefined

  def minutes = for {
    s <- startTime
    e <- endTime
  } yield (e.getTime - s.getTime) / 1000.0 / 60

  /// A detailed description for facebook
  /*
  def descriptionString = {
    val fmt = new SimpleDateFormat("MMMMM d")
    val date = fmt.format(startTime)

    val name = if (ownerId.isEmpty)
      "a droneshare pilot"
    else
      ownerId

    val minutes = (endTime.getTime - startTime.getTime) / 1000 / 60

    """On %s, %s flew their %s for %s minutes""".format(date, name, vehicleTypeGuess, minutes.toLong)
  }
  */
}

object MissionSummary extends DapiRecordCompanion[MissionSummary] {

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
   * A reconstructed playback model for this vehicle - note: accessing this lazy val is _expensive_
   * CPU and ongoing memory
   */
  def model = tlogBytes.map { bytes =>
    warn(s"Regenerating model for $this")
    PlaybackModel.fromBytes(bytes, false)
  }

  /// FIXME - figure out when to call this
  def regenSummary() {
    if (!summary.headOption.isDefined) {
      warn("Mission summary missing")
      model.foreach { m =>
        val s = m.summary
        s.create
        s.mission := this
        s.save
        vehicle.updateFromMission(m)
        this.save
        warn("Regen completed")
      }
    }
  }

  /**
   * The server generated summary of the flight
   */
  lazy val summary = hasOne[MissionSummary]

  /**
   * this function is potentially expensive - it will read from S3 (subject to a small shared cache)
   */
  def tlogBytes = try {
    tlogId.flatMap { s => Mission.getBytes(s) }
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
  viewPrivacy: Int,
  vehicleId: Option[Long],
  maxAlt: Double,
  maxGroundspeed: Double,
  maxAirspeed: Double,
  maxG: Double,
  latitude: Option[Double],
  longitude: Option[Double],
  softwareVersion: Option[String],
  softwareGit: Option[String],
  createdOn: Date,
  updatedOn: Date)

/// We provide an initionally restricted view of users
object MissionSerializer extends CustomSerializer[Mission](implicit format => (
  {
    // more elegant to just make a throw away case class object and use it for the decoding
    //case JObject(JField("login", JString(s)) :: JField("fullName", JString(e)) :: Nil) =>
    case x: JValue =>
      throw new Exception
  },
  {
    case u: Mission =>
      val m = MissionJson(u.id, u.notes, u.isLive, u.viewPrivacy, u.vehicleId, u.summary.maxAlt,
        u.summary.maxGroundSpeed, u.summary.maxAirSpeed, u.summary.maxG, u.summary.latitude,
        u.summary.longitude, u.summary.softwareVersion, u.summary.softwareGit, u.createdOn, u.updatedOn)
      Extraction.decompose(m)
  }))

object Mission extends DapiRecordCompanion[Mission] with Logging {
  val mimeType = "application/vnd.mavlink.tlog"

  // We use a cache to avoid (slow) rereading of s3 data if we can help it
  private val bytesCache = CacheBuilder.newBuilder.maximumSize(5).build { (key: String) => readBytesByPath(S3Client.tlogPrefix + key) }

  private def readBytesByPath(id: String): Array[Byte] = {
    logger.debug("Asking S3 for " + id)
    Using.using(S3Client.downloadStream(id)) { s =>
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
    logger.info(s"Uploading to s3: $id")
    S3Client.uploadStream(S3Client.tlogPrefix + id, src, mimeType, srcLen)
  }

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
