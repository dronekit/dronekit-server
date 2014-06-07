package com.geeksville.dapi

import com.geeksville.akka.MockAkka
import akka.actor.Props
import com.geeksville.dapi.test.SimGCSClient
import com.geeksville.dapi.temp.NestorImporter
import com.geeksville.dapi.temp.DoImport
import com.geeksville.dapi.model.Tables
import com.geeksville.akka.AkkaReflector
import scala.concurrent.Await
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.xml.Elem
import org.scalatra.atmosphere._
import org.scalatra.swagger.SwaggerSupport
import org.scalatra.swagger.Swagger
import com.geeksville.dapi.test.PlaybackGCSClient
import org.scalatra.CorsSupport
import org.scalatra.AsyncResult
import _root_.akka.actor.{ ActorRef, Actor, Props, ActorSystem }
import _root_.akka.util.Timeout
import _root_.akka.pattern.ask
import org.scalatra.FutureSupport
import org.scalatra.CorsSupport
import com.geeksville.dapi.model.Mission
import com.github.aselab.activerecord.dsl._
import scala.xml._

/**
 * A little stub controller that just redirects to www.droneshare.com (we can't use CNAMEs for root domains, so instead we do this)
 */
class RedirectController extends DroneHubStack {

  get("/") {
    redirect("http://www.droneshare.com")
  }
}