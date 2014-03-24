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
import com.geeksville.logback.Logging
import com.geeksville.util.Using
import com.google.common.io.ByteStreams
import java.io.InputStream

/**
 * Stats which cover an entire flight (may span multiple tlog chunks)
 *
 * We keep our summaries in a separate table because we will nuke and reformat this table frequently as we decide to precalc more data
 */
case class MissionSummary(startTime: Option[Date],
  endTime: Option[Date],
  maxAlt: Double = 0.0,
  maxGroundSpeed: Double = 0.0,
  maxAirSpeed: Double = 0.0,
  maxG: Double = 0.0,
  flightDuration: Option[Double] = None) extends DapiRecord {

  val missionId: Option[Long] = None
  lazy val mission = belongsTo[Mission]

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
  var viewPrivacy: Int = AccessCode.DEFAULT_VALUE,

  var controlPrivacy: Int = AccessCode.DEFAULT_VALUE) extends DapiRecord {
  /**
   * What vehicle made me?
   */
  lazy val vehicle = belongsTo[Vehicle]
  val vehicleId: Option[Long] = None

  @Transient
  var keep: Boolean = true

  /**
   * If the tlog is stored to s3 this is the ID
   */
  var tlogId: Option[UUID] = None

  /**
   * The server generated summary of the flight
   */
  lazy val summary = hasOne[MissionSummary]

  @Transient
  lazy val tlogBytes = tlogId.flatMap(Mission.getBytes)
}

object Mission extends DapiRecordCompanion[Mission] with Logging {
  val mimeType = "application/vnd.nestor.tlog"

  // We use a cache to avoid (slow) rereading of s3 data if we can help it
  private val bytesCache = CacheBuilder.newBuilder.maximumSize(5).build { (key: UUID) => readBytesByPath(S3Client.tlogPrefix + key) }

  private def readBytesByPath(id: String): Array[Byte] = {
    logger.debug("Asking S3 for " + id)
    Using.using(S3Client.downloadStream(id)) { s =>
      logger.debug("Reading bytes from S3")
      val r = ByteStreams.toByteArray(s)
      logger.debug("Done reading S3 bytes")
      r
    }
  }

  /**
   * Get bytes from the cache or S3
   */
  private def getBytes(id: UUID) = {
    Option(bytesCache.getUnchecked(id))
  }

  def putBytes(id: String, src: InputStream, srcLen: Long) {
    logger.info(s"Uploading to s3: $id")
    S3Client.uploadStream(S3Client.tlogPrefix + id, src, mimeType, srcLen)
  }

  def create(vehicle: Vehicle) = {
    val r = Mission().create
    vehicle.missions += r
    r.save
    vehicle.save // FIXME - do I need to explicitly save?
    r
  }
}
