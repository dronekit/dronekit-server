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
import org.json4s.JsonAST._
import org.json4s.JsonDSL._

/**
 * These tests can be disabled by adding an argument to the constructor.
 */
class OAuthTests extends ServerDependentSuite {

  // We want cookies for this test
  session {
    val u = UserJson(login, Some(password), Some(email), Some("Unit Test User"))

    // Create a user we can use to test oauth with
    test("User create") {
      info(s"Attempting create of $u")
      post(s"/api/v1/auth/create", toJSON(u), headers = jsonHeaders) {
        checkStatusOk()
        response.headers.foreach {
          case (k, v) =>
            println(s"Create response: $k => ${v.mkString(",")}")
        }
      }
    }

    test("Oauth create token") {
      println("creating oauth token")
      val headers = Map("Authorization" -> "Basic Y2xpZW50X2lkX3ZhbHVlOmNsaWVudF9zZWNyZXRfdmFsdWU=")
      val req = Seq(("grant_type" -> "password"), ("username" -> u.login), ("password" -> u.password.get), ("scope" -> "all"))

      val result = paramPost(s"/api/v1/oauth/access_token", req, headers)
      println(s"OAuth token creation result: $result")
    }

  }

}