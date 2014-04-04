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

  // Autopilot software version #
  var softwareVersion: Option[String] = None) extends DapiRecord with Logging {
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
  def createMission(bytes: Array[Byte]) {
    // Copy over tlog
    val newTlogId = UUID.randomUUID()
    info("Copying from S3")
    val s = new ByteArrayInputStream(bytes)
    Mission.putBytes(newTlogId.toString, s, bytes.length)

    // Create mission record
    val m = Mission.create(this)
    m.notes = Some("Imported from Droneshare")
    m.controlPrivacy = AccessCode.DEFAULT.id
    m.viewPrivacy = AccessCode.DEFAULT.id
    m.keep = true
    m.isLive = false
    m.tlogId = Some(newTlogId)
    // FIXME - regenerate summaries?
    m.save()
    debug("Done with record")
  }
}

object Vehicle extends DapiRecordCompanion[Vehicle] {
  /**
   * Find by ID but using a string encoding (i.e. UUID or somesuch)
   * For now I just convert the int to its base-10 representation
   */
  def findByUUID(id: String): Option[Vehicle] = this.where(_.uuid === UUID.fromString(id)).headOption
}