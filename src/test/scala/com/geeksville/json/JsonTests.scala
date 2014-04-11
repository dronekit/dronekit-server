package com.geeksville.json

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
import com.geeksville.flight.Location

class JsonTests extends FunSuite with Logging with GivenWhenThen {

  test("geojson") {
    val p = Location(2.4, 3.5, Some(100))
    val parr = Seq(p, p)
    val parr2 = Seq(parr, parr)
    println(pretty(render(GeoJSON.makePoint(p))))
    println(pretty(render(GeoJSON.makeLineString(parr))))
    println(pretty(render(GeoJSON.makeMultiLineString(parr2))))
  }

}