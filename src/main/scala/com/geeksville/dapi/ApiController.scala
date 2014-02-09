package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger

class ApiController[T <: Product: Manifest](aName: String, val swagger: Swagger) extends ScalatraServlet with NativeJsonSupport with SwaggerSupport {

  // Sets up automatic case class to JSON output serialization
  protected implicit val jsonFormats: Formats = DefaultFormats

  override protected val applicationName = Some(aName)
  protected lazy val applicationDescription = s"The $aName API. It exposes operations for browsing and searching lists of $aName, and retrieving single $aName."

  /// Utility glue to make easy documentation boilerplate
  def aNames = aName + "s"
  def aCamel = aName.head.toUpper.toString + aName.tail
  def aCamels = aCamel + "s"

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }

  private val getOp =
    (apiOperation[List[T]]("get")
      summary s"Show all $aNames"
      notes s"Shows all the $aNames. You can search it too."
      parameter queryParam[Option[String]]("name").description("A name to search for"))

  /*
   * Retrieve a list of users
   */
  get("/", operation(getOp)) {
    params.get("name") match {
      case Some(name) => UserData.all filter (_.name.toLowerCase contains name.toLowerCase())
      case None => UserData.all
    }
  }

  private val findById =
    (apiOperation[User]("findById")
      summary "Find by id"
      parameters (
        pathParam[String]("id").description(s"Id of $aName that needs to be fetched")))

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
        pathParam[String]("id").description(s"Id of $aName that needs to be created")))

  post("/:id", operation(createById)) {
    halt(404)
  }

  private val deleteById =
    (apiOperation[String]("deleteById")
      summary "Delete by id"
      parameters (
        pathParam[String]("id").description(s"Id of $aName that needs to be deleted")))

  delete("/:id", operation(deleteById)) {
    halt(404)
  }
}

