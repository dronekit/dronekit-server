package com.geeksville.dapi

import org.scalatra._
import org.scalatra.swagger.SwaggerSupport
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra.json._
import org.scalatra.swagger.Swagger
import com.geeksville.dapi.model._
import java.net.URL
import com.geeksville.mavlink.DataReducer
import com.geeksville.nestor.ParamVal
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JValue
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JDouble
import org.json4s.JsonAST.JString
import org.json4s.JsonDSL._
import com.github.aselab.activerecord.dsl._
import com.geeksville.json.ActiveRecordSerializer
import org.scalatra.atmosphere._
import com.geeksville.dapi.auth.UserPasswordStrategy
import java.util.UUID
import org.scalatra.swagger.DataType
import org.scalatra.swagger.SwaggerSupportSyntax.ModelParameterBuilder
import org.scalatra.swagger.StringResponseMessage
import com.geeksville.apiproxy.APIConstants
import org.json4s.Extraction
import com.geeksville.json.GeeksvilleFormats
import _root_.akka.pattern.ask
import com.geeksville.json.EnumSerializer
import scala.concurrent.Future
import com.github.aselab.activerecord.ActiveRecordException
import org.squeryl.dsl.TDouble
import org.squeryl.dsl.TString
import com.geeksville.nasa.NASAClient
import java.util.Date

case class ParameterJson(id: String, value: String, doc: String, rangeOk: Boolean, range: Option[Seq[Float]])

// Helper class for generating json
case class MessageJson(t: Long, typ: String, fld: List[(String, Any)])
case class MessageHeader(modelType: String, messages: Seq[MessageJson])

/// Atmosphere doesn't work in the test framework so we split it out
class MissionController(implicit swagger: Swagger) extends SharedMissionController with AtmosphereSupport {
  private lazy val liveOp = apiOperation[AtmosphereClient]("live") summary "An atmosphere endpoint containing an endless stream of mission update messages"
  atmosphere("/live", operation(liveOp)) {
    dumpRequest()

    request.header("Via").foreach { f =>
      if (f == "HTTP/1.1 NetScaler") {
        error("Bad atmo client - not placing in PENALTY BOX!")
        //Thread.sleep(5 * 60 * 1000) // Keep the client from trying again for 5 minutes
        //haltBadRequest("Sorry, our (beta) server really doesn't like your firewall.  Would you mind emailing support@droneshare.com and we can debug it?")
      }
    }

    // We support customizing the feed for a particular user (note - this customization doesn't guarantee the user is really logged in or 
    // their password is valid.  (FIXME - atmo headers need to include valid cookies etc...)
    // val login = tryLogin()
    val login = params.get("login").flatMap(User.find)
    warn(s"Passing into atmo $login")
    new AtmosphereLive(login)
  }
}

class SharedMissionController(implicit swagger: Swagger) extends ActiveRecordController[Mission, MissionJson]("mission", swagger, Mission) with MissionUploadSupport {

  /**
   * We allow reading vehicles if the vehicle is not protected or the user has suitable permissions.
   *
   * @param isSharedLink if true we are resolving a _specific_ URL which was passed between users (so not something like a google
   * search or top level map view)
   */
  override protected def filterForReadAccess(oin: Mission, isSharedLink: Boolean = false) = {
    val r = super.filterForReadAccess(oin).flatMap { o =>
      val userId = o.userId

      // Use the privacy setting from the vehicle if the mission specifies default sharing
      var vehiclePrivacy = o.vehicle.viewPrivacy

      debug(s"filter check for $o, userId=$userId, privCode=${o.viewPrivacy}, vehiclePriv=$vehiclePrivacy, isShared=$isSharedLink")

      if (vehiclePrivacy == AccessCode.DEFAULT_VALUE)
        vehiclePrivacy = ApiController.defaultVehicleViewAccess

      if (isAccessAllowed(userId.getOrElse(-1L), o.viewPrivacy, vehiclePrivacy, isSharedLink))
        Some(o)
      else
        None
    }

    if (!r.isDefined) warn(s"filter allowed=${r.isDefined} to $oin, isShared=$isSharedLink")
    r
  }

