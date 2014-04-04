package com.geeksville.dapi

import org.scalatra.{ ScalatraServlet, ScalatraBase }
import org.scalatra.json.NativeJsonSupport
import org.scalatra.InternalServerError
import grizzled.slf4j.Logging
import java.net.URL

abstract class DroneHubStack extends ScalatraServlet with Logging {
  super[ScalatraServlet].error {
    case e: Exception =>
      contentType = "text/html"

      error("Fatal exception", e)

      InternalServerError(<html>
                            <body>
                              <p>
                                Oh my - you've found a problem with this beta-test.  Our geeks have been alerted and will work on a fix shortly...  Thank you for your help.
                              </p>
                              <p>
                                { e }
                              </p>
                            </body>
                          </html>)
  }

  /// Where was our app served up from?
  def uriBase = {
    val url = if (request.getServerPort == 80)
      new URL(request.getScheme(),
        request.getServerName(), "")
    else
      new URL(request.getScheme(),
        request.getServerName(),
        request.getServerPort(), "")

    url.toURI
  }

  /// If we are on localhost, lie and claim we are on the public server (so gmaps will work)
  def publicUriBase = {
    val h = request.getServerName()

    new URL(request.getScheme(), if (h == "localhost") "nestor.3dr.com" else h, "").toURI
  }

  /// syntatic sugar
  def haltNotFound() = halt(404)
  def haltBadRequest(reason: String = null) = halt(400, reason = reason)
}