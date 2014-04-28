package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger
import com.geeksville.dapi.model.User
import org.json4s.JsonAST.JObject
import org.json4s._
import com.geeksville.dapi.model.UserJson

class UserController(implicit swagger: Swagger) extends ActiveRecordController[User]("user", swagger, User) {
  override val blacklist = Set("hashedPassword", "password", "groupId")

  /**
   * Until a compelling use-case can be made we only allow admins to list all users
   */
  protected override def requireReadAllAccess() = {
    requireAdmin()

    super.requireReadAllAccess()
  }

  private lazy val createOp = apiOperation[User]("create") summary "Creates a new user record" parameter (bodyParam[UserJson])

  /// Subclasses can provide suitable behavior if they want to allow PUTs to /:id to result in creating new objects
  put("/:id", operation(createOp)) {

    val id = params("id")

    warn(s"Creating $id")
    val found = User.find(id)
    if (found.isDefined)
      haltConflict()

    val u = parsedBody.extract[UserJson]
    User.create(id, u.password.get, u.email, u.fullName)
  }
}

