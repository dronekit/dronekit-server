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

/**
 * Stats which cover an entire flight (may span multiple tlog chunks)
 *
 * We keep our summaries in a separate table because we will nuke and reformat this table frequently as we decide to precalc more data
 */
case class MissionSummary(startTime: Date,
  endTime: Date,
  maxAlt: Double = 0.0,
  maxGroundSpeed: Double = 0.0,
  maxAirSpeed: Double = 0.0,
  maxG: Double = 0.0,
  flightDuration: Option[Double] = None) extends DapiRecord {

  val missionId: Option[Long] = None
  lazy val mission = belongsTo[Mission]

  def minutes = (endTime.getTime - startTime.getTime) / 1000.0 / 60

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

object MissionSummary extends DapiRecordCompanion[MissionSummary]

/**
 * A mission recorded from a vehicle
 */
case class Mission() extends DapiRecord {
  /**
   * What vehicle made me?
   */
  lazy val vehicle = belongsTo[Vehicle]
  val vehicleId: Option[Long] = None

  // As specified by the user
  var notes: Option[String] = None

  /**
   * Is the client currently uploading data to this mission
   */
  var isLive: Boolean = false

  @Transient
  var keep: Boolean = true

  var viewPrivacy: Int = AccessCode.DEFAULT_VALUE

  var controlPrivacy: Int = AccessCode.DEFAULT_VALUE

  /**
   * If the tlog is stored to s3 this is the ID
   */
  var tlogId: Option[String] = None

  /**
   * The server generated summary of the flight
   */
  lazy val summary = hasOne[MissionSummary]
}

object Mission extends DapiRecordCompanion[Mission] {
  def create(vehicle: Vehicle) = {
    val r = Mission().create
    vehicle.missions += r
    r.save
    vehicle.save // FIXME - do I need to explicitly save?
    r
  }
}
