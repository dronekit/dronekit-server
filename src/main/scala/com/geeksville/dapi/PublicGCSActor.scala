package com.geeksville.dapi

import akka.actor.Actor
import akka.util.ByteString
import akka.actor.ActorLogging

/**
 * This singleton? actor
 */
class PublicGCSActor extends Actor with ActorLogging {
  def receive = {
    case x: LoginMsg ⇒ sender
  }
}