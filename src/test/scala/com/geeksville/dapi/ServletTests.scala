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
import com.geeksville.dapi.model.DroneModelFormats
import com.geeksville.dapi.model.VehicleJson
import java.util.UUID

/**
 * These tests can be disabled by adding an argument to the constructor.
 * broken with atmosphere...
 */
class ServletTests /* (disabled: Boolean) */ extends FunSuite with ScalatraSuite with Logging with GivenWhenThen {
  implicit val swagger = new ApiSwagger

  lazy val activeRecordTables = new ScalatraConfig().schema

  // Sets up automatic case class to JSON output serialization
  protected implicit def jsonFormats: Formats = DefaultFormats ++ DroneModelFormats ++ GeeksvilleFormats

  // The random ID we will use for this test session
  val random = new Random(System.currentTimeMillis)
  val uniqueSuffix = random.alphanumeric.take(6).mkString
  val login = "test-" + uniqueSuffix
  val password = random.alphanumeric.take(8).mkString

  val apiKey = "eb34bd67.megadroneshare"

  // Send this in all cases
  val commonHeaders = Map(
    "Authorization" -> s"""DroneApi apikey="$apiKey"""")

  val jsonHeaders = commonHeaders ++ Map(
    "Accept" -> "application/json",
    "Content-Type" -> "application/json")

  val loginInfo = Map("login" -> login, "password" -> password)

  // Instead of using before we use beforeAll so that we don't tear down the DB for each test (speeds run at risk of side effect - FIXME)
  override def beforeAll() {
    println("**************************** STARTING TESTS ************************************")
    System.setProperty("run.mode", "test") // So we use the correct testing DB
    Global.setConfig()

    super.beforeAll()

    activeRecordTables.initialize

    addServlet(new SessionsController, "/api/v1/session/*")
    addServlet(new UserController, "/api/v1/user/*")
    addServlet(new VehicleController, "/api/v1/vehicle/*")
    addServlet(new SharedMissionController, "/api/v1/mission/*")
    addServlet(new SessionsController, "/api/v1/auth/*")
  }

  override def afterAll() {
    super.afterAll()
  }

  def jsonGet(uri: String) = {
    get(uri, /* params = loginInfo, */ headers = jsonHeaders) {
      checkStatusOk()
      parse(body)
    }
  }

  def checkStatusOk() {
    if (status != 200) { // If not okay then show the error msg from server
      error(response.statusLine.message)
    }
    status should equal(200)
  }

  def toJSON(x: AnyRef) = {
    val r = Serialization.write(x)
    debug(s"Sending $r")
    r.getBytes
  }

  /// Do a session logged in as our test user
  def userSession[A](f: => A): A = session {
    post("/api/v1/session/login", loginInfo, jsonHeaders) {
      checkStatusOk()
    }

    f
  }

  // We want cookies for this test
  session {
    val email = s"kevin+$uniqueSuffix@3drobotics.com"
    val u = UserJson(login, Some(password), Some(email), Some("Unit Test User"))

    test("User create") {
      post(s"/api/v1/auth/create", toJSON(u), headers = jsonHeaders) {
        checkStatusOk()
        response.headers.foreach {
          case (k, v) =>
            println(s"Create response: $k => ${v.mkString(",")}")
        }
      }
    }

    test("User !recreate") {
      post(s"/api/v1/auth/create", toJSON(u), headers = jsonHeaders) {
        status should equal(409)
      }
    }

    test("User read self") {
      jsonGet(s"/api/v1/user/$login")
    }

    ignore("User !read !self") {
      get(s"/api/v1/user/root", headers = jsonHeaders) {
        status should equal(401)
      }
    }

    test("User !read user list") {
      get(s"/api/v1/user", headers = jsonHeaders) {
        status should equal(401)
      }
    }
  }

  ignore("vehicle") {
    userSession {
      Given("Create a new vehicle")
      val v = VehicleJson(UUID.randomUUID, "unit-test vehicle")
      put("/api/v1/vehicle", toJSON(v), headers = jsonHeaders) {
        checkStatusOk()
      }
      And("Read vehicle")
      jsonGet("/api/v1/vehicle/1") // .extract[Vehicle]
    }
  }

  ignore("security-tlog-upload (not logged in)") {
    post("/api/v1/vehicle/1/missions", headers = jsonHeaders) {
      status should equal(401)
    }
  }

  ignore("sessions work") {
    // We want cookies for this test
    session {
      Given("We start by logging out")
      post("/api/v1/session/logout", loginInfo, headers = jsonHeaders) {
        checkStatusOk()
      }

      Then("We are logged out")
      // First try being not logged in
      get("/api/v1/session/user", headers = jsonHeaders) {
        status should equal(401)
      }

      Then("We can log in")
      post("/api/v1/session/login", loginInfo, headers = jsonHeaders) {
        checkStatusOk()
      }

      And("login cookie works")
      get("/api/v1/session/user", headers = jsonHeaders) {
        checkStatusOk()
      }
    }
  }

  ignore("tlog-upload") {
    userSession {
      // Set the payload
      val name = "test.tlog"
      val is = getClass.getResourceAsStream(name)
      val bytes = Stream.continually(is.read).takeWhile(-1 !=).map(_.toByte).toArray
      is.close()
      val payload = BytesPart(name, bytes, Mission.mimeType)

      post("/api/v1/vehicle/1/missions", Iterable.empty, Map("payload" -> payload), headers = commonHeaders) {
        checkStatusOk()
        info("View URL is " + body)
      }
    }
  }

  test("mission") {
    jsonGet("/api/v1/mission")
  }

}