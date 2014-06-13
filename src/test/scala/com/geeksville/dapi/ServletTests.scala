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

/**
 * These tests can be disabled by adding an argument to the constructor.
 */
class ServletTests /* (disabled: Boolean) */ extends ServerDependentSuite {

  // We want cookies for this test
  session {
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

    test("User read !self") {
      jsonGet(s"/api/v1/user/root")
    }

    test("User !read user list") {
      get(s"/api/v1/user", headers = jsonHeaders) {
        status should equal(401)
      }
    }
  }

  //userSession {
  ignore("Vehicle create") {
    val v = VehicleJson(Some(UUID.randomUUID), "unit-test vehicle")
    put("/api/v1/vehicle", toJSON(v), headers = jsonHeaders) {
      checkStatusOk()
    }
  }
  ignore("Vehicle read") {
    jsonGet("/api/v1/vehicle/1") // .extract[Vehicle]
  }
  //}

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

  ignore("tlog-upload-to-vehicle") {
    userSession {
      // Set the payload
      val payload = tlogPayload

      post("/api/v1/vehicle/1/missions", Iterable.empty, Map("payload" -> payload), headers = commonHeaders) {
        checkStatusOk()
        info("View URL is " + body)
      }
    }
  }

  test("tlog-upload-easy without user create") {
    val params = loginInfo
    testEasyUpload(params, tlogPayload)
  }

  test("tlog-upload-easy with user create") {
    val login = "test-uploader-" + uniqueSuffix
    val password = random.alphanumeric.take(8).mkString
    val email = s"kevin+$login@3drobotics.com"

    val params = Map("login" -> login) + ("password" -> password) + ("autoCreate" -> "true") + ("email" -> email) + ("fullName" -> fullName)
    testEasyUpload(params, tlogPayload)
  }

  test("mission") {
    jsonGet("/api/v1/mission")
  }

}