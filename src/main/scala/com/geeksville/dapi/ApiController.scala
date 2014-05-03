package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger
import com.geeksville.util.URLUtil
import com.geeksville.dapi.model.User
import com.geeksville.dapi.model.CRUDOperations
import com.geeksville.json.GeeksvilleFormats
import javax.servlet.http.HttpServletRequest
import org.json4s.JsonAST.JObject
import javax.servlet.http.HttpServletResponse
import java.util.Date
import org.json4s.Extraction

/**
 * A base class for REST endpoints that contain various fields
 *
 * Subclasses can call roField etc... to specify handlers for particular operations
 * T is the primary type
 */
class ApiController[T <: Product: Manifest](val aName: String, val swagger: Swagger, val companion: CRUDOperations[T]) extends DroneHubStack with CorsSupport with SwaggerSupport {

  // This override is necessary for the swagger docgen to make correct paths
  override protected val applicationName = Some("api/v1/" + aName)

  protected lazy val applicationDescription = s"The $aName API. It exposes operations for browsing and searching lists of $aName, and retrieving single $aName."

  private val expire = new Date().toString

  /// Utility glue to make easy documentation boilerplate
  def aNames = aName + "s"
  def aCamel = URLUtil.capitalize(aName)
  def aCamels = aCamel + "s"

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
    applyNoCache(response)
  }

  /**
   * Used to prevent caching
   */
  def applyNoCache(response: HttpServletResponse) {
    response.addHeader("Expires", expire)
    response.addHeader("Last-Modified", expire)
    response.addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
    response.addHeader("Pragma", "no-cache")
  }

  protected def requireReadAllAccess() = {
    requireServiceAuth(aName + "/read")
  }

  /**
   * Filter read access to a potentially protected record.  Subclasses can override if they want to restrict reads based on user or object
   * If not allowed, override should call haltUnauthorized()
   */
  protected def requireReadAccess(o: T) = {
    requireServiceAuth(aName + "/read")
    o
  }

  /// Subclasses can overide to limit access for creating new records
  protected def requireCreateAccess() = {
    requireServiceAuth(aName + "/create")
  }

  /**
   * Filter read access to a potentially protected record.  Subclasses can override if they want to restrict reads based on user or object
   * If not allowed, override should call haltUnauthorized()
   */
  protected def requireWriteAccess(o: T): T = {
    requireServiceAuth(aName + "/update")
    haltMethodNotAllowed("We don't allow writes to this")
  }

  protected def requireDeleteAccess(o: T): T = {
    requireWriteAccess(o) // Not quite correct but rare
  }

  /**
   * Check if the specified owned resource can be accessed by the current user given an AccessCode
   */
  protected def requireAccessCode(ownerId: Long, privacyCode: Int, defaultPrivacy: Int) {
    val u = tryLogin()
    val isOwner = u.map(_.id == ownerId).getOrElse(false)
    val isResearcher = u.map(_.isResearcher).getOrElse(false)

    if (!ApiController.isAccessAllowed(privacyCode, isOwner, isResearcher, defaultPrivacy))
      haltUnauthorized("No access")
  }

  /// Generate a ro attribute on this rest endpoint of the form /:id/name.
  /// call getter as needed
  /// FIXME - move this great utility somewhere else
  def roField[R](name: String)(getter: T => R) {
    val getInfo =
      (apiOperation[T]("get" + URLUtil.capitalize(name))
        summary s"Get the $name for the specified $aName"
        parameters (
          pathParam[String]("id").description(s"Id of $aName to be read")))

    get("/:id/" + name, operation(getInfo)) {
      getter(findById)
    }
  }

  /// Generate a wo attribute on this rest endpoint of the form /:id/name.
  /// call getter and setter as needed
  /// FIXME - move this great utility somewhere else
  def woField[R: Manifest](name: String, setter: (T, R) => Unit) {
    val putInfo =
      (apiOperation[String]("set" + URLUtil.capitalize(name))
        summary s"Set the $name on specified $aName"
        parameters (
          bodyParam[R],
          pathParam[String]("id").description(s"Id of $aName to be changed"),
          bodyParam[R](name).description(s"New value for the $name")))

    put("/:id/" + name, operation(putInfo)) {
      setter(findById, parsedBody.extract[R])
    }
  }

  put("/") {
    requireCreateAccess()

    val jobj = try {
      parsedBody.extract[JObject]
    } catch {
      case ex: Exception =>
        error(s"Malformed client json: $parsedBody")
        haltBadRequest("JSON object expected")
    }
    createDynamically(jobj)
  }

  /// Subclasses can provide suitable behavior if they want to allow PUTs to / to result in creating new objects.  implementations should return the new ID
  protected def createDynamically(payload: JObject): Any = {
    haltMethodNotAllowed("creation without IDs not allowed")
  }

  /// Subclasses can provide suitable behavior if they want to allow DELs to /:id to result in deleting objects
  protected def doDelete(o: T): Any = {
    haltMethodNotAllowed("deletion not allowed")
  }

  /// Generate an append only attribute on this rest endpoint of the form /:id/name.
  def aoField[R: Manifest](name: String)(appender: (T, R) => Unit) {
    val putInfo =
      (apiOperation[String]("add" + URLUtil.capitalize(name))
        summary s"Set the $name on specified $aName"
        parameters (
          pathParam[String]("id").description(s"Id of $aName to be appended"),
          bodyParam[R](name).description(s"New value for the $name")))

    post("/:id/" + name, operation(putInfo)) {
      appender(findById, parsedBody.extract[R])
    }
  }

  /// Generate a rw attribute on this rest endpoint of the form /:id/name.
  /// call getter and setter as needed
  def rwField[R: Manifest](name: String, getter: T => R, setter: (T, R) => Unit) {
    roField(name)(getter)
    woField(name, setter)
  }

  /// Read an appendable field
  def raField[R: Manifest](name: String, getter: T => List[R], appender: (T, R) => Unit) {
    roField(name)(getter)
    aoField(name)(appender)
  }

  protected def getOp =
    (apiOperation[List[T]]("get")
      summary s"Show all $aNames"
      notes s"Shows all the $aNames. You can search it too."
      parameters (
        queryParam[Option[Int]]("page_offset").description("If paging, the record # to start with (use 0 at start)"),
        queryParam[Option[Int]]("page_size").description("If paging, the # of records in the page"),
        queryParam[Option[String]]("order_by").description("To get sorted response, the field name to sort on"),
        queryParam[Option[String]]("order_dir").description("If sorting, the optional direction.  either asc or desc")))

  /*
   * Retrieve a list of instances
   */
  get("/", operation(getOp)) {
    requireReadAllAccess()
    val r = getAll
    // We do the json conversion here - so that it happens inside of our try/catch block
    Extraction.decompose(r)
  }

  /**
   * Using the current query parameters, return all matching records (paging and ordering is supported as well
   */
  protected def getAll(): List[T] = {
    haltMethodNotAllowed()
  }

  private lazy val findByIdOp =
    (apiOperation[T]("findById")
      summary "Find by id"
      parameters (
        pathParam[String]("id").description(s"Id of $aName that needs to be fetched")))

  /**
   * Find an object
   */
  get("/:id", operation(findByIdOp)) {
    findById
  }

  /**
   * Get the object associated with the provided id param (or fatally end the request with a 404)
   */
  protected def findById(implicit request: HttpServletRequest) = {
    val id = params("id")
    val r = companion.find(id).getOrElse(haltNotFound(s"$id not found"))

    requireReadAccess(r)
  }

  private lazy val createByIdOp =
    (apiOperation[String]("createById")
      summary "Create by id"
      parameters (
        bodyParam[T],
        pathParam[String]("id").description(s"Id of $aName that needs to be created")))

  post("/:id", operation(createByIdOp)) {
    haltNotFound()
  }

  private lazy val deleteByIdOp =
    (apiOperation[String]("deleteById")
      summary "Delete by id"
      parameters (
        pathParam[String]("id").description(s"Id of $aName that needs to be deleted")))

  delete("/:id", operation(deleteByIdOp)) {
    val o = findById
    requireDeleteAccess(o)
    doDelete(o)
  }
}

object ApiController {
  /**
   * Does the user have appropriate access to see the specified AccessCode?
   */
  def isAccessAllowed(requiredIn: Int, isOwner: Boolean, isResearcher: Boolean, default: Int) = {
    val required = if (requiredIn == AccessCode.DEFAULT_VALUE)
      default
    else
      requiredIn

    required match {
      case AccessCode.DEFAULT_VALUE =>
        throw new Exception("Bug: Can't check against default access code")
      case AccessCode.PRIVATE_VALUE =>
        isOwner
      case AccessCode.PUBLIC_VALUE =>
        true
      case AccessCode.SHARED_VALUE =>
        true // If they got here they must have the URL
      case AccessCode.RESEARCHER_VALUE =>
        isOwner || isResearcher
    }
  }

  val defaultVehicleViewAccess = AccessCode.PUBLIC_VALUE
  val defaultVehicleControlAccess = AccessCode.PRIVATE_VALUE
}
