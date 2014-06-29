package com.geeksville.dapi

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
import org.scalatest.GivenWhenThen
import scala.util.Random
import java.io.BufferedOutputStream
import java.io.FileOutputStream

class PlaybackModelTests extends FunSuite with Logging with GivenWhenThen {

  test("blog to IGC") {
    val t = DataflashPlaybackModel.fromBytes(ServerDependentSuite.blogPayload.content, false)
    val out = new BufferedOutputStream(new FileOutputStream("/tmp/test.igc"))
    t.toIGC(out)
  }
}