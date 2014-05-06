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

    MockAkka.system.actorOf(Props(new ThreeActor(key)))
  }

}

/**
 * A mixin that adds API validation through threescale
 */
trait ThreescaleSupport extends ScalatraBase with ControllerExtras {
  import ThreescaleSupport._

  def requireServiceAuth(metricIn: String) {
    val metric = metricIn.replace('/', '_') // 3scale converts slashes to underscores
    requireServiceAuth(Map(metric -> "1"))
  }

  /**
   * Look for API key in an authorization header, or if not there, then in the query string.
   */
  private def apiKey = {
    //debug(s"*** Looking for API keys in $request")
    val authHeaders = request.getHeaders("Authorization").asScala.toSeq

    val headerkeys = authHeaders.flatMap { s =>
      s match {
        case HeaderRegex(key) => Some(key)
        case _ => None
      }
    }.toSeq
    //debug(s"*** Auth headers ${authHeaders.mkString(",")} => keys=${headerkeys.mkString(",")}")

    if (headerkeys.isEmpty)
      params.getOrElse("api_key", haltUnauthorized("api key is required. See http://nestor.3dr.com/develop"))
    else if (headerkeys.size > 1)
      haltBadRequest("Too many API keys")
    else
      headerkeys.head
  }

  /**
   * Check for authorization to use serviceId X.  will haltUnauthorized if quota exceeded
   */
  def requireServiceAuth(metrics: Map[String, String]) {
    // FIXME include a better URL for developer site
    val req = AuthRequest(apiKey, service, metrics)

    implicit val timeout = Timeout(5 seconds)
    val future = ask(threeActor, req).mapTo[AuthorizeResponse]

    try {
      val result = Await.result(future, timeout.duration)

      // FIXME - if threescale is too slow show an error msg in logs and just let the user go
      if (!result.success) {
        warn(s"3scale denied $req due to ${result.getReason}")
        haltQuotaExceeded("Quota exceeded: " + result.getReason)
      } else {
        //debug(s"3scale said okay to $req, plan ${result.getPlan}")
      }
    } catch {
      case ex: TimeoutException =>
        error(s"Threescale is DOWN - allowing transaction...")
    }
  }
}