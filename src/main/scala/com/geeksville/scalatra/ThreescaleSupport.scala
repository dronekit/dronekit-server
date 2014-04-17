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

/**
 * A mixin that adds API validation through threescale
 */
trait ThreescaleSupport extends ScalatraBase with ControllerExtras {
  // FIXME - we should not be making a separate threescale actor for each endpoint
  private lazy val threeActor: ActorRef = MockAkka.system.actorOf(Props(new ThreeActor(MockAkka.config.getString("threescale.apiKey"))))
  private lazy val service = MockAkka.config.getString("threescale.serviceId")

  def requireServiceAuth(metricIn: String) {
    val metric = metricIn.replace('/', '_') // 3scale converts slashes to underscores
    requireServiceAuth(Map(metric -> "1"))
  }

  /**
   * Check for authorization to use serviceId X.  will haltUnauthorized if quota exceeded
   */
  def requireServiceAuth(metrics: Map[String, String]) {
    // FIXME include a better URL
    val userkey = params.getOrElse("api_key", haltUnauthorized("api key is required. See http://nestor.3dr.com/develop"))
    val req = AuthRequest(userkey, service, metrics)

    implicit val timeout = Timeout(5 seconds)
    val future = threeActor ? req
    val result = Await.result(future, timeout.duration).asInstanceOf[AuthorizeResponse]

    // FIXME - if threescale is too slow show an error msg in logs and just let the user go
    if (!result.success) {
      warn(s"3scale denied $req due to ${result.getReason}")
      haltQuotaExceeded("Quota exceeded: " + result.getReason)
    } else
      debug(s"3scale said okay to $req, plan ${result.getPlan}")
  }
}