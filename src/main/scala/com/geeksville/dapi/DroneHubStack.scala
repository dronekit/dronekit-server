package com.geeksville.dapi

import org.scalatra.{ ScalatraServlet, ScalatraBase }
import org.scalatra.json.NativeJsonSupport
import org.scalatra.InternalServerError
import grizzled.slf4j.Logging
import java.net.URL
import com.geeksville.dapi.auth.AuthenticationSupport
import com.geeksville.scalatra.ControllerExtras
import org.scalatra.GZipSupport

abstract class DroneHubStack extends ScalatraServlet with AuthenticationSupport with ControllerExtras with GZipSupport {

  before() {
    logger.debug("Handle " + request)
  }

  /// If we are on localhost, lie and claim we are on the public server (so gmaps will work)
  def publicUriBase = {
    val h = request.getServerName()

    new URL(request.getScheme(), if (h == "localhost") "nestor.3dr.com" else h, "").toURI
  }
}