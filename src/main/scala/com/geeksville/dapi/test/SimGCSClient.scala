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

/**
 * An integration test that calls into the server as if it was a GCS/vehicle client
 */
class SimGCSClient(host: String, keep: Boolean) extends DebuggableActor with ActorLogging {
  import context._

  private val webapi = new GCSHooksImpl(host)
  private val random = new Random

  override def receive = {
    case Terminated(_) =>
      if (children.isEmpty) // All our vehicles done?
        self ! PoisonPill

    case SimGCSClient.RunTest(numVehicles, numSeconds) =>
      log.error("Running test")
      runTest(numVehicles, numSeconds)
      sender ! "Started background sim"
  }

  override def postStop() {
    webapi.stopMission(keep)
    webapi.close()

    log.info("Sim test completed")

    super.postStop()
  }

  private def getMachineId = {
    val iface = NetworkInterface.getNetworkInterfaces.nextElement
    val addr = iface.getHardwareAddress
    log.warning(s"Using $iface: ${addr.mkString(":")}")
    addr
  }

  private class SimVehicle(val systemId: Int, numSeconds: Int, val numPoints: Int) extends DebuggableActor with ActorLogging with VehicleSimulator with HeartbeatSender {
    private var seqNum = 0

    case object SimNext
    val interfaceNum = 0
    val isControllable = false

    val generation = SimGCSClient.nextGeneration
    val center = (21.2966980, -157.8480360)
    val lineAngle = generation * random.nextDouble % (math.Pi * 2)
    val maxLen = 0.5 // in degrees
    val maxAlt = 100

    // 20-40sec per each path down the line
    val secondsPerLoop = 20.0 + random.nextDouble / 20
    val numLoops = numSeconds / secondsPerLoop

    var numRemaining = numPoints

    var heading = random.nextInt(360)

    val uuid = UUID.nameUUIDFromBytes(getMachineId :+ systemId.toByte :+ generation.toByte)
    log.info(s"Created sim vehicle $systemId: $uuid")
    webapi.setVehicleId(uuid.toString, interfaceNum, systemId, isControllable)

    val interval = numSeconds.toDouble / numPoints
    private def scheduleNext() = context.system.scheduler.scheduleOnce(interval seconds, self, SimNext)

    // We are not a GCS - pretend to be a quad
    vehicleTypeCode = MAV_TYPE.MAV_TYPE_QUADROTOR
    autopilotCode = MAV_AUTOPILOT.MAV_AUTOPILOT_ARDUPILOTMEGA

    sendMavlink(makeStatusText("Starting sim vehicle"))

    // Start our sim
    scheduleNext()

    /// A fake current position
    def curLoc = {
      val pos = (numPoints.toDouble - numRemaining.toDouble) / numPoints
      val len = (maxLen / numLoops) * pos

      Location(center._1 + len * math.cos(lineAngle),
        center._2 + len * math.sin(lineAngle),
        Some(maxAlt * math.sin(pos)))
    }

    def receive = {
      case SendMessage(m) =>
        //println("Sending heartbeat")
        //if (!listenOnly) // Don't talk to the vehicle if we are supposed to stay off the air
        sendMavlink(m)

      case SimNext =>
        if (numRemaining == 0)
          self ! PoisonPill
        else {
          import com.geeksville.util.MathTools._

          sendMavlink(makeVFRHud(random.nextFloat % 10, random.nextFloat % 10, random.nextInt(100), heading))
          sendMavlink(makeAttitude(toRad(heading).toFloat, toRad(heading).toFloat, toRad(heading).toFloat))
          sendMavlink(makePosition(curLoc))
          if (random.nextInt(100) < 2)
            sendMavlink(makeStatusText("Random status text msg!"))

          // Fake up some mode changes
          if (random.nextInt(100) < 5) {
            log.debug("Faking a mode change")
            heading = random.nextInt(360)
            gcsCustomMode = random.nextInt(5) + 1
            gcsBaseMode = (if (random.nextBoolean()) MAV_MODE_FLAG.MAV_MODE_FLAG_SAFETY_ARMED else 0) | MAV_MODE_FLAG.MAV_MODE_FLAG_AUTO_ENABLED
          }

          numRemaining -= 1
          scheduleNext()
        }
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
      }
    }
  }

  private def runTest(numVehicles: Int, numSeconds: Int) {

    val loginName = "test-bob"
    val email = "test-bob@3drobotics.com"
    val password = "sekrit"

    // Create user if necessary/possible
    if (webapi.isUsernameAvailable(loginName))
      webapi.createUser(loginName, password, Some(email))
    else
      webapi.loginUser(loginName, password)

    webapi.flush()

    // How long to run the test
    val numPoints = numSeconds * 2

    log.info("Starting mission")
    webapi.startMission(keep, UUID.randomUUID)

    (0 until numVehicles).foreach { i =>
      watch(context.actorOf(Props(new SimVehicle(i, numSeconds, numPoints))))
    }

    // Handle the no vehicle case
    if (numVehicles == 0)
      self ! PoisonPill
  }

}

object PlaybackGCSClient {
  case class RunTest(name: String)
}

class PlaybackGCSClient(host: String) extends DebuggableActor with ActorLogging {
  def receive = {
    case PlaybackGCSClient.RunTest(name) =>
      log.error("Running test")
      fullTest(name)
  }

  /**
   * Creates an fake vehicle which actually calls up and sends real TLOG data/accepts commands
   *
   * FIXME: Add support for accepting commands
   * FIXME: Don't use the old MavlinkEventBus global
   */
  private def fullTest(testname: String) {

    import context._

    log.info("Starting full test vehicle")
    val tlog = context.actorOf(Props {
      val s = new BufferedInputStream(this.getClass.getResourceAsStream(testname + ".tlog"), 8192)
      val actor = TlogStreamReceiver.open(s, 10000, autoStart = false) // Play back the file at 10000x the normal speed
      actor
    }, "tlogsim")

    // Anything coming from the controller app, forward it to the serial port
    val groundControlId = 253 // FIXME
    MavlinkEventBus.subscribe(tlog, groundControlId)

    val uploader = LiveUploader.create(context, APIProxyActor.testAccount, host, isLive = false)
    context.watch(uploader)

    // Wait for the uploader to start (so it is subscribed) before starting the tlog reader
    // FIXME - make this into a waitStarted utility...
    implicit val timeout = Timeout(30 second)
    (uploader ? Identify(0L)).map { r =>
      // When our tlog reader finishes, we automatically end the upload
      context.watch(tlog)
      log.debug("Waiting for tlog file to end")
      context.become {
        case Terminated(t) if t == tlog =>
          log.info("Tlog finished, telling uploader to exit")
          uploader ! StopMissionAndExitMsg
          context.unbecome()

        case Terminated(t) if t == uploader =>
          log.info("Simulation completed!")
          context.sender ! s"Completed sim"
      }

      log.debug("Starting tlog playback")

      // We wait to start the tlog reader until _after_ our subscriptions are setup
      tlog ! MavlinkStreamReceiver.StartMsg
    }
  }
}

object SimGCSClient extends Logging {
  case class RunTest(numVehicles: Int, numSeconds: Int)

  /// We use this to ensure each new run of the simulator picks a different set of UUIDs - but we want to always start from
  /// the same set so the DB doesn't fill with a zilliong records
  private var generation = 0

  def nextGeneration() = {
    val r = generation
    generation += 1
    r
  }
}