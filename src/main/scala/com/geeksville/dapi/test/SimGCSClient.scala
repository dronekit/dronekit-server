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

case object RunTest

/**
 * An integration test that calls into the server as if it was a GCS/vehicle client
 */
class SimGCSClient extends Actor with ActorLogging {
  def receive = {
    case RunTest =>
      log.error("Running test")
      if (false) fullTest() else quickTest()
  }

  private def quickTest() {
    using(new GCSHooksImpl()) { webapi: GCSHooks =>

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
      webapi.startMission(true)
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
  private def fullTest() {
    log.info("Starting full test vehicle")
    val s = new BufferedInputStream(getClass.getResourceAsStream("test.tlog"), 8192)

    val tlog = context.actorOf(Props(TlogStreamReceiver.open(s)), "tlogsim")

    // Anything coming from the controller app, forward it to the serial port
    val groundControlId = 253 // FIXME
    MavlinkEventBus.subscribe(tlog, groundControlId)

    LiveUploader.create(context, APIProxyActor.testAccount, isLive = false)
  }
}