  /**
   * Filter read access to a potentially protected record.  Subclasses can override if they want to restrict reads based on user or object
   * If not allowed, override should call haltUnauthorized()
   */
  override protected def requireWriteAccess(o: Mission) = {
    val userId = o.userId

    // Be even more strict than this - only let them change the mission object if the owner (for now)
    // requireAccessCode(userId.getOrElse(-1L), o.controlPrivacy, ApiController.defaultVehicleControlAccess)
    requireBeOwnerOrAdmin(userId.getOrElse(-1L))

    super.requireWriteAccess(o)
  }

  /**
   * We provide extra data in this case: the (expensive to generate) doarama URL
   */
  override protected def toSingletonJSON(o: Mission): JValue = {
    Extraction.decompose(o)(DefaultFormats ++ GeeksvilleFormats + EnumSerializer.create(AccessCode) + new MissionSerializer(true))
  }

  // FIXME - experimenting with reflection
  import scala.reflect.runtime.{ universe => ru }
  val m = ru.runtimeMirror(classOf[MissionSummary].getClassLoader)
  val summaryType = ru.typeOf[MissionSummary]
  summaryType.members.foreach { m =>
    println(s"Member: $m, ${m.typeSignature}")
  }

  private val doubleFields = Set("maxAlt", "maxGroundspeed", "maxAirspeed", "latitude", "longitude", "flightDuration")
  private val stringFields = Set("summaryText", "softwareVersion", "softwareGit")

  private def makeDoubleOp(field: TypedExpression[Double, TDouble], opcode: String) = {
    type CmpType = Double

    opcode match {
      case "GT" => field.>(_: CmpType)
      case "GE" => field.>=(_: CmpType)
      case "EQ" => field.===(_: CmpType)
      case "NE" => field.<>(_: CmpType)
      case "LT" => field.<(_: CmpType)
      case "LE" => field.<=(_: CmpType)
      //case "LIKE" => field.like(_: String)

      case _ => throw new ActiveRecordException("Bad opcode: " + opcode)
    }
  }

  private def makeStringOp(field: TypedExpression[String, TString], opcode: String) = {
    type CmpType = String

    opcode match {
      case "GT" => field.>(_: CmpType)
      case "GE" => field.>=(_: CmpType)
      case "EQ" => field.===(_: CmpType)
      case "NE" => field.<>(_: CmpType)
      case "LT" => field.<(_: CmpType)
      case "LE" => field.<=(_: CmpType)
      case "LIKE" => field.like(_: String)

      case _ => throw new ActiveRecordException("Bad opcode: " + opcode)
    }
  }

