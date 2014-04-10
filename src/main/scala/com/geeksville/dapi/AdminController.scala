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

  before() {
    requireLogin("basic")
  }

  get("/import") {
    nestorImport ! DoImport
  }

  get("/sim/full") {
    val host = multiParams("host").headOption.getOrElse("localhost")
    simClient ! RunTest(host, false)
  }

  get("/sim/quick") {
    val host = multiParams("host").headOption.getOrElse("localhost")
    simClient ! RunTest(host, true)
  }

  get("/db/create") {
    Tables.create
  }

  get("/akka") {
    implicit val timeout = Timeout(5 seconds)

    akkaReflect ! AkkaReflector.PollMsg
    Thread.sleep(5000) // Super hackish way to give 5 secs for actors to reply with their ids

    val future = akkaReflect ? AkkaReflector.GetHtmlMsg
    Await.result(future, timeout.duration).asInstanceOf[Elem]
  }
}