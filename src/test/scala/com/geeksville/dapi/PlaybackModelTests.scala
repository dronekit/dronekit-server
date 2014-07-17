package com.geeksville.dapi

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.json4s.Formats
import org.json4s.DefaultFormats
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import java.io.File
import grizzled.slf4j.Logging
import org.scalatest.GivenWhenThen
import scala.util.Random
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.io.FileInputStream

class PlaybackModelTests extends FunSuite with Logging with GivenWhenThen {

  ignore("blog to IGC") {
    val t = DataflashPlaybackModel.fromBytes(ServerDependentSuite.blogPayload.content, false)
    val out = new BufferedOutputStream(new FileOutputStream("/tmp/test.igc"))
    t.toIGC(out)
  }

  test("px4 read") {
    val path = "/home/kevinh/tmp/lorenz-fail.bin"
    val is = new BufferedInputStream(new FileInputStream(path))
    val bytes = ServerDependentSuite.filesystemBlog(path)
    val t = DataflashPlaybackModel.fromBytes(bytes.content, false)
    //val t = DataflashPlaybackModel.fromInputStream(is, false)
    println(t.summary)
  }
}