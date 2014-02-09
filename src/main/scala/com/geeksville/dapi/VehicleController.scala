package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger

class VehicleController(implicit swagger: Swagger) extends ApiController[Vehicle]("vehicle", swagger) {
}

// A Flower object to use as a faked-out data model
case class Vehicle(id: String, name: String)

