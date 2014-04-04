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

class ServletTests extends FunSuite with ScalatraSuite with BeforeAndAfter {
  implicit val swagger = new ApiSwagger

  lazy val activeRecordTables = new ScalatraConfig().schema

  // Sets up automatic case class to JSON output serialization
  protected implicit def jsonFormats: Formats = DefaultFormats ++ GeeksvilleFormats

  before {
    activeRecordTables.initialize
  }

  after {
    activeRecordTables.cleanup
  }

  // `HelloWorldServlet` is your app which extends ScalatraServlet
  addServlet(new UserController, "/api/v1/user/*")
  addServlet(new VehicleController, "/api/v1/vehicle/*")
  addServlet(new MissionController, "/api/v1/mission/*")

  test("simple get") {
    get("/api/v1/vehicle/1") {
      status should equal(200)

      val parsedBody = parse(body)
      parsedBody.extract[Vehicle]
    }
  }

}