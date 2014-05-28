package com.geeksville.scalatra

import org.scalatra.Control
import org.scalatra.ScalatraServlet
import grizzled.slf4j.Logging
import org.scalatra.InternalServerError
import java.net.URL
import org.scalatra.ScalatraBase
import java.lang.{ Integer => JInteger }
import org.scalatra.HaltException
import com.newrelic.api.agent.NewRelic
import com.geeksville.util.AnalyticsService
import scala.collection.JavaConverters._

/**
 * Mixin of my scalatra controller extensions
 */
trait ControllerExtras extends ScalatraBase with Logging {

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

  def dumpRequest() {
    debug(s"Request dump: $request")

    request.headers.foreach { h =>
      debug(s"  Header: $h")
    }
    request.cookies.foreach { h =>
      debug(s"  Cookie: $h")
    }

    if (request.contentType.getOrElse("") == "multipart/form-data")
      debug(s"  Parts: " + request.getParts.asScala.mkString(","))

    debug(s"  ClientIP: ${request.getRemoteHost}")
  }

  /// Better error messages for the user
  super[ScalatraBase].error {
    case e: Exception =>
      contentType = "text/html"

      println(e)
      error("Fatal exception", e)
      AnalyticsService.reportException("scalatra exception", e)

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

  /// Print a log message any time we bail on a request
  override def halt[T: Manifest](
    status: JInteger = null,
    body: T = (),
    headers: Map[String, String] = Map.empty,
    reason: String = null): Nothing = {
    warn(s"Halt $status: $reason")
    super.halt(status, body, headers, reason)
  }

  /// syntatic sugar
  def haltUnauthorized(reason: String) = halt(401, reason = reason)
  def haltForbidden(reason: String) = halt(403, reason = reason)
  def haltQuotaExceeded(reason: String) = halt(403, reason = reason)
  def haltNotFound(reason: String) = halt(404, reason = reason)
  def haltMethodNotAllowed(reason: String) = halt(405, reason = reason)
  def haltConflict(reason: String) = halt(409, reason = reason)
  def haltBadRequest(reason: String) = halt(400, reason = reason)
  def haltNotImplemented(reason: String) = halt(501, reason = reason)
  def haltInternalServerError(reason: String) = halt(500, reason = reason)
}

