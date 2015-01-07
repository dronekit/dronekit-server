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
import com.geeksville.util.URLUtil

/**
 * These tests can be disabled by adding an argument to the constructor.
 */
class OAuthTests extends ServerDependentSuite {

  val u = UserJson(login, Some(password), Some(email), Some("Unit Test User"))

  val redirectUri = "FIXME"
  val clientId = "FIXMEclient"

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

  ignore("Oauth create token: password auth") {
    println("creating oauth token")
    val headers = Map("Authorization" -> "Basic Y2xpZW50X2lkX3ZhbHVlOmNsaWVudF9zZWNyZXRfdmFsdWU=")
    val req = Seq(("grant_type" -> "password"), ("username" -> u.login), ("password" -> u.password.get), ("scope" -> "all"))

    val result = jsonParamPost(s"/api/v1/oauth/access_token", req, headers)
    println(s"OAuth token creation result: $result")
  }

  test("Oauth create token: access token auth") {
    userSession {
      Then("request auth HTML page")
      // val headers = Map("Authorization" -> "Basic Y2xpZW50X2lkX3ZhbHVlOmNsaWVudF9zZWNyZXRfdmFsdWU=")

      // Step 1: FIXME - send request token, to receive auth code
      val req1 = Seq("redirect_uri" -> redirectUri, "client_id" -> clientId, "response_type" -> "code", "scope" -> "fish cat dog",
        "state" -> "I like monkies")
      val authHtml = bodyGet("/api/v1/oauth/auth", req1)
      println(s"User visible HTML: $authHtml")

      Then("Send user auth click")
      val authResp = post("/api/v1/oauth/auth", Seq("approved" -> "OK"), commonHeaders) {
        URLUtil.parseQueryString(parseRedirect())
      }

      val code = authResp("code") //  (authResp \ "code").toString

      Then("exchange code for token")
      // Step 2: exchange auth code returned from server for an access token
      val req2 = Seq("grant_type" -> "authorization_code", "code" -> code, "redirect_uri" -> redirectUri, "client_id" -> clientId)
      val result = jsonParamPost(s"/api/v1/oauth/access_token", req2, headers)
      println(s"OAuth token creation result: $result")
    }
  }
}
