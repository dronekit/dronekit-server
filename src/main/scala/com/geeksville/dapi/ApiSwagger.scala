package com.geeksville.dapi

import org.scalatra.swagger.{ NativeSwaggerBase, Swagger }
import org.scalatra.ScalatraServlet
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.ApiInfo
import com.geeksville.json.ActiveRecordSerializer
import com.geeksville.dapi.model.DroneModelFormats
import com.geeksville.json.GeeksvilleFormats

class ResourcesApp(implicit val swagger: Swagger) extends DroneHubStack with NativeSwaggerBase {
  // It is very important that we use the json formats for swagger (they have a bunch of custom serializers)
  implicit override val jsonFormats: Formats = super[NativeSwaggerBase].jsonFormats ++ GeeksvilleFormats ++ DroneModelFormats + new ActiveRecordSerializer
}

class ApiSwagger extends Swagger("1.0", "1", ApiInfo("DroneAPIHub", "Central drone server", "TBD", "kevinh@geeksville.com", "TBD", "TBD")) {

}