  /// Subclasses can override if they want to make finding fields smarter
  override protected def applyFilterExpressions(rIn: myCompanion.Relation, whereExp: Seq[LogicalBoolean]) = {
    var r = rIn

    var forSuper = whereExp

    // We have to do this scan FIRST because the join rule isn't smart enough to keep previous results 
    // FIXME - the loop over whereExp should be done _inside_ the join.where
    forSuper = whereExp.filter { w =>
      if (doubleFields.contains(w.colName) || stringFields.contains(w.colName)) {
        // FIXME - this is all pretty nasty

        // val fieldTerm = ru.typeOf[MissionSummary].declaration(ru.newTermName(w.colName)).asTerm

        debug(s"Handling special col: $w")
        r = Mission.joins[MissionSummary]((mission, summary) => summary.missionId === mission.id).where { (mission, summary) =>

          // FIXME - figure out how to do this nasty thing with reflection
          //val im = m.reflect(summary)
          //val fieldTerm = summaryType.declaration(ru.newTermName(w.colName)).asTerm
          //val reflectedField = summaryType.

          if (doubleFields.contains(w.colName)) {
            val field = w.colName match {
              case "maxAlt" => summary.maxAlt
              case "maxGroundspeed" => summary.maxGroundSpeed
              case "maxAirspeed" => summary.maxAirSpeed
              case "latitude" => summary.latitude.getOrElse(0.0)
              case "longitude" => summary.longitude.getOrElse(0.0)
              case "flightDuration" => summary.flightDuration.getOrElse(0.0)
            }

            val op = makeDoubleOp(field, w.opcode)
            op(w.cmpValue.toDouble)
          } else {
            val field = w.colName match {
              case "summaryText" => summary.text.getOrElse("")
              case "softwareVersion" => summary.softwareVersion.getOrElse("")
              case "softwareGit" => summary.softwareGit.getOrElse("")
            }

            val op = makeStringOp(field, w.opcode)
            op(w.cmpValue)
          }
        }.select((mission, summary) => mission)

        false
      } else
        true
    }

    // Find which clauses we can handle as special cases and handle them, for the others let the superclass handle it
    forSuper = forSuper.filter { w =>
      var handled = w.colName match {
        case "userName" =>
          // Ugh - bug in activerecords two levels deep - https://github.com/aselab/scala-activerecord/issues/48
          // r = r.where(_.vehicle.user.login === w.cmpValue)
          val u = User.find(w.cmpValue).getOrElse(throw new ActiveRecordException("Can't find user"))
          val vehicleIds = u.vehicles.map { v => v.id } // .toList
          debug(s"Looking for missions by $u, in vehicleIds: " + vehicleIds.mkString(","))
          r = r.where(_.vehicleId in vehicleIds)
          //debug(s"FIXME - num matching missions: " + r.size)
          true

        case x @ _ =>
          debug(s"Letting superclass handle $x")
          false
      }

      !handled
    }

    super.applyFilterExpressions(r, forSuper)
  }

  override protected def getOp = (super.getOp
    parameter queryParam[Option[String]]("within").description("Flights within a specified GeoJSON polygon")
    parameter queryParam[Option[Boolean]]("completed").description("Completed flights only")
    parameter queryParam[Option[Boolean]]("live").description("Live flights only"))

  //raField[Mavlink]("mavlink", null, { (v) => })
  //roField[List[Location]]("location", null)
  //roField[List[String]]("mode", null)

  // Send a response with a recommended filename
  def OkWithFilename(payload: Any, filename: String) = {
    Ok(payload, Map(
      //"Content-Type" -> contentType,
      "Content-Disposition" -> ("attachment; filename=\"" + filename + "\"")))
  }

  unsafeROField("messages.tlog") { (o) =>
    warn("FIXME: allowing anyone to read missions to make tlog/kmz download work")
    val r = o.logfileName.map { logname =>
      debug(s"Reading from $logname")
      contentType = APIConstants.extensionToMimeType(logname).getOrElse(APIConstants.tlogMimeType)
      if (contentType != APIConstants.flogMimeType) {
        // contentType = "application/octet-stream"
        response.characterEncoding = None
      }

      val bytes = o.tlogBytes
      debug(s"File is " + bytes.map(_.size) + s" bytes long, contenttype=$contentType")
      OkWithFilename(bytes.getOrElse(haltNotFound("log not found")), logname)
    }.getOrElse(haltNotFound("No logname specified"))

    r
  }

  roField[MessageHeader]("messages.json") { (o) =>
    applyMissionCache()
    val m = getModel(o)
    var msgs = m.abstractMessages

    params.get("page_offset").foreach { numrecs =>
      msgs = msgs.drop(numrecs.toInt)
    }

    params.get("page_size").foreach { numrecs =>
      msgs = msgs.take(numrecs.toInt)
    }

    // FIXME - instead of passing msg content as string, it should be a json object
    val json = msgs.map { a => MessageJson(a.time, a.msg.messageType, a.msg.fields.toList) }
    MessageHeader(m.modelType, json)
  }

