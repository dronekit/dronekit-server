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

/// FIXME - we don't want v controller to inherit from activerecordcontroller - instead it should talk to actors to get live state
@MultipartConfig(maxFileSize = 1024 * 1024)
class VehicleController(implicit swagger: Swagger) extends ActiveRecordController[Vehicle]("vehicle", swagger, Vehicle) with FileUploadSupport {

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

  private val addMissionInfo =
    (apiOperation[String]("addMission")
      summary s"Add a new mission (as a tlog, bog or log)"
      parameters (
        pathParam[String]("id").description(s"Id of $aName to be appended")))

  post("/:id/missions", operation(addMissionInfo)) {
    val v = findById
    val payload = fileParams("payload")
    val ctype = payload.contentType.getOrElse(haltBadRequest("content-type not set"))
    if (Mission.mimeType != ctype)
      haltBadRequest("invalid content-type")

    v.createMission(payload.get)
  }

}

