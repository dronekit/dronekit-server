package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger
import com.geeksville.dapi.model.User
import org.json4s.JsonAST.JObject
import org.json4s._

class UserController(implicit swagger: Swagger) extends ActiveRecordController[User]("user", swagger, User) {
  override val blacklist = Set("hashedPassword", "password")

  override protected def createById(id: String, payload: JObject): Any = {
    warn(s"Creating $id")
    val found = User.find(id)
    if (found.isDefined)
      haltConflict()

    User.create(id, (payload \ "password").toString) // payload \ "email", payload.fullName)
  }
}

