package com.geeksville.mapbox

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
import com.geeksville.util.Using._

class MapboxTests extends FunSuite with Logging with GivenWhenThen {

  test("geocode") {
    using(new MapboxClient()) { client =>
      val r = client.geocode(21.2917566, -157.84892689999998)
      r.foreach { m => info("Mapbox says: " + m)}
    }
  }

  test("static map") {
    val r = MapboxClient.staticMapURL(21.2917566, -157.84892689999998, 5, 300, 200, "star")
    info("Mapbox says: " + r)
  }
}