  private def getModel(o: Mission) = o.model.getOrElse(haltNotFound("no logs found"))
  private def getTLOGModel(o: Mission) = o.tlogModel.getOrElse(haltNotFound("no tlog found"))

  /// A recommended end user visible (HTML) view to see this mission
  private def viewUrl(o: Mission) = publicUriBase.resolve("/view/" + o.id)

  roField("messages.kml") { (o) =>
    contentType = "application/vnd.google-earth.kml+xml"
    applyMissionCache()

    // FIXME - we should pull our static content (icons etc... from a cdn)
    new KMLFactory(getModel(o)).toKMLBytes(uriBase)
  }

  /// Allow some mission data to be cached up to an hr
  def applyMissionCache() {
    if (!isAppDeveloper)
      applyCache(60 * 60)
    else
      debug("Suppressing caching - in developer mode")
  }

  unsafeROField("messages.kmz") { (o) =>
    contentType = "application/vnd.google-earth.kmz"
    applyMissionCache()

    // FIXME - we should pull our static content (icons etc... from a cdn)
    new KMLFactory(getModel(o)).toKMZBytes(uriBase, false)
  }

  roField("messages.gmaps.kmz") { (o) =>
    contentType = "application/vnd.google-earth.kmz"
    applyMissionCache()

    // FIXME - we should pull our static content (icons etc... from a cdn)
    new KMLFactory(getModel(o)).toKMZBytes(uriBase, true)
  }

  roField("messages.geo.json") { (o) =>
    applyMissionCache()

    // FIXME - we should pull our static content (icons etc... from a cdn)
    new GeoJSONFactory(getModel(o)).toGeoJSON().getOrElse(haltGone("No position data found"))
  }

  roField("analysis.json") { (o) =>
    this.synchronized { // FIXME: For now we only allow one user to be downloading an analysis plot at a time - just in case the python tool starts spinnig
      applyMissionCache()

      val report = o.tlogBytes.flatMap { bytes =>
        if (o.isDataflashText || o.isDataflashBinary)
          new AnalysisFactory(bytes, o.isDataflashText).toJSON()
        else
          None
      }

      report.getOrElse(haltGone("Flight analysis is only supported for dataflash files"))
    }
  }

  /// This is a temporary endpoint to support the old droneshare API - it will be getting refactored substantially
  roField("dseries") { (o) =>
    applyMissionCache()

    val model = getTLOGModel(o)
    val msgs = model.messages // .take(10000) // FIXME, temp limit for testing

    // Parse strings like the following: "MAVLINK_MSG_ID_PARAM_VALUE : param_value=0.0 bob=45"

    // We try to ignore the boring mavling_msg_id_ prefix if we can
    val IdParse = "(?:MAVLINK_MSG_ID_)?(\\S+) :(.*)".r
    val ArgParse = "(\\S+)=(\\S+)".r

    val isPlottable = DataReducer.filterByIds(DataReducer.plottableCommands) _

    // Generate a seq of time -> Seq[ParamVal]
    val pmsgs = msgs.flatMap { m =>
      if (!isPlottable(m.msg))
        None
      else {
        // Super skanky - until I can fix the mavlink code generator the easiest way to get values is to parse the string
        val str = m.msg.toString

        str match {
          case IdParse(id, args) =>
            //println("Considering args: " + args)
            val argsFormatted = args.split(" ").flatMap { arg =>
              arg.trim match {
                case ArgParse(k, v) =>
                  ParamVal.perhapsCreate(id + "." + k, v)
                case "" =>
                  None // Must have been whitespace
                case x @ _ =>
                  println("Error, can't parse: " + x)
                  None
              }
            }
            Some(m.timeMsec -> argsFormatted) // Javascript wants msecs
        }
      }
    }

    /* Emit json like this:
     * [ { label: "Foo", data: [ [10, 1], [17, -14], [30, 5] ] },
         { label: "Bar", data: [ [11, 13], [19, 11], [30, -7] ] } ]
     */

    // Swizzle to get a seq of plotSeries (each data element is a two entry array)
    case class XYPair(x: Long, y: Double)
    val seriesOut = HashMap[String, ArrayBuffer[XYPair]]()

    pmsgs.foreach {
      case (time, params) =>
        params.foreach { p =>
          val buffer = seriesOut.getOrElseUpdate(p.name, ArrayBuffer[XYPair]())

          // Many of the mavlink packets send the same data over and over - for plotting we only care about changes
          val isNew = buffer.isEmpty || buffer.last.y != p.v
          if (isNew)
            buffer += XYPair(time, p.v)
        }
    }

    // return thins sorted in order and properly colored
    val r = seriesOut.toSeq.sortWith((a, b) => (a._1 < b._1)).zipWithIndex.map {
      case (s, i) =>
        val array = s._2.map { pair =>
          JArray(List(JInt(pair.x), JDouble(pair.y))): JValue
        }

        // The following is the slick json4s version
        // val jarray = JArray(array.toList)
        // JObject("label" -> JString(s._1), "data" -> jarray, "color" -> JInt(i))
        ("label" -> s._1) ~ ("data" -> array) ~ ("color" -> i)
    }
    r
  }

