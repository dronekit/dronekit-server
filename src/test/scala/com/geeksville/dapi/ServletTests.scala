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
import org.json4s.native.Serialization
import java.io.File
import org.scalatra.test.BytesPart
import com.geeksville.dapi.model.Mission
import grizzled.slf4j.Logging
import org.scalatra.test.Client
import com.geeksville.dapi.auth.SessionsController
import org.scalatest.GivenWhenThen
import scala.util.Random
import com.geeksville.dapi.model.UserJson

/**
 * These tests are temporarily disabled - by adding an argument to the constructor.
 * broken with atmosphere...
 */
class ServletTests(disabled: Boolean) extends FunSuite with ScalatraSuite with Logging with GivenWhenThen {
  implicit val swagger = new ApiSwagger

  lazy val activeRecordTables = new ScalatraConfig().schema

  // Sets up automatic case class to JSON output serialization
  protected implicit def jsonFormats: Formats = DefaultFormats ++ GeeksvilleFormats

  val jsonHeaders = Map("Accept" -> "application/json", "Content-Type" -> "application/json")

  val loginInfo = Map("login" -> "test-bob", "password" -> "sekrit")

  // Instead of using before we use beforeAll so that we don't tear down the DB for each test (speeds run at risk of side effect - FIXME)
  override def beforeAll() {
    System.setProperty("run.mode", "test") // So we use the correct testing DB
    Global.setConfig()

    super.beforeAll()

    activeRecordTables.initialize
  }

  override def afterAll() {
    activeRecordTables.cleanup
    super.afterAll()
  }

  addServlet(new SessionsController, "/api/v1/session/*")
  addServlet(new UserController, "/api/v1/user/*")
  addServlet(new VehicleController, "/api/v1/vehicle/*")
  addServlet(new MissionController, "/api/v1/mission/*")

  def jsonGet(uri: String) = {
    get(uri, headers = jsonHeaders) {
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

  def toJSON(x: AnyRef) = {
    val r = Serialization.write(x)
    debug(s"Sending $r")
    r.getBytes
  }

  test("user") {
    Given("First make a new user")
    val login = "test-" + Random.alphanumeric.take(6).mkString
    val u = UserJson("sekrit")
    put(s"/api/v1/user/$login", toJSON(u), headers = jsonHeaders) {
      checkStatusOk()
    }

    Then("FIXME - fails: Make sure we can't recreate using the same ID")
    //put(s"/api/v1/user/$login", toJSON(u), headers = jsonHeaders) {
    //  status should equal(409) // conflict
    //}

    And("List all users")
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

    post("/api/v1/vehicle/1/missions", loginInfo, Map("payload" -> payload)) {
      checkStatusOk()
      info("View URL is " + body)
    }
  }

  test("sessions work") {
    // We want cookies for this test
    session {
      Given("We start by logging out")
      post("/api/v1/session/logout", loginInfo) {
        checkStatusOk()
      }

      Then("We are logged out")
      // First try being not logged in
      get("/api/v1/session/user") {
        status should equal(401)
      }

      Then("We can log in")
      post("/api/v1/session/login", loginInfo) {
        checkStatusOk()
      }

      And("login cookie works")
      get("/api/v1/session/user") {
        checkStatusOk()
      }
    }
  }
}