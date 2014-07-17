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
import com.geeksville.apiproxy.APIConstants
import com.geeksville.util.ThreadTools
import com.geeksville.util.FileTools
import java.io.BufferedInputStream
import java.io.FileInputStream

class ServerDependentSuite /* (disabled: Boolean) */ extends FunSuite with ScalatraSuite with Logging with GivenWhenThen {
  implicit val swagger = new ApiSwagger

  lazy val activeRecordTables = new ScalatraConfig().schema

  // Sets up automatic case class to JSON output serialization
  protected implicit def jsonFormats: Formats = DefaultFormats ++ DroneModelFormats ++ GeeksvilleFormats

  // The random ID we will use for this test session
  val random = new Random(System.currentTimeMillis)
  def uniqueSuffix() = random.alphanumeric.take(6).mkString

  val login = "test-" + uniqueSuffix
  val password = random.alphanumeric.take(8).mkString
  val email = s"kevin+$login@3drobotics.com"
  val fullName = "TestUser LongName"

  val apiKey = "eb34bd67.megadroneshare"

  // Send this in all cases
  val commonHeaders = Map(
    "Authorization" -> s"""DroneApi apikey="$apiKey"""",
    "Referer" -> "http://www.droneshare.com/") // Pretend to come from droneshare server

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

    ThreadTools.catchIgnore {
      activeRecordTables.cleanup
    }
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

  def testEasyUpload(params: Map[String, String], payload: BytesPart) {
    // Set the payload
    val vehicleId = UUID.randomUUID.toString

    post(s"/api/v1/mission/upload/$vehicleId", params, Map("payload" -> payload), headers = commonHeaders) {
      // checkStatusOk()
      // status should equal(406) // The tlog we are sending should be considered uninteresting by the server
      info("View URL is " + body)
    }
  }
}

object ServerDependentSuite {
  /**
   * Get test tlog data that can be posted to the server
   */
  lazy val tlogPayload = readLog("test.tlog", APIConstants.tlogMimeType)
  lazy val logPayload = readLog("test.log", APIConstants.flogMimeType)
  lazy val blogPayload = readLog("test.bin", APIConstants.blogMimeType)

  // From resources
  def readLog(name: String, mime: String) = {
    val is = getClass.getResourceAsStream(name)
    val bytes = FileTools.toByteArray(is)
    is.close()
    BytesPart(name, bytes, mime)
  }

  // A blog file on the filesystem
  def filesystemBlog(path: String) = {
    val mime = APIConstants.blogMimeType
    val is = new BufferedInputStream(new FileInputStream(path))
    val bytes = FileTools.toByteArray(is)
    BytesPart("px4.bin", bytes, mime)
  }
}