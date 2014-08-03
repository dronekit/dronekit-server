package com.geeksville.dapi.test

import akka.actor.Actor
import akka.actor.ActorLogging
import com.geeksville.apiproxy.GCSHooksImpl
import com.geeksville.util.Using._
import java.io.BufferedInputStream
import akka.actor.Props
import com.geeksville.mavlink.TlogStreamReceiver
import com.geeksville.mavlink.MavlinkEventBus
import com.geeksville.apiproxy.LiveUploader
import com.geeksville.apiproxy.GCSHooks
import com.geeksville.apiproxy.APIProxyActor
import java.util.UUID
import akka.actor.Terminated
import akka.actor.PoisonPill
import com.geeksville.apiproxy.StopMissionAndExitMsg
import com.geeksville.apiproxy.APIConstants
import com.geeksville.mavlink.MavlinkStreamReceiver
import scala.concurrent.duration._
import akka.pattern.ask
import scala.concurrent.Await
import akka.actor.Identify
import akka.util.Timeout
import com.geeksville.flight.VehicleSimulator
import org.mavlink.messages.MAVLinkMessage
import akka.actor.ActorContext
import grizzled.slf4j.Logging
import akka.actor.ActorSystem
import scala.util.Random
import com.geeksville.flight.Location
import com.geeksville.flight.HeartbeatSender
import org.mavlink.messages.MAV_TYPE
import org.mavlink.messages.MAV_MODE_FLAG
import org.mavlink.messages.MAV_AUTOPILOT
import java.net.NetworkInterface
import com.geeksville.akka.DebuggableActor
import com.geeksville.flight.SendMessage
import com.geeksville.akka.AkkaTools
import scala.util.Success
import scala.util.Failure
import com.geeksville.mavlink.MavlinkUtils

/// This vehicle just sits quietly (just sending heartbeats) and listens for commands from the web
class SimSimpleVehicle(val numPoints: Int, host: String, systemId: Int = 1, keep: Boolean = false) extends SimVehicle(systemId, host, keep) {
  import SimClient._

  val interval = 1.0

  /// For now just use a well known ID
  override def uuid = SimSimpleVehicle.singletonUUID

  override def doNextStep() {
    // log.debug("I'm a simple vehicle, just sitting here listening for commands...")
  }
}

object SimSimpleVehicle {
  val singletonUUID = UUID.fromString("1dd33cc0-1a7b-11e4-8c21-0800200c9a66")
}

