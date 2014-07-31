package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger
import com.geeksville.util.URLUtil
import com.geeksville.dapi.model._
import org.scalatra.servlet.FileUploadSupport
import javax.servlet.annotation.MultipartConfig
import org.json4s.JsonAST.JString
import com.geeksville.json.ActiveRecordSerializer
import org.json4s.JsonAST.JObject
import java.util.UUID
import org.scalatra.swagger.SwaggerSupportSyntax.ModelParameterBuilder
import org.scalatra.swagger.DataType
import com.geeksville.apiproxy.APIConstants

/// FIXME - we don't want v controller to inherit from activerecordcontroller - instead it should talk to actors to get live state
@MultipartConfig(maxFileSize = 1024 * 1024)
class VehicleController(implicit swagger: Swagger) extends ActiveRecordController[Vehicle, VehicleJson]("vehicle", swagger, Vehicle) with MissionUploadSupport {

  override implicit val jsonFormats: Formats = super.jsonFormats + new VehicleSerializer(true)

  /**
   * We allow reading vehicles if the vehicle is not protected or the user has suitable permissions
   */
  override protected def filterForReadAccess(oin: Vehicle, isSharedLink: Boolean = false) = {
    super.filterForReadAccess(oin).flatMap { o =>
      if (isAccessAllowed(o.userId.getOrElse(-1L), o.viewPrivacy, ApiController.defaultVehicleViewAccess, isSharedLink))
        Some(o)
      else
        None
    }
  }

  /**
   * Filter read access to a potentially protected record.  Subclasses can override if they want to restrict reads based on user or object
   * If not allowed, override should call haltUnauthorized()
   */
  override protected def requireWriteAccess(o: Vehicle) = {

    // Be even more strict than this - only let them change the vehicle object if the owner (for now)
    // requireAccessCode(o.userId.getOrElse(-1L), o.controlPrivacy, ApiController.defaultVehicleControlAccess)
    requireBeOwnerOrAdmin(o.userId.getOrElse(-1L))

    super.requireWriteAccess(o)
  }

  override protected def requireCreateAccess() = {
    requireLogin()
    requireServiceAuth(aName + "/create")
  }

  // FIXME - make this code actually do something
  rwField[String]("mode", (v) => "FIXME", { (v, arg) => })
  //roField[Location]("location", null)
  //roField[Attitude]("attitude", null)
  roField("airspeed") { (v) => 1.5 }
  roField("groundspeed") { (v) => 1.5 }
  roField("batteryVolt") { (v) => 1.5 }
  roField("batterySOC") { (v) => 1.5 }

  roField[List[Int]]("rcChannels") { (v) => List(1, 2, 3) }
  woField[List[Int]]("rcOverrides", { (v, arg) => })

  //rwField[Location]("targetLocation", null, { (v) => })

  // FIXME - need to use correct domain objects (Waypoints)
  //rwField[List[Location]]("waypoints", null, { (v) => })

  override protected def createDynamically(payload: JObject): Any = {
    val json = payload.extract[VehicleJson]
    user.getOrCreateVehicle(json.uuid.getOrElse(UUID.randomUUID))
  }

  private val addMissionInfo =
    (apiOperation[List[MissionJson]]("addMission")
      summary s"Add a new mission (as a tlog, bog or log)"
      consumes (APIConstants.flogMimeType, APIConstants.blogMimeType, APIConstants.tlogMimeType)
      parameters (
        (new ModelParameterBuilder(DataType("file"))).description("log file as a standard html form upload POST").fromBody,
        pathParam[String]("id").description(s"Id of $aName to be have mission added")))

  // Allow adding missions by posting under the vehicle
  post("/:id/missions", operation(addMissionInfo)) {
    val v = requireWriteAccess(findById)

    handleMissionUpload(v)
  }

  /// Allow web gui to update vehicle
  override protected def updateObject(o: Vehicle, payload: JObject) = {
    val r = payload.extract[VehicleJson]

    debug(s"Setting vehicle from web client, payload=$r")
    o.name = r.name
    r.viewPrivacy.foreach { v => o.viewPrivacy = v.id }
    r.controlPrivacy.foreach { v => o.controlPrivacy = v.id }
    o.save

    o
  }
}

