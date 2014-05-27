package com.geeksville.dapi

import org.scalatra.{ ScalatraServlet, ScalatraBase }
import org.scalatra.json.NativeJsonSupport
import org.scalatra.InternalServerError
import grizzled.slf4j.Logging
import java.net.URL
import com.geeksville.dapi.auth.AuthenticationSupport
import com.geeksville.scalatra.ControllerExtras
import org.scalatra.GZipSupport
import org.json4s.Formats
import org.json4s.DefaultFormats
import com.geeksville.json.GeeksvilleFormats
import com.geeksville.dapi.model.DroneModelFormats
import com.geeksville.scalatra.ThreescaleSupport
import org.scalatra.CorsSupport
import com.geeksville.util.AnalyticsService

abstract class DroneHubStack extends ScalatraServlet with ControllerExtras with AuthenticationSupport with GZipSupport with NativeJsonSupport with ThreescaleSupport {

  // Sets up automatic case class to JSON output serialization
  protected implicit def jsonFormats: Formats = DefaultFormats ++ GeeksvilleFormats ++ DroneModelFormats

  before() {
    logger.debug("Handle " + request)
    AnalyticsService.addBreadcrumb("clientIP", request.getRemoteAddr)
  }

  /// We allow CORS requests from anywhere - FIXME - check if secure?
  options("/*") {
    // response.setHeader("Access-Control-Allow-Methods", "GET, POST")

    Option(request.getHeader("Access-Control-Request-Headers")).foreach { h =>
      response.setHeader("Access-Control-Allow-Headers", h)
    }
  }

  /// If we are on localhost, lie and claim we are on the public server (so gmaps will work)
  def publicUriBase = {
    val h = request.getServerName()

    new URL(request.getScheme(), if (h == "localhost") "api.3dr.com" else h, "").toURI
  }

}