  val client = new NASAClient()

  get("/:id/submitNASA") {
    if (!user.isAdmin)
      haltUnauthorized("Private API testing only")

    val mission = findById

    val primaryContact = mission.vehicle.user.login + "@droneshare"
    val aircraftType = mission.vehicle.vehicleType.getOrElse(haltNotFound("Invalid vehicle type"))
    val flightNotes = mission.summary.text.getOrElse(haltNotFound("Invalid summary text"))
    val primaryPhone = None
    val flightStartTime = mission.summary.startTime.getOrElse(haltNotFound("Invalid flight start"))
    val flightEndTime = mission.summary.endTime.getOrElse(haltNotFound("Invalid flight end"))
    val minAltitude = 0L // FIXME - how is this encoded?
    val maxAltitude = mission.summary.maxAlt.toLong

    // FIXME - for now we just use the waypoints - is there a better way to specify the amount of airspace we want
    val model = mission.model.getOrElse(haltNotFound("Can't generate model"))
    val locs = model.waypoints.map(_.location)

    val r = client.requestAuth(primaryContact, aircraftType, flightNotes, primaryPhone, flightStartTime, flightEndTime, minAltitude, maxAltitude, locs)

    println("NASA says: " + r)
    r
  }

  roField("parameters.json") { (o) =>
    applyMissionCache()

    val model = getModel(o)
    val ps = model.parameters
    val unsorted = ps.flatMap { a =>
      for {
        id <- a.getId
      } yield {
        // The json stuff doesn't understand tuples yet
        val range = a.rangeOpt.map { t => t.productIterator.map(_.asInstanceOf[Float]).toSeq }

        ParameterJson(id,
          a.asString.getOrElse("?"),
          a.docs.map(_.documentation).getOrElse(""), a.isInRange, range)
      }
    }
    val sorted = unsorted.toSeq.sortWith { case (a, b) => a.id < b.id }
    sorted
  }

  private def genParams(o: Mission, complete: Boolean) = {
    applyMissionCache()
    contentType = "application/vnd.diydrones.param+text"

    val model = getModel(o)

    val unfiltered = model.parameters

    if (unfiltered.isEmpty)
      haltNotFound("This flight log did not include parameters")

    val ps = if (complete)
      unfiltered
    else
      unfiltered.filter(_.isSharable)

    val unsorted = ps.flatMap { a =>
      for {
        id <- a.getId
        v <- a.getValue
      } yield {
        "%s,%s".format(id, v)
      }
    }
    val sorted = unsorted.toSeq.sorted
    val header = "# Auto generated from " + viewUrl(o)
    header + "\n" + sorted.mkString("\n")
  }

