package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger
import com.geeksville.util.URLUtil
import com.geeksville.dapi.model.User

/**
 * A base class for REST endpoints that contain various fields
 *
 * Subclasses can call roField etc... to specify handlers for particular operations
 */
class ApiController[T <: Product: Manifest](val aName: String, val swagger: Swagger) extends ScalatraServlet with NativeJsonSupport with SwaggerSupport {

  // Sets up automatic case class to JSON output serialization
  protected implicit val jsonFormats: Formats = DefaultFormats

  override protected val applicationName = Some(aName)
  protected lazy val applicationDescription = s"The $aName API. It exposes operations for browsing and searching lists of $aName, and retrieving single $aName."

  /// Utility glue to make easy documentation boilerplate
  def aNames = aName + "s"
  def aCamel = URLUtil.capitalize(aName)
  def aCamels = aCamel + "s"

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }

  /// Generate a ro attribute on this rest endpoint of the form /:id/name.
  /// call getter as needed
  /// FIXME - move this great utility somewhere else
  def roField[T: Manifest](name: String, getter: => T) {
    val getInfo =
      (apiOperation[T]("get" + URLUtil.capitalize(name))
        summary s"Get the $name for the specified $aName"
        parameters (
          pathParam[String]("id").description(s"Id of $aName to be read")))

    get("/:id/" + name, operation(getInfo)) {
      halt(404) // FIXME
    }
  }

  /// Generate a wo attribute on this rest endpoint of the form /:id/name.
  /// call getter and setter as needed
  /// FIXME - move this great utility somewhere else
  def woField[T: Manifest](name: String, setter: T => Unit) {
    val putInfo =
      (apiOperation[String]("set" + URLUtil.capitalize(name))
        summary s"Set the $name on specified $aName"
        parameters (
          pathParam[String]("id").description(s"Id of $aName to be changed"),
          bodyParam[T](name).description(s"New value for the $name")))

    put("/:id/" + name, operation(putInfo)) {
      halt(404) // FIXME
    }
  }

  /// Generate an append only attribute on this rest endpoint of the form /:id/name.
  def aoField[T: Manifest](name: String, appender: T => Unit) {
    val putInfo =
      (apiOperation[String]("add" + URLUtil.capitalize(name))
        summary s"Set the $name on specified $aName"
        parameters (
          pathParam[String]("id").description(s"Id of $aName to be appended"),
          bodyParam[T](name).description(s"New value for the $name")))

    post("/:id/" + name, operation(putInfo)) {
      halt(404) // FIXME
    }
  }

  /// Generate a rw attribute on this rest endpoint of the form /:id/name.
  /// call getter and setter as needed
  def rwField[T: Manifest](name: String, getter: => T, setter: T => Unit) {
    roField(name, getter)
    woField(name, setter)
  }

  def raField[T: Manifest](name: String, getter: => List[T], appender: T => Unit) {
    roField[List[T]](name, getter)
    aoField(name, appender)
  }

  protected def getOp =
    (apiOperation[List[T]]("get")
      summary s"Show all $aNames"
      notes s"Shows all the $aNames. You can search it too."
      parameter queryParam[Option[String]]("name").description("A name to search for"))

  /*
   * Retrieve a list of instances
   */
  get("/", operation(getOp)) {
    User.getAll
  }

  private val findByIdOp =
    (apiOperation[User]("findById")
      summary "Find by id"
      parameters (
        pathParam[String]("id").description(s"Id of $aName that needs to be fetched")))

  /**
   * Find an object
   */
  get("/:id", operation(findByIdOp)) {
    findById(params("id")).getOrElse(halt(404))
  }

  def findById(id: String) = User.find(id)

  private val createByIdOp =
    (apiOperation[String]("createById")
      summary "Create by id"
      parameters (
        pathParam[String]("id").description(s"Id of $aName that needs to be created")))

  post("/:id", operation(createByIdOp)) {
    halt(404)
  }

  private val deleteByIdOp =
    (apiOperation[String]("deleteById")
      summary "Delete by id"
      parameters (
        pathParam[String]("id").description(s"Id of $aName that needs to be deleted")))

  delete("/:id", operation(deleteByIdOp)) {
    halt(404)
  }
}

