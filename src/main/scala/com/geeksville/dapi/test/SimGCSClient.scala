package com.geeksville.dapi.test

import akka.actor.Actor
import akka.actor.ActorLogging
import com.geeksville.apiproxy.GCSHooksImpl
import com.geeksville.util.Using._

case object RunTest

/**
 * An integration test that calls into the server as if it was a GCS/vehicle client
 */
class SimGCSClient extends Actor with ActorLogging {
  def receive = {
    case RunTest =>
      log.error("Running test")
      runTest()
  }

  private def runTest() {
    using(new GCSHooksImpl()) { webapi =>
      webapi.loginUser("test-bob@3drobotics.com", "sekrit");
      webapi.flush();

      val interfaceNum = 0;
      val sysId = 1;
      webapi.setVehicleId("550e8400-e29b-41d4-a716-446655440000",
        interfaceNum, sysId);

      // webapi.filterMavlink(interfaceNum, payload);

      log.info("Test successful")
    }
  }
}