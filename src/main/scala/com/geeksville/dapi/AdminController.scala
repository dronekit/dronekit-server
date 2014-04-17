package com.geeksville.dapi

import com.geeksville.akka.MockAkka
import akka.actor.Props
import com.geeksville.dapi.test.SimGCSClient
import com.geeksville.dapi.temp.NestorImporter
import com.geeksville.dapi.temp.DoImport
import com.geeksville.dapi.test.RunTest
import com.geeksville.dapi.model.Tables
import com.geeksville.akka.AkkaReflector
import scala.concurrent.Await
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.xml.Elem

/**
 * Special admin operations
 */
class AdminController extends DroneHubStack {

  def system = MockAkka.system

  lazy val simClient = system.actorOf(Props(new SimGCSClient), "simClient")

  lazy val nestorImport = system.actorOf(Props(new NestorImporter), "importer")

  lazy val akkaReflect = system.actorOf(Props(new AkkaReflector), "akkaReflect")

  def host = multiParams("host").headOption.getOrElse("localhost")

  before() {
    requireAdmin()
    requireServiceAuth("admin")
  }

  get("/import/:count") {
    nestorImport ! DoImport(params("count").toInt)
    "started import"
  }

  get("/sim/huge") {
    simClient ! RunTest(host, "bigtest")
    "started sim"
  }

  get("/sim/full") {
    simClient ! RunTest(host, "test")
  }

  get("/sim/quick") {
    simClient ! RunTest(host, "quick")
    "started sim"
  }

  // FIXME -very dangerous remove before production
  get("/db/reset") {
    Tables.reset
    "DB Reset completed"
  }

  get("/db/create") {
    Tables.create
    "DB created"
  }

  get("/akka") {
    implicit val timeout = Timeout(5 seconds)

    akkaReflect ! AkkaReflector.PollMsg
    Thread.sleep(5000) // Super hackish way to give 5 secs for actors to reply with their ids

    val future = akkaReflect ? AkkaReflector.GetHtmlMsg
    Await.result(future, timeout.duration).asInstanceOf[Elem]
  }
}