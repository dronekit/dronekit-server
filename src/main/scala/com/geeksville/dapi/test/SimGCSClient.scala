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

/**
 * An integration test that calls into the server as if it was a GCS/vehicle client
 */
class SimGCSClient(host: String, keep: Boolean) extends DebuggableActor with ActorLogging {
  import context._

  private val random = new Random(System.currentTimeMillis)

  override def receive = {
    case Terminated(_) =>
      if (children.isEmpty) // All our vehicles done?
        self ! PoisonPill

    case SimGCSClient.StopAllTests =>
      children.foreach { _ ! PoisonPill }
      sender ! "Stopped all tests"

    case SimGCSClient.RunTest(numVehicles, numSeconds) =>
      log.error("Running test")
      runTest(numVehicles, numSeconds)
      sender ! "Started background sim"
  }

  override def postStop() {

    log.info("Sim test completed")

    super.postStop()
  }

  private def runTest(numVehicles: Int, numSeconds: Int) {
    // How long to run the test
    val numPoints = numSeconds * 2

    (0 until numVehicles).foreach { i =>
      watch(context.actorOf(Props(new SimVehicle(SimGCSClient.nextGeneration(), numSeconds, numPoints, host, keep))))
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
    AkkaTools.waitAlive(uploader) onComplete {
      case Failure(t) =>
        throw t

      case Success(_) =>
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
  case object StopAllTests

  val loginName = "test-bob"

  /// We use this to ensure each new run of the simulator picks a different set of UUIDs - but we want to always start from
  /// the same set so the DB doesn't fill with a zilliong records
  private var generation = 0

  def nextGeneration() = {
    val r = generation
    generation += 1
    r
  }
}