  /// Parameters as a loadable param file
  unsafeROField("parameters.complete") { (o) =>
    genParams(o, true)
  }

  /// Parameters but only with the sharable bits
  roField("parameters.share") { (o) =>
    genParams(o, false)
  }

  protected def staticMap =
    (apiOperation[List[MissionJson]]("staticMap")
      summary s"Get recent flights suitable for a global map view")

  /// Provide the same information that would normally be returned in the initial atmosphere download (to allow non atmo clients to show maps)
  get("/staticMap", operation(staticMap)) {
    val space = SpaceSupervisor.find() // FIXME - eventually find the supervisor that makes sense for the current

    applyNoCache(response) // Tell client to never cache this (it will change)
    new AsyncResult {

      val is = {
        val f = space ? SpaceSupervisor.GetInitialJSON(tryLogin())
        // f.map(jresult => Ok(jresult))
        f
      }
    }
  }

  private val newEndpointDocs = """
    Note: I'm temporarily placing these docs here for review - to make it easy to just move text around when I create the real code
    and the swagger webdocs.  Please add comments via github.
    
    So Arthur, Ramon and I have been discussing how to support extra flight metadata in the web GUI and in our various GCS apps.  
    This proposal is for the creation of a 'heirachical folder of datafiles or URLs which can be accessed from clients using standard
    REST conventions.
    
    Note: This documentation is currently placed under the mission node, but when implemented it will actually be available under mission,
    user and vehicle.  So clients will have the option of attaching extra data under any of those nodes.
    
    Use cases:
    * Allow GCS and web UI to share a richer notion of wpts/flight-plans than supported by the vehicle code(i.e. some sort of JSON flt plan Arthur
    and I have been discussing).  These flt plans could be stored under mission, vehicle or user (TBD based on GCS needs)
    * Allow youtube, sketchfab or other oembed based URLs to be assocated with missions - this would allow the web UI to check for these
    optional blobs and if present show a richer UI using this new data
    * Missions could include both TLOGS and dataflash logs - currently we assume only one or the other.
    * Apps could use this store for their own private application state (stored under user or vehicle).  i.e. droidplanner settings etc... 
    magically found by your 3dr ID
    * Someday when we can squirt up images for stitching, this directory tree might be a good home for such raw images before stitching
    
    Proposed API (details to be added based on feedback/proof of concept implementation):
    * Placed under the parent node (i.e. /api/v1/mission/4443d-3042/data or /api/v1/user/kevinh/data
    * Doing a GET of DATA includes the full list of child files as some sort of JSON structure).  Something like:
  { { "path": "droidplanner/extrafile", "appcreator": "<appkey>", "appshare": "public|private", "mime": "<mimetype>" } }
    * Doing a GET of .../data/mydroidplannerblob returns the contents of that blob
    * Doing a PUT of .../data/mydroidplannerblob creates or updates some blob
    * DELETE has the expected behavior (non public data can only be deleted by the app that created it)
    * PUT supports an optional appshare query param, if set it will control access for apps that are different than the creator.  If private
    (the default) only the app that created this node will see it, if public any app can see this node.  (All API operations already have
    the appkey provided by the client)
    * PUT supports a required query param of MIME.  This param must be set to indicate the mimetype of the associated datafile or URL.
    """

