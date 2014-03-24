package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger
import com.geeksville.util.URLUtil
import com.geeksville.dapi.model._

class VehicleController(implicit swagger: Swagger) extends ApiController[Vehicle]("vehicle", swagger, Vehicle) {

  // FIXME - make this code actually do something
  rwField[String]("mode", "FIXME", { (v) => })
  //roField[Location]("location", null)
  //roField[Attitude]("attitude", null)
  roField[Double]("airspeed", 1.5)
  roField[Double]("groundspeed", 1.5)
  roField[Double]("batteryVolt", 1.5)
  roField[Double]("batterySOC", 1.5)

  roField[List[Int]]("rcChannels", List(1, 2, 3))
  woField[List[Int]]("rcOverrides", { (v) => })

  //rwField[Location]("targetLocation", null, { (v) => })

  // FIXME - need to use correct domain objects (Waypoints)
  //rwField[List[Location]]("waypoints", null, { (v) => })

  // FIXME add operations to list/add/delete missions
}

