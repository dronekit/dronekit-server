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
import com.geeksville.akka.TimesteppedActor

/// A base class for simulated vehicles - it just starts a mission, subclass needs to provide more interesting behavior
abstract class SimVehicle(systemId: Int, host: String, val keep: Boolean) extends SimClient(systemId, host) with TimesteppedActor {
  import SimClient._
  import context._

  val generation = SimGCSClient.nextGeneration

  /// Subclasses can override, but by default we use a random UUID
  def uuid = UUID.nameUUIDFromBytes(Array(systemId.toByte, generation.toByte) ++ getMachineId)

  sendMavlink(makeStatusText("Starting sim vehicle"))

  def numPoints: Int
  def interval: Double

  override def postStop() {
    webapi.stopMission(keep)
    super.postStop()
  }

  override def startConnection() {
    super.startConnection()

    log.info("Starting mission")
    webapi.startMission(keep, UUID.randomUUID)

    log.info(s"Created sim vehicle $systemId: $uuid")
    webapi.setVehicleId(uuid.toString, interfaceNum, systemId, isControllable)
  }

  /// Dear GCS, please send this packet
  override def sendMavlink(b: Array[Byte]) {
    val msg = MavlinkUtils.bytesToPacket(b)
    log.warning(s"Server wants us to send $msg, but we are ignoring!")
  }

  protected def doNextStep(): Unit
}

object SimVehicle extends Logging {

  /// We use this to ensure each new run of the simulator picks a different set of UUIDs - but we want to always start from
  /// the same set so the DB doesn't fill with a zilliong records
  private var generation = 0

  def nextGeneration() = {
    val r = generation
    generation += 1
    r
  }
}