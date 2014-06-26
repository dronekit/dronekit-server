package com.geeksville.flight

import org.scalatra.test.scalatest._
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.json4s.Formats
import org.json4s.DefaultFormats
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import java.io.File
import org.scalatra.test.BytesPart
import grizzled.slf4j.Logging
import org.scalatra.test.Client
import org.scalatest.GivenWhenThen
import scala.util.Random
import java.util.UUID

/**
 * These tests can be disabled by adding an argument to the constructor.
 */
class FlightTests /* (disabled: Boolean) */ extends FunSuite with Logging with GivenWhenThen {

  test("decode version") {
    assert(LiveOrPlaybackModel.decodeVersionMessage("ArduCopter V3.1.4 (abcde12)") == Some("ArduCopter", "V3.1.4", "abcde12"))
  }

  test("decode hw") {
    assert(LiveOrPlaybackModel.decodeHardwareMessage("PX4v2 00320033 35324719 36343032") == Some("PX4v2"))
  }
}