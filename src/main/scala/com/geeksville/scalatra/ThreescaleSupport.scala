package com.geeksville.scalatra

import org.scalatra.ScalatraBase
import akka.actor.ActorRef
import scala.concurrent.Await
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import com.geeksville.threescale.AuthRequest
import threescale.v3.api.AuthorizeResponse
import com.geeksville.akka.MockAkka
import akka.actor.Props
import com.geeksville.threescale.ThreeActor
import scala.collection.JavaConverters._
import java.util.concurrent.TimeoutException
import com.newrelic.api.agent.NewRelic
import com.geeksville.threescale.WhitelistStrict
import com.geeksville.util.AnalyticsService
import com.geeksville.threescale.WhitelistOkay

object ThreescaleSupport {
  private val HeaderRegex = "DroneApi apikey=\"(.*)\"".r

  def config = MockAkka.config

  private lazy val service = {
    val keyname = "dapi.threescale.serviceId"
    if (config.hasPath(keyname))
      config.getString(keyname)
    else
      "unspecified"
  }

  private lazy val threeActor: ActorRef = synchronized {
    val keyname = "dapi.threescale.apiKey"

    val key = if (config.hasPath(keyname))
      Some(config.getString(keyname))
    else
      None

    val whitelist = Seq(
      // Don't let anyone but us (or dev on local machine use the mds key)
      WhitelistStrict("eb34bd67.megadroneshare",
        "http://localhost",
        "http://omega.droneshare.com",
        "http://alpha.droneshare.com",
        "http://beta.droneshare.com",
        "http://www.droneshare.com"),
      WhitelistOkay("eb34bd67.newrelic"))
    MockAkka.system.actorOf(Props(new ThreeActor(key, whitelist)))
  }

}

/**
 * A mixin that adds API validation through threescale
 */
trait ThreescaleSupport extends ScalatraBase with ControllerExtras {

  import ThreescaleSupport._

  final def requireServiceAuth(metricIn: String) {
    val metric = metricIn.replace('/', '_') // 3scale converts slashes to underscores

    requireServiceAuth(Map(metric -> "1"))
  }

  protected def authHeaders = request.getHeaders("Authorization").asScala

  private def referrer = {
    // Find referer for api checking - we prefer 'Origin' as the new preferred but will fall back to Referer
    val originOpt = request.header("Origin")
    originOpt.orElse(request.referrer)
  }

  /// Headers that are simple the raw 3scale auth ID (not needed if using oauth)
  private def simpleAuthHeaders = authHeaders.flatMap { s =>
    s match {
      case HeaderRegex(key) => Some(key)
      case _ => None
    }
  }

  /**
   * Look for API key in an authorization header, or if not there, then in the query string.
   */
  private def apiKey = {
    //debug(s"*** Looking for API keys in $request")

    val headerkeys = simpleAuthHeaders.toSeq
    //debug(s"*** Auth headers ${authHeaders.mkString(",")} => keys=${headerkeys.mkString(",")}")

    if (headerkeys.isEmpty)
      params.getOrElse("api_key", haltUnauthorized("api_key is required. See http://api.3dr.com/develop"))
    else if (headerkeys.size > 1)
      haltBadRequest("Too many API keys")
    else
      headerkeys.head
  }

  /**
   * Check for threescale authorization to use serviceId X.  will haltUnauthorized if quota exceeded.
   */
  protected final def requireThreescaleAuth(key: String, metrics: Map[String, String]) {
    NewRelic.setProductName(key)

    // FIXME include a better URL for developer site
    val req = AuthRequest(key, service, referrer, metrics)

    val future = ask(threeActor, req).mapTo[AuthorizeResponse]

    try {
      val result = Await.result(future, defaultTimeout.duration)

      // FIXME - if threescale is too slow show an error msg in logs and just let the user go
      if (!result.success) {
        val msg = s"3scale denied $req due to ${result.getReason}"
        warn(msg)

        // For now tell us if anyone starts using bad keys
        AnalyticsService.reportException("3scale", new Exception(msg))

        haltQuotaExceeded("Quota exceeded: " + result.getReason)
      } else {
        //debug(s"3scale said okay to $req, plan ${result.getPlan}")
      }
    } catch {
      case ex: TimeoutException =>
        val msg = s"Threescale is DOWN - allowing transaction..."
        error(msg)
        AnalyticsService.reportException("3scale", new Exception(msg))
    }
  }

  /**
   * Check for authorization to use serviceId X.  will haltUnauthorized if quota exceeded.
   *
   * Can be overridden by subclasses to generate alternative checking (i.e. OAuthSupport)
   */
  def requireServiceAuth(metrics: Map[String, String]) = requireThreescaleAuth(apiKey, metrics)
}
