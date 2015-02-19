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
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s._
import java.util.Date
import com.geeksville.flight.HasVehicleType
import com.geeksville.apiproxy.APIConstants

/**
 * A vehicle model
 *
 * @param uuid is selected by the client on first connection
 */
case class Vehicle(
                    // A UUID provided by the client to represent this vehicle. NOTE: We do not ensure that
                    // for a particular UUID it only appears once in the DB.  Clients can be buggy and pick
                    // any id they want.  The unique ID for a vehicle is 'id'.
                    @Required uuid: UUID = UUID.randomUUID(),
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

  /// A pretty user visible string for this vehicle
  def text = {
    if (name.isEmpty)
      vehicleType.getOrElse("mystery vehicle")
    else
      name
  }

  /// Create a new mission as a child of this vehicle (given tlog bytes)
  def createMission(bytes: Array[Byte],
                    notes: Option[String] = None,
                    tlogIdIn: String = UUID.randomUUID().toString,
                    mimeType: String = APIConstants.tlogMimeType) = {

    // We add a suffix to our log UUID showing type of log
    val tlogId = tlogIdIn + APIConstants.mimeTypeToExtension(mimeType)

    // Copy over tlog
    Mission.putBytes(tlogId, bytes, mimeType)

    // Create mission record
    val m = Mission.create(this)
    m.notes = notes
    m.viewPrivacy = viewPrivacy
    m.keep = true
    m.isLive = false
    m.tlogId = Some(tlogId)
    m.vehicle := this
    m.save()
    save()

    // We need to do this after the record is written
    m.regenSummary()
    m.save()
    debug("Done with record")
    m
  }

  /**
   * Reverse engineer vehicle data from a recent mission upload
   */
  def updateFromMission(m: HasVehicleType) {
    var dirty = false

    info(s"Perhaps updaing vehicle from ${m.humanVehicleType}, vehicleType was $vehicleType")
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

case class VehicleJson(
                        uuid: Option[UUID],
                        name: Option[String] = None,
                        id: Option[Long] = None,
                        userId: Option[Long] = None,
                        manufacturer: Option[String] = None,
                        vehicleType: Option[String] = None,
                        autopilotType: Option[String] = None,
                        viewPrivacy: Option[AccessCode.EnumVal] = None,
                        controlPrivacy: Option[AccessCode.EnumVal] = None,
                        missions: Option[Seq[JValue]] = None,
                        createdOn: Option[Date] = None,
                        updatedOn: Option[Date] = None,
                        summaryText: Option[String] = None,
                        userName: Option[String] = None)



/// We provide an initionally restricted view of users
class VehicleSerializer(flavor: DeepJSON.Flavor.Type) extends CustomSerializer[Vehicle](implicit format => ( {
  // more elegant to just make a throw away case class object and use it for the decoding
  //case JObject(JField("login", JString(s)) :: JField("fullName", JString(e)) :: Nil) =>
  case x: JValue =>
    throw new Exception("not yet implemented")
}, {
  case u: Vehicle =>
    // u.missions.map(_.id).toSeq.sorted(Ordering[Long].reverse)
    val recs = u.missions.orderBy(_.createdAt desc).toSeq
    val missions = DeepJSON.asJSONArray(recs, flavor)

    val m = VehicleJson(Some(u.uuid), Some(u.name), Some(u.id), u.userId,
      u.manufacturer,
      u.vehicleType,
      u.autopilotType,
      Some(AccessCode.valueOf(u.viewPrivacy)),
      Some(AccessCode.valueOf(u.controlPrivacy)),
      // We deliver newer missions first
      Some(missions),
      Some(u.createdAt),
      Some(u.updatedAt),
      Some(u.text),
      Some(u.user.login)
    )
    Extraction.decompose(m)
}))

object Vehicle extends DapiRecordCompanion[Vehicle] {
  /**
   * Find by ID but using a string encoding (i.e. UUID or somesuch)
   * For now I just convert the int to its base-10 representation
   */
  def findByUUID(id: String): Option[Vehicle] = this.where(_.uuid === UUID.fromString(id)).headOption
}
