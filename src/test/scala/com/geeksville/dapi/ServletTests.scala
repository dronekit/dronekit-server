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
 * These tests can be disabled by adding an argument to the constructor.
 * broken with atmosphere...
 */
class ServletTests /* (disabled: Boolean) */ extends FunSuite with ScalatraSuite with Logging with GivenWhenThen {
  implicit val swagger = new ApiSwagger

  lazy val activeRecordTables = new ScalatraConfig().schema

  // Sets up automatic case class to JSON output serialization
  protected implicit def jsonFormats: Formats = DefaultFormats ++ GeeksvilleFormats

  // The random ID we will use for this test session
  val uniqueSuffix = Random.alphanumeric.take(6).mkString
  val login = "test-" + uniqueSuffix
  val password = Random.alphanumeric.take(8).mkString

  val apiKey = "eb34bd67.megadroneshare"
  val jsonHeaders = Map(
    "Accept" -> "application/json",
    "Content-Type" -> "application/json",
    "Authorization" -> s"""DroneApi apikey="$apiKey"""")

  val loginInfo = Map("login" -> login, "password" -> password)

  // Instead of using before we use beforeAll so that we don't tear down the DB for each test (speeds run at risk of side effect - FIXME)
  override def beforeAll() {
    System.setProperty("run.mode", "test") // So we use the correct testing DB
    Global.setConfig()

    super.beforeAll()

    activeRecordTables.initialize
  }

  override def afterAll() {
    super.afterAll()
  }

  addServlet(new SessionsController, "/api/v1/session/*")
  addServlet(new UserController, "/api/v1/user/*")
  addServlet(new VehicleController, "/api/v1/vehicle/*")
  addServlet(new MissionController, "/api/v1/mission/*")
  addServlet(new SessionsController, "/api/v1/auth/*")

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

  ignore("vehicle") {
    jsonGet("/api/v1/vehicle/1") // .extract[Vehicle]
  }

  ignore("mission") {
    jsonGet("/api/v1/mission/1")
  }

  def toJSON(x: AnyRef) = {
    val r = Serialization.write(x)
    debug(s"Sending $r")
    r.getBytes
  }

  test("user") {
    // We want cookies for this test
    session {
      Given("First make a new user")

      val email = s"kevin+$uniqueSuffix@3drobotics.com"
      val u = UserJson(login, Some(password), Some(email), Some("Unit Test User"))

      post(s"/api/v1/auth/create", toJSON(u), headers = jsonHeaders) {
        checkStatusOk()
      }

      Then("Make sure we can't recreate using the same ID")
      post(s"/api/v1/auth/create", toJSON(u), headers = jsonHeaders) {
        status should equal(409)
      }

      And("List that user")
      jsonGet(s"/api/v1/user/$login")

      And("Can't see other users")
      // Make sure a regular user can't read other users
      get(s"/api/v1/user/root") {
        status should equal(401)
      }

      // They also shouldn't be allowed to list all users
      get(s"/api/v1/user") {
        status should equal(401)
      }
    }
  }

  ignore("security-tlog-upload (not logged in)") {
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