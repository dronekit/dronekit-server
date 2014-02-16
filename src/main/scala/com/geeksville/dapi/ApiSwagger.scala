package com.geeksville.dapi

import org.scalatra.swagger.{ NativeSwaggerBase, Swagger }
import org.scalatra.ScalatraServlet
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json.NativeJsonSupport

class ResourcesApp(implicit val swagger: Swagger) extends ScalatraServlet with NativeSwaggerBase {
  implicit override val jsonFormats: Formats = DefaultFormats
}

class ApiSwagger extends Swagger("1.0", "1")