package com.geeksville.dapi

import com.geeksville.akka.MockAkka
import akka.actor.Props
import com.geeksville.dapi.test.SimGCSClient
import com.geeksville.dapi.temp.NestorImporter
import com.geeksville.dapi.temp.DoImport
import com.geeksville.dapi.test.RunTest
import com.geeksville.dapi.model.Tables

/**
 * Special admin operations
 */
class AdminController extends DroneHubStack {

  def system = MockAkka.system

  lazy val simClient = system.actorOf(Props(new SimGCSClient), "simClient")

  lazy val nestorImport = system.actorOf(Props(new NestorImporter), "importer")

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
}