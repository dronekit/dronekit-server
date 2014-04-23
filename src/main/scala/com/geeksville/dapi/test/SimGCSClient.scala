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

case class RunTest(host: String = APIConstants.DEFAULT_SERVER, name: String)

/**
 * An integration test that calls into the server as if it was a GCS/vehicle client
 */
class SimGCSClient extends Actor with ActorLogging {
  import context._

  def receive = {
    case RunTest(host, name) =>
      log.error("Running test")
      if (name != "quick") fullTest(name, host) else quickTest(host)
  }

  private def quickTest(host: String) {
    using(new GCSHooksImpl(host)) { webapi: GCSHooks =>

      val loginName = "test-bob"
      val email = "test-bob@3drobotics.com"
      val password = "sekrit"

      // Create user if necessary/possible
      if (webapi.isUsernameAvailable(loginName))
        webapi.createUser(loginName, password, Some(email))
      else
        webapi.loginUser(loginName, password)

      webapi.flush()

      val interfaceNum = 0;
      val sysId = 1;
      webapi.setVehicleId("550e8400-e29b-41d4-a716-446655440000",
        interfaceNum, sysId, false);

      log.info("Starting mission")
      webapi.startMission(true, UUID.randomUUID)
      // webapi.filterMavlink(interfaceNum, payload);

      webapi.stopMission(true)

      log.info("Test successful")
    }
  }

  /**
   * Creates an fake vehicle which actually calls up and sends real TLOG data/accepts commands
   *
   * FIXME: Add support for accepting commands
   * FIXME: Don't use the old MavlinkEventBus global
   */
  private def fullTest(testname: String, host: String) {
    log.info("Starting full test vehicle")
    val tlog = context.actorOf(Props {
      val s = new BufferedInputStream(getClass.getResourceAsStream(testname + ".tlog"), 8192)
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