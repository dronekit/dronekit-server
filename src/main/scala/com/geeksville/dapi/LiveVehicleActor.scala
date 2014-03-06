package com.geeksville.dapi

import akka.actor.Actor
import akka.actor.ActorLogging
import com.geeksville.mavlink.TimestampedMessage

/// Sent when a vehicle connects to the server
case class VehicleConnected()

case class VehicleDisconnected()

/**
 * An actor that represents a connection to a live vehicle.  GCSAdapters use this object to store mavlink from vehicle and publishes from this object
 * can cause GCSAdapters to send messages to the vehicle (from the web).
 *
 * Supported message types:
 * TimestampedMessage - used to add to the running log/new data received from the vehicle
 * VehicleConnected - sent by the GCSActor when the vehicle first connects
 * VehicleDisconnected - sent by the GCSActor when the vehicle disconnects
 */
class LiveVehicleActor(val vehicleId: String) extends Actor with ActorLogging {
  def receive = {
    case VehicleConnected() =>
      log.debug("Vehicle connected")
    case VehicleDisconnected() =>
      log.debug("Vehicle disconnected")
    // FIXME - store tlog to server

    case msg: TimestampedMessage =>
      log.debug(s"Ignoring $msg")
    // FIXME - add to running tlog and publish so watchers can do the right thing
  }
}