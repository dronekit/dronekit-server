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
import com.geeksville.dapi.model.UserSerializer
import com.geeksville.json.GeeksvilleFormats
import com.geeksville.dapi.model.Vehicle
import com.geeksville.dapi.model.VehicleSerializer
import java.util.UUID

class UserController(implicit swagger: Swagger) extends ActiveRecordController[User]("user", swagger, User) {
  override val blacklist = Set("hashedPassword", "password", "groupId")

  /**
   * Until a compelling use-case can be made we only allow admins to list all users
   */
  protected override def requireReadAllAccess() = {
    requireAdmin()

    super.requireReadAllAccess()
  }

  override protected def toJSON(o: Any): JValue = {
    Extraction.decompose(o)(DefaultFormats ++ GeeksvilleFormats + VehicleSerializer + new UserSerializer(Option(user)))
  }

  private val addVehicleInfo =
    (apiOperation[List[Vehicle]]("addVehicle")
      summary s"Add a new mission (as a tlog, bog or log)"
      parameters (
        bodyParam[Array[Byte]],
        pathParam[String]("id").description(s"Id of $aName to be appended")))

  post("/:id/vehicles", operation(addVehicleInfo)) {
    val u = requireWriteAccess(findById)
    info("Creating new vehicle for web client")

    // FIXME - use the payload provided by the client
    val uuid = UUID.randomUUID()
    val v = u.getOrCreateVehicle(uuid)
    v
  }
}

