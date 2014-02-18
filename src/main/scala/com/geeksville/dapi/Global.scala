package com.geeksville.dapi

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem

object Global {
  private val config = ConfigFactory.load()

  // Our akka actor system
  val system = ActorSystem("MyApp", config)

  def start() {

  }
}