package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger

class UserController(implicit val swagger: Swagger) extends ScalatraServlet with NativeJsonSupport with SwaggerSupport {

  // Sets up automatic case class to JSON output serialization
  protected implicit val jsonFormats: Formats = DefaultFormats

  override protected val applicationName = Some("user")
  protected val applicationDescription = "The user API. It exposes operations for browsing and searching lists of users, and retrieving single user."

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }

  private val getUsers =
    (apiOperation[List[User]]("getUsers")
      summary "Show all users"
      notes "Shows all the users. You can search it too."
      parameter queryParam[Option[String]]("name").description("A name to search for"))

  /*
   * Retrieve a list of users
   */
  get("/", operation(getUsers)) {
    params.get("name") match {
      case Some(name) => UserData.all filter (_.name.toLowerCase contains name.toLowerCase())
      case None => UserData.all
    }
  }

  private val findById =
    (apiOperation[User]("findById")
      summary "Find by id"
      parameters (
        pathParam[String]("id").description("Id of user that needs to be fetched")))

  /**
   * Find a flower using its slug.
   */
  get("/:id", operation(findById)) {
    UserData.all find (_.id == params("id")) match {
      case Some(b) => b
      case None => halt(404)
    }
  }

  private val createById =
    (apiOperation[String]("createById")
      summary "Create by id"
      parameters (
        pathParam[String]("id").description("Id of user that needs to be created")))

  post("/:id", operation(createById)) {
    halt(404)
  }

  private val deleteById =
    (apiOperation[String]("deleteById")
      summary "Delete by id"
      parameters (
        pathParam[String]("id").description("Id of user that needs to be deleted")))

  delete("/:id", operation(deleteById)) {
    halt(404)
  }
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