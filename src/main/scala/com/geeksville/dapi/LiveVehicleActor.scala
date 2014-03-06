package com.geeksville.dapi

import akka.actor.Actor
import akka.actor.ActorLogging

/**
 * An actor that represents a connection to a live vehicle.  GCSAdapters use this object to store mavlink from vehicle and publishes from this object
 * can cause GCSAdapters to send messages to the vehicle (from the web).
 */
class LiveVehicleActor extends Actor with ActorLogging {
  // FIXME - add real code
  override def receive = Actor.emptyBehavior

}