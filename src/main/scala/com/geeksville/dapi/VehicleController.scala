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

/// FIXME - we don't want v controller to inherit from activerecordcontroller - instead it should talk to actors to get live state
@MultipartConfig(maxFileSize = 1024 * 1024)
class VehicleController(implicit swagger: Swagger) extends ActiveRecordController[Vehicle]("vehicle", swagger, Vehicle) with FileUploadSupport {

  /**
   * We allow reading vehicles if the vehicle is not protected or the user has suitable permissions
   */
  override protected def requireReadAccess(o: Vehicle) = {
    requireAccessCode(o.userId.getOrElse(-1L), o.viewPrivacy, ApiController.defaultVehicleViewAccess)
    super.requireReadAccess(o)
  }

  /**
   * Filter read access to a potentially protected record.  Subclasses can override if they want to restrict reads based on user or object
   * If not allowed, override should call haltUnauthorized()
   */
  override protected def requireWriteAccess(o: Vehicle) = {
    requireAccessCode(o.userId.getOrElse(-1L), o.controlPrivacy, ApiController.defaultVehicleControlAccess)
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
    (apiOperation[List[Mission]]("addMission")
      summary s"Add a new mission (as a tlog, bog or log)"
      consumes (Mission.mimeType)
      parameters (
        bodyParam[Array[Byte]],
        pathParam[String]("id").description(s"Id of $aName to be have mission added")))

  post("/:id/missions", operation(addMissionInfo)) {
    val v = requireWriteAccess(findById)
    val files = fileMultiParams.values.flatMap { s => s }

    var errMsg: Option[String] = None
    val created = files.flatMap { payload =>
      warn(s"Considering ${payload.name} ${payload.fieldName} ${payload.contentType}")
      val ctype = {
        if (payload.name.endsWith(".tlog")) // In case the client isn't smart enough to set mime types
          Mission.mimeType
        else
          payload.contentType.getOrElse(haltBadRequest("content-type not set"))
      }
      if (Mission.mimeType != ctype) {
        val msg = (s"${payload.name} did not seem to be a TLOG")
        errMsg = Some(msg)
        None
      } else {
        info(s"Processing tlog upload for vehicle $v, numBytes=${payload.get.size}, notes=${payload.name}")

        val m = v.createMission(payload.get, Some(payload.name))

        // Make this new mission show up on the recent flights list
        val space = SpaceSupervisor.find()
        SpaceSupervisor.tellMission(space, m)

        Some(m)
      }
    }.toList

    // If we had exactly one bad file, tell the client there was a problem via an error code.
    // Otherwise, claim success (this allows users to drag and drop whole directories and we'll cope with
    // just the tlogs).
    errMsg.foreach { msg =>
      if (files.size == 1)
        haltBadRequest(msg)
    }

    warn(s"Returning ${created.mkString(", ")}")

    // Return the missions that were created
    created
  }

}

