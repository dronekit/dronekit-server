package com.geeksville.dapi

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import com.geeksville.util.TCPListener
import akka.actor.Props
import com.geeksville.apiproxy.APIConstants
import com.geeksville.akka.TCPListenerActor

object Global {
  private val config = ConfigFactory.load()

  // Our akka actor system
  val system = ActorSystem("MyApp", config)

  // Start up our tcp listener
  system.actorOf(Props(new TCPListenerActor[TCPGCSActor](APIConstants.DEFAULT_TCP_PORT)), "tcpListener")
}