package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger
import com.geeksville.dapi.model._
import java.net.URL

class MissionController(implicit swagger: Swagger) extends ActiveRecordController[Mission]("mission", swagger, Mission) {

  override protected def getOp = (super.getOp
    parameter queryParam[Option[String]]("within").description("Flights within a specified GeoJSON polygon")
    parameter queryParam[Option[Boolean]]("completed").description("Completed flights only")
    parameter queryParam[Option[Boolean]]("live").description("Live flights only"))

  //raField[Mavlink]("mavlink", null, { (v) => })
  //roField[List[Location]]("location", null)
  //roField[List[String]]("mode", null)

  // Send a response with a recommended filename
  def OkWithFilename(payload: Any, filename: String) = {
    Ok(payload, Map(
      // "Content-Type"        -> (file.contentType.getOrElse("application/octet-stream")),
      "Content-Disposition" -> ("attachment; filename=\"" + filename + "\"")))
  }

  roField("messages.tlog") { (o) =>
    contentType = Mission.mimeType
    OkWithFilename(o.tlogBytes.getOrElse(haltNotFound()), o.tlogId.get + ".tlog")
  }

  private def getModel(o: Mission) = {
    val bytes = o.tlogBytes.getOrElse(haltNotFound())
    PlaybackModel.fromBytes(bytes, true)
  }

  roField("messages.kml") { (o) =>
    contentType = "application/vnd.google-earth.kml+xml"

    // FIXME - we should pull our static content (icons etc... from a cdn)
    getModel(o).toKMLBytes(uriBase)
  }

  roField("messages.kmz") { (o) =>
    contentType = "application/vnd.google-earth.kmz"

    // FIXME - we should pull our static content (icons etc... from a cdn)
    getModel(o).toKMZBytes(uriBase, false)
  }

  roField("messages.gmaps.kmz") { (o) =>
    contentType = "application/vnd.google-earth.kmz"

    // FIXME - we should pull our static content (icons etc... from a cdn)
    getModel(o).toKMZBytes(uriBase, true)
  }
}

