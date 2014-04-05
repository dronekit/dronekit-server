package com.geeksville.dapi

import org.scalatra.test.scalatest._
import org.scalatest.FunSuite
import com.github.aselab.activerecord.scalatra.ScalatraConfig
import org.scalatest.BeforeAndAfter
import com.geeksville.dapi.model.Vehicle
import org.json4s.Formats
import org.json4s.DefaultFormats
import com.geeksville.json.GeeksvilleFormats
import org.json4s._
import org.json4s.native.JsonMethods._
import java.io.File
import org.scalatra.test.BytesPart
import com.geeksville.dapi.model.Mission
import grizzled.slf4j.Logging
import org.scalatra.test.Client

class ServletTests extends FunSuite with ScalatraSuite with Logging {
  implicit val swagger = new ApiSwagger

  lazy val activeRecordTables = new ScalatraConfig().schema

  // Sets up automatic case class to JSON output serialization
  protected implicit def jsonFormats: Formats = DefaultFormats ++ GeeksvilleFormats

  // Instead of using before we use beforeAll so that we don't tear down the DB for each test (speeds run at risk of side effect - FIXME)
  override def beforeAll() {
    Global.setConfig()

    super.beforeAll()

    activeRecordTables.initialize
  }

  override def afterAll() {
    activeRecordTables.cleanup
    super.afterAll()
  }

  // `HelloWorldServlet` is your app which extends ScalatraServlet
  addServlet(new UserController, "/api/v1/user/*")
  addServlet(new VehicleController, "/api/v1/vehicle/*")
  addServlet(new MissionController, "/api/v1/mission/*")

  def jsonGet(uri: String) = {
    get(uri) {
      checkStatusOk()
      parse(body)
    }
  }

  def checkStatusOk() {
    if (status != 200) // If not okay then show the error msg from server
      error(body)
    status should equal(200)
  }

  test("vehicle") {
    jsonGet("/api/v1/vehicle/1") // .extract[Vehicle]
  }

  test("mission") {
    jsonGet("/api/v1/mission/1")
  }

  test("user") {
    jsonGet("/api/v1/user")
  }

  test("security-tlog-upload (not logged in)") {
    post("/api/v1/vehicle/1/missions") {
      status should equal(401)
    }
  }

  test("tlog-upload") {
    // Set the payload
    val name = "test.tlog"
    val is = getClass.getResourceAsStream(name)
    val bytes = Stream.continually(is.read).takeWhile(-1 !=).map(_.toByte).toArray
    is.close()
    val payload = BytesPart(name, bytes, Mission.mimeType)

    post("/api/v1/vehicle/1/missions", Map("login" -> "test-bob", "password" -> "sekrit"), Map("payload" -> payload)) {
      checkStatusOk()
      info("View URL is " + body)
    }
  }
}