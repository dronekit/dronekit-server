package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger
import com.geeksville.dapi.model.User
import org.json4s.JsonAST.JObject
import org.json4s._

case class UserJson(password: String, email: Option[String] = None, fullName: Option[String] = None)

class UserController(implicit swagger: Swagger) extends ActiveRecordController[User]("user", swagger, User) {
  override val blacklist = Set("hashedPassword", "password", "groupId")

  /// Subclasses can provide suitable behavior if they want to allow PUTs to /:id to result in creating new objects
  put("/:id") {

    val id = params("id")

    warn(s"Creating $id")
    val found = User.find(id)
    if (found.isDefined)
      haltConflict()

    val u = parsedBody.extract[UserJson]
    User.create(id, u.password, u.email, u.fullName)
  }
}

