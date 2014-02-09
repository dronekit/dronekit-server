package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger

class MissionController(implicit swagger: Swagger) extends ApiController[Mission]("mission", swagger) {
}

// A Flower object to use as a faked-out data model
case class Mission(id: String, name: String)

