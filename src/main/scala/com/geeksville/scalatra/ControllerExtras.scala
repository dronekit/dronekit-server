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
import org.scalatra.ActionResult
import org.scalatra.ResponseStatus
import org.eclipse.jetty.io.EofException
import org.scalatra.BadRequest
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JObject
import akka.util.Timeout
import scala.concurrent.duration._
import org.scalatra.FutureSupport
import com.geeksville.akka.MockAkka
import javax.servlet.http.HttpServletResponse
import java.util.Date

/**
 * For some strange reason the scalatra folks made their HaltException private.  If you want to throw an exception but include
 * a http error code hint, just throw this instead
 */
case class WebException(val code: Int, msg: String) extends Exception(msg)

/**
 * Mixin of my scalatra controller extensions
 */
trait ControllerExtras extends ScalatraBase with FutureSupport with Logging {

  /// If any of our controllers use akka, default to a 30 second timeout before reporting fault to client
  protected implicit val defaultTimeout = Timeout(30 seconds)

  // Akka implicits for FutureSupport
  lazy val system = MockAkka.system
  protected implicit def executor = system.dispatcher

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

  /**
   * Used to prevent caching
   */
  def applyNoCache(implicit response: HttpServletResponse) {
    val expire = new Date().toString

    // This was an http 1.0 header
    response.setHeader("Expires", expire)
    response.setHeader("Last-Modified", expire)
    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")

    // this was only for http 1.0 and only for requests, not responses
    // response.addHeader("Pragma", "no-cache")
  }

  /**
   * Used to prevent caching
   */
  def applyCache(numSecs: Int)(implicit response: HttpServletResponse) {
    val now = new Date().toString
    val expire = new Date(System.currentTimeMillis + numSecs * 1000).toString

    // This was an http 1.0 header
    response.setHeader("Expires", expire)
    response.setHeader("Last-Modified", now)
    response.setHeader("Cache-Control", s"private, max-age=$numSecs")
  }

  /// Is the user app running on something served from localhost?  If so, they are a developer - so turn off caching etc...
  def isAppDeveloper = request.referrer.getOrElse("").startsWith("http://localhost")

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

    debug(s"  ClientIP: $clientIP")
  }

  /// A one line log msg
  def dumpRequestSummary() {
    debug(s"REQ: $clientIP ${request.getMethod} ${request.getRequestURL} ")
  }

  /// Return the client's IP address (being careful to work if we are behind a load balancer)
  def clientIP = {
    request.header("X-Real-IP").getOrElse(request.getRemoteAddr)
  }

  /// Better error messages for the user
  super[ScalatraBase].error {
    case e: WebException =>
      ActionResult(ResponseStatus(e.code, e.getMessage), e.getMessage, Map.empty)

    case e: EofException =>
      // This failure can occur while parsing FileUploadSupport if the client closes the stream
      error("Client dropped connection")
      BadRequest("Client dropped connection - good bye")

    case e: Throwable =>
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

  // We return the error message in the body
  private def verboseHalt(status: JInteger, reason: String) = {
    halt(status, reason = reason, body = JObject("message" -> JString(reason)))
  }

  /// syntatic sugar
  def haltUnauthorized(reason: String) = verboseHalt(401, reason = reason)
  def haltForbidden(reason: String) = verboseHalt(403, reason = reason)
  def haltQuotaExceeded(reason: String) = verboseHalt(403, reason = reason)
  def haltNotFound(reason: String) = verboseHalt(404, reason = reason)
  def haltMethodNotAllowed(reason: String) = verboseHalt(405, reason = reason)
  def haltNotAcceptable(reason: String) = verboseHalt(406, reason = reason)
  def haltConflict(reason: String) = verboseHalt(409, reason = reason)
  def haltGone(reason: String) = verboseHalt(410, reason = reason)
  def haltBadRequest(reason: String) = verboseHalt(400, reason = reason)
  def haltNotImplemented(reason: String) = verboseHalt(501, reason = reason)
  def haltInternalServerError(reason: String) = verboseHalt(500, reason = reason)
}

