package com.geeksville.dapi

import org.scalatra.test.scalatest._
import org.scalatest.FunSuite

class ServletTests extends FunSuite with ScalatraSuite {
  // `HelloWorldServlet` is your app which extends ScalatraServlet
  addServlet(classOf[UserController], "/api/v1/user/*")
  addServlet(classOf[VehicleController], "/api/v1/vehicle/*")
  addServlet(classOf[MissionController], "/api/v1/mission/*")

  test("simple get") {
    get("/api/v1/vehicle/1") {
      status should equal(200)
      body should include("hi!")
    }
  }
}

//import org.scalatra.test.specs2._
//
//class HelloWorldMutableServletSpec extends MutableScalatraSpec {
//  addServlet(classOf[VehicleController], "/api/v1/vehicle/*")
//
//  "GET / on HelloWorldServlet" should {
//    "return status 200" in {
//      get("/api/v1/vehicle/1") {
//        status must_== 200
//      }
//    }
//  }
//}