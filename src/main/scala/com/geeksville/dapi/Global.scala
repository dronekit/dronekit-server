package com.geeksville.dapi

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import com.geeksville.util.TCPListener
import akka.actor.Props
import com.geeksville.apiproxy.APIConstants
import com.geeksville.akka.TCPListenerActor
import java.io.File
import com.geeksville.akka.MockAkka
import com.geeksville.dapi.test.SimGCSClient
import com.geeksville.dapi.test.SimWebController
import com.geeksville.dapi.test.SimSimpleVehicle

object Global {
  def system = MockAkka.system

  val scheme = "http" // eventually https
  val hostname = "www.droneshare.com"
  val rootUrl = s"$scheme://$hostname"
  val senderEmail = "support@droneshare.com"
  val appName = "Droneshare"

  val simServerHostname = "localhost"

  lazy val simGCSClient = system.actorOf(Props(new SimGCSClient(simServerHostname, false)))
  lazy val simWebController = system.actorOf(Props(new SimWebController(simServerHostname)))
  lazy val simSimpleVehicle = system.actorOf(Props(new SimSimpleVehicle(60, simServerHostname)))

  def setConfig() {
    val configOverride = new File(System.getProperty("user.home") + "/nestor.conf")
    if (configOverride.exists)
      System.setProperty("config.file", configOverride.toString)
    else
      println(s"No config override file found.  You should probably create $configOverride")

    val cfg = ConfigFactory.load()
    MockAkka.configOverride = Some(cfg)
    //println(cfg.getString("dapi.threescale.serviceId"))
  }
}