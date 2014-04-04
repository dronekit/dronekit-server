package com.geeksville.dapi

import org.scalatra.swagger.{ NativeSwaggerBase, Swagger }
import org.scalatra.ScalatraServlet
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.ApiInfo

class ResourcesApp(implicit val swagger: Swagger) extends DroneHubStack with NativeSwaggerBase {
  implicit override val jsonFormats: Formats = DefaultFormats
}

class ApiSwagger extends Swagger("1.0", "1", ApiInfo("DroneAPIHub", "Central drone server", "TBD", "kevinh@geeksville.com", "TBD", "TBD"))