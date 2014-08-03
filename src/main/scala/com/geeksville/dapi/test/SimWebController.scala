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

/// A base class for simulated vehicles - it just starts a mission, subclass needs to provide more interesting behavior
class SimWebController(host: String, val toControl: UUID = SimSimpleVehicle.singletonUUID) extends SimClient(SimWebController.controllerSysId, host) {
  import SimClient._

  override def postStop() {
    super.postStop()
  }

  override def startConnection() {
    super.startConnection()

    // log.info(s"Created sim vehicle $systemId: $uuid")
    // webapi.setVehicleId(uuid.toString, interfaceNum, systemId, isControllable)
  }

  /// Dear GCS, please send this packet
  override def sendMavlink(b: Array[Byte]) {
    val msg = MavlinkUtils.bytesToPacket(b)
    log.warning(s"Server us msg from vehicle $msg, but we are ignoring!")
  }

}

object SimWebController {
  val controllerSysId = 249 // FIXME - can be anything, but this makes for easier log reading
}
