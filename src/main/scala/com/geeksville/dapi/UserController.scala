package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger

class UserController(implicit swagger: Swagger) extends ApiController[User]("user", swagger) {
}

// A Flower object to use as a faked-out data model
case class User(id: String, name: String)

// An amazing datastore!
object UserData {

  /**
   * Some fake flowers data so we can simulate retrievals.
   */
  var all = List(
    User("uid4", "Bob Someone"),
    User("uid5", "Bob Someone2"),
    User("uid6", "Bob Someone3"))
}