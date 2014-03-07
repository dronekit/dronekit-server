package com.geeksville.dapi.test

import akka.actor.Actor
import akka.actor.ActorLogging
import com.geeksville.apiproxy.TestClient

case object RunTest

/**
 * An integration test that calls into the server as if it was a GCS/vehicle client
 */
class SimGCSClient extends Actor with ActorLogging {
  def receive = {
    case RunTest =>
      TestClient.runTest()
  }
}