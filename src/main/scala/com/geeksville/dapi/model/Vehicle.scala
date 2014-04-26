package com.geeksville.dapi.model

import com.github.aselab.activerecord.Datestamps
import com.github.aselab.activerecord.annotations._
import org.squeryl.annotations.Transient
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import com.github.aselab.activerecord.dsl._
import grizzled.slf4j.Logging
import java.io.ByteArrayInputStream
import com.geeksville.dapi.AccessCode
import com.geeksville.flight.ParametersReadOnlyModel
import com.geeksville.flight.LiveOrPlaybackModel

/**
 * A vehicle model
 *
 * @param uuid is selected by the client on first connection
 */
case class Vehicle(
  @Required @Unique uuid: UUID = UUID.randomUUID(),
  // A user specified name for this vehicle (i.e. my bixler)
  var name: String = "",

  // Vehicle manufacturer if known, preferably from the master vehicle-mfg.txt definitions file.  
  // To add new definitions to the file, please submit a github pull-request.
  var manufacturer: Option[String] = None,

  // Vehicle type if known, preferably from the master vehicle-types.txt definitions file.  
  // To add new definitions to the file, please submit a github pull-request.
  var vehicleType: Option[String] = None,

  // Autopilot type if known, preferably from the master autopilot-types.txt definitions file.
  // To add new definitions to the file, please submit a github pull-request.
  var autopilotType: Option[String] = None,

  var viewPrivacy: Int = AccessCode.DEFAULT_VALUE,
  var controlPrivacy: Int = AccessCode.DEFAULT_VALUE) extends DapiRecord with Logging {
  /**
   * Who owns me?
   */
  lazy val user = belongsTo[User]
  val userId: Option[Long] = None

  /**
   * All the missions this vehicle has made
   */
  lazy val missions = hasMany[Mission]

  /// Create a new mission as a child of this vehicle (given tlog bytes)
  def createMission(bytes: Array[Byte], notes: Option[String] = None, tlogId: String = UUID.randomUUID().toString) {
    // Copy over tlog
    Mission.putBytes(tlogId, bytes)

    // Create mission record
    val m = Mission.create(this)
    m.notes = notes
    m.viewPrivacy = viewPrivacy
    m.keep = true
    m.isLive = false
    m.tlogId = Some(tlogId)
    m.regenSummary()
    m.save()
    debug("Done with record")
  }

  /**
   * Reverse engineer vehicle data from a recent mission upload
   */
  def updateFromMission(m: LiveOrPlaybackModel) {
    var dirty = false

    if (!vehicleType.isDefined && m.humanVehicleType.isDefined) {
      vehicleType = m.humanVehicleType
      dirty = true
    }

    if (!autopilotType.isDefined && m.humanAutopilotType.isDefined) {
      autopilotType = m.humanAutopilotType
      dirty = true
    }

    if (dirty) {
      save
      debug(s"Updated $this based on mission data")
    } else
      trace(s"$this was already up-to-date")
  }
}

object Vehicle extends DapiRecordCompanion[Vehicle] {
  /**
   * Find by ID but using a string encoding (i.e. UUID or somesuch)
   * For now I just convert the int to its base-10 representation
   */
  def findByUUID(id: String): Option[Vehicle] = this.where(_.uuid === UUID.fromString(id)).headOption
}