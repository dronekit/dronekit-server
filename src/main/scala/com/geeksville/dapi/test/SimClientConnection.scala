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
import com.geeksville.apiproxy.GCSCallback

object SimClient extends Logging {
  val random = new Random(System.currentTimeMillis)

  def getMachineId = {
    val iface = NetworkInterface.getNetworkInterfaces.nextElement
    var addr = iface.getHardwareAddress

    if (addr == null)
      addr = Array(0.toByte) // If no network interfaces is available, just use a placeholder

    debug(s"Using $iface: ${addr.mkString(":")}")
    addr
  }
}

abstract class SimClient(val systemId: Int, host: String) extends DebuggableActor with ActorLogging with VehicleSimulator with HeartbeatSender with GCSCallback {
  import SimClient._

  private var seqNum = 0

  protected val webapi = new GCSHooksImpl(host)

  val interfaceNum = 0
  val isControllable = true

  // We are not a GCS - pretend to be a quad
  vehicleTypeCode = MAV_TYPE.MAV_TYPE_QUADROTOR
  autopilotCode = MAV_AUTOPILOT.MAV_AUTOPILOT_ARDUPILOTMEGA

  startConnection()

  sendMavlink(makeStatusText("Starting sim client"))

  override def postStop() {
    webapi.close()
    super.postStop()
  }

  protected def startConnection() {
    import SimGCSClient.loginName

    val email = "test-bob@3drobotics.com"
    val password = "sekrit"

    // Create user if necessary/possible
    if (webapi.isUsernameAvailable(loginName))
      webapi.createUser(loginName, password, Some(email))
    else
      webapi.loginUser(loginName, password)

    webapi.flush()
    webapi.setCallback(this)
  }

  def receive = {
    case SendMessage(m) =>
      //println("Sending heartbeat")
      //if (!listenOnly) // Don't talk to the vehicle if we are supposed to stay off the air
      sendMavlink(m)
  }

  /**
   * m must be a SendYoungest or a MAVLinkMessage
   */
  override protected def handlePacket(m: Any) {
    //log.debug(s"Sending to server: $m")
    m match {
      case m: MAVLinkMessage =>
        seqNum += 1
        m.sequence = (seqNum & 0xff)
        webapi.filterMavlink(interfaceNum, m.encode)
        webapi.flush()
    }
  }
}