  private val addMissionInfo =
    (apiOperation[List[MissionJson]]("uploadForVehicle")
      summary s"Add a new mission (as a tlog, bog or log)"
      notes """This endpoint is designed to facilitate easy log file uploading from GCS applications.  It requires no oauth or
      other authentication (but you will need to use your application's api_key).  You should pass in the user's login and password
      as query parameters.<p>
      
      You'll also need to pick a UUID to represent the vehicle (if your user interface allows the user to specify
      particular models you should associate the UUID with the model - alternatively you can open a WebView and use droneshare to let the
      user pick a model).  If the vehicle has not previously been seen it will be created.<p>
      
      If you are taking advantage of the autoCreate feature, you should specify a user email address and name (so we can send them
      password reset emails if they forget their password).<p>
      
      Both multi-part file POSTs and simple posts of log files as the entire request body are supported.  In the latter case the content
      type must be set appropriately.<p>
      """
      parameters (
        (new ModelParameterBuilder(DataType("file"))).description("log file as a standard html form upload POST").fromBody,
        pathParam[String]("vehicleUUID").description(s"UUID of vehicle to be have mission added (the client should pick a stable UUID)"),
        queryParam[String]("login").description(s"User login (used if not already logged-in via cookie)"),
        queryParam[String]("password").description(s"User password (used if not already logged-in via cookie)"),
        queryParam[String]("email").description(s"Email address (optional, used if user creation is required)").optional,
        queryParam[String]("fullName").description(s"User full name (optional, used if user creation is required)").optional,
        queryParam[Boolean]("autoCreate").description(s"If true a new user account will be created if required"),
        queryParam[String]("privacy").description(s"The privacy setting for this flight (DEFAULT, PRIVATE, PUBLIC, SHARED, RESEARCHER)").optional)
        responseMessage (StringResponseMessage(200, """Success.  Payload will be a JSON array of mission objects.  
        		You probably want to show the user the viewURL for each file, but the other mission fields might also be interesting.""")))

  // Allow adding missions in the easiest possible way for web clients
  post("/upload/:vehicleUUID", operation(addMissionInfo)) {
    requireCreateAccess()

    val privacy = EnumSerializer.stringToEnum(AccessCode, params.getOrElse("privacy", "DEFAULT"))
    val autoCreate = params.getOrElse("autoCreate", "false").toBoolean
    val user = tryLogin().getOrElse {
      if (!autoCreate)
        haltUnauthorized("login not found and autoCreate is false")

      // If user is not logged in, see if we can create them...
      val login = params.getOrElse(UserPasswordStrategy.loginKey, haltUnauthorized("login not specified"))
      val password = params.getOrElse(UserPasswordStrategy.passwordKey, haltUnauthorized("password not specified"))
      val email = params.get("email")
      val fullName = params.get("fullName")

      if (login.isEmpty) {
        error("FIXME - temp hack to cope with buggy clients")
        //Thread.sleep(60 * 1000L)
        haltUnauthorized("Login can not be empty")
      }

      // If the login already exists, but tryLogin() failed, that means the user must have used a bad password
      // Return a better error msg than what createUserAndWelcome would give
      if (User.find(login).isDefined)
        haltUnauthorized("invalid password")

      val u = createUserAndWelcome(login, password, email, fullName)

      // Set the user's default sharing perms based on how this flight was configured
      u.defaultViewPrivacy = privacy
      u.save()

      u
    }

    val id = params("vehicleUUID")
    val v = user.getOrCreateVehicle(UUID.fromString(id))
    if (v.userId.get != user.id)
      haltForbidden("Not your vehicle")

    handleMissionUpload(v, privacy)
  }

  /// Allow web gui to update vehicle
  override protected def updateObject(o: Mission, payload: JObject) = {
    val r = payload.extract[MissionJson]

    val summary: MissionSummary = o.summary
    r.summaryText.foreach { text =>
      debug(s"Setting summary text to $text")
      summary.text = Some(text)
    }
    r.notes.foreach { notes => o.notes = Some(notes) }
    r.viewPrivacy.foreach { o.viewPrivacy = _ }
    summary.save
    o.save

    o
  }

  /* Does not work - bug in activerecord
  override protected def getFiltered() = {
    // For now we strip out test accounts
    var r = super.getFiltered.includes(_.vehicle)

    User.find("test-bob").foreach { u =>
      r = r.not(_.vehicle.userId === u.id)
    }
    // 

    r
  }
  * 
  */
}

