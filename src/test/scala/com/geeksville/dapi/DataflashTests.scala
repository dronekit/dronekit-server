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
import java.io.BufferedInputStream
import java.io.FileInputStream
import com.geeksville.util.FileTools

/**
 * These tests can be disabled by adding an argument to the constructor.
 */
class DataflashTests /* (disabled: Boolean) */ extends ServerDependentSuite {

  test("flog-upload-easy with user create") {
    val login = "test-uploader-" + uniqueSuffix
    val password = random.alphanumeric.take(8).mkString
    val email = s"kevin+$login@3drobotics.com"

    val params = Map("login" -> login) + ("password" -> password) + ("autoCreate" -> "true") + ("email" -> email) + ("fullName" -> fullName)
    testEasyUpload(params, ServerDependentSuite.logPayload)
  }

  test("blog-upload-easy with user create") {
    val login = "test-uploader-" + uniqueSuffix
    val password = random.alphanumeric.take(8).mkString
    val email = s"kevin+$login@3drobotics.com"

    val params = Map("login" -> login) + ("password" -> password) + ("autoCreate" -> "true") + ("email" -> email) + ("fullName" -> fullName)
    testEasyUpload(params, ServerDependentSuite.blogPayload)
  }

  test("px4 native file upload") {
    val login = "test-uploader-" + uniqueSuffix
    val password = random.alphanumeric.take(8).mkString
    val email = s"kevin+$login@3drobotics.com"

    val params = Map("login" -> login) + ("password" -> password) + ("autoCreate" -> "true") + ("email" -> email) + ("fullName" -> fullName)
    testEasyUpload(params, px4Payload)
  }

  // A test Px4 native file
  def px4Payload = {
    val mime = APIConstants.blogMimeType
    val is = new BufferedInputStream(new FileInputStream("/home/kevinh/tmp/px4.bin"))
    val bytes = FileTools.toByteArray(is)
    BytesPart("px4.bin", bytes, mime)
  }

  test("mission get one") {
    println(jsonGet("/api/v1/mission/1"))
  }
}