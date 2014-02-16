package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger

class MissionController(implicit swagger: Swagger) extends ApiController[Mission]("mission", swagger) {

  override protected def getOp = (super.getOp
    parameter queryParam[Option[String]]("within").description("Flights within a specified GeoJSON polygon")
    parameter queryParam[Option[Boolean]]("completed").description("Completed flights only")
    parameter queryParam[Option[Boolean]]("live").description("Live flights only"))

  raField[Mavlink]("mavlink", null, { (v) => })
  roField[List[Location]]("location", null)
  roField[List[String]]("mode", null)
}

// A Flower object to use as a faked-out data model
case class Mission(id: String, name: String)

// FIXME - unify with real model
case class Mavlink(time: Long, id: Int, payload: List[Byte])