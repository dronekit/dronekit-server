package com.geeksville.scalatra

import org.scalatra.Control
import org.scalatra.ScalatraServlet
import grizzled.slf4j.Logging
import org.scalatra.InternalServerError
import java.net.URL
import org.scalatra.ScalatraBase

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

  /// Better error messages for the user
  super[ScalatraBase].error {
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

  /// syntatic sugar
  def haltUnauthorized(reason: String = null) = halt(401, reason = reason)
  def haltForbidden(reason: String = null) = halt(403, reason = reason)
  def haltNotFound(reason: String = null) = halt(404, reason = reason)
  def haltMethodNotAllowed(reason: String = null) = halt(405, reason = reason)
  def haltConflict(reason: String = null) = halt(409, reason = reason)
  def haltBadRequest(reason: String = null) = halt(400, reason = reason)
  def haltNotImplemented(reason: String = null) = halt(501, reason = reason)
  def haltInternalServerError(reason: String = null) = halt(500, reason = reason)
}