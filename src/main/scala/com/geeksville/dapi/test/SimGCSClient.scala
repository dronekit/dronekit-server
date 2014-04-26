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

/**
 * An integration test that calls into the server as if it was a GCS/vehicle client
 */
class SimGCSClient(host: String, keep: Boolean) extends Actor with ActorLogging {
  import context._

  private val webapi = new GCSHooksImpl(host)
  private val random = new Random

  def receive = {
    case Terminated(_) =>
      if (children.isEmpty) // All our vehicles done?
        self ! PoisonPill

    case SimGCSClient.RunTest(numVehicles, numSeconds) =>
      log.error("Running test")
      runTest(numVehicles, numSeconds)
  }

  override def postStop() {
    webapi.stopMission(keep)
    webapi.close()

    log.info("Sim test completed")

    super.postStop()
  }

  private class SimVehicle(val systemId: Int, numSeconds: Int, val numPoints: Int) extends Actor with ActorLogging with VehicleSimulator {
    case object SimNext
    val interfaceNum = 0
    val isControllable = false

    val center = (21.2966980, -157.8480360)
    val lineAngle = random.nextDouble % (math.Pi * 2)
    val maxLen = 0.5 // in degrees
    val maxAlt = 100

    var numRemaining = numPoints

    val uuid = UUID.nameUUIDFromBytes(Array(systemId.toByte))
    log.info("Created sim vehicle $systemID: $uuid")
    webapi.setVehicleId(uuid.toString, interfaceNum, systemId, isControllable)

    val interval = numSeconds.toDouble / numPoints
    private def scheduleNext() = context.system.scheduler.scheduleOnce(interval seconds, self, SimNext)

    sendMavlink(makeStatusText("Starting sim vehicle"))

    // Start our sim
    scheduleNext()

    /// A fake current position
    def curLoc = {
      val pos = numRemaining.toDouble / numPoints
      val len = maxLen * pos

      Location(center._1 + len * math.cos(lineAngle),
        center._2 + len * math.sin(lineAngle),
        Some(maxAlt * math.sin(pos)))
    }

    def receive = {
      case SimNext =>
        if (numRemaining == 0)
          self ! PoisonPill
        else {
          sendMavlink(makeVFRHud(random.nextFloat % 10, random.nextFloat % 10, random.nextInt(100)))
          sendMavlink(makePosition(curLoc))
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

class PlaybackGCSClient(host: String) extends Actor with ActorLogging {
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

    val uploader = LiveUploader.create(context, APIProxyActor.testAccount, isLive = false)

    // Wait for the uploader to start (so it is subscribed) before starting the tlog reader
    // FIXME - make this into a waitStarted utility...
    implicit val timeout = Timeout(30 second)
    (uploader ? Identify(0L)).map { r =>
      // When our tlog reader finishes, we automatically end the upload
      context.watch(tlog)
      log.debug("Waiting for tlog file to end")
      context.become {
        case Terminated(t) =>
          log.info("Tlog finished, telling uploader to exit")
          uploader ! StopMissionAndExitMsg
          context.unbecome()
      }

      log.debug("Starting tlog playback")

      // We wait to start the tlog reader until _after_ our subscriptions are setup
      tlog ! MavlinkStreamReceiver.StartMsg
    }
  }
}

object SimGCSClient extends Logging {
  case class RunTest(numVehicles: Int, numSeconds: Int)

}