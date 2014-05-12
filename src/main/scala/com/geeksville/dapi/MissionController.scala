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

case class ParameterJson(id: String, value: String, doc: String, rangeOk: Boolean, range: Option[Seq[Float]])

/// Atmosphere doesn't work in the test framework so we split it out
class MissionController(implicit swagger: Swagger) extends SharedMissionController with AtmosphereSupport {
  private lazy val liveOp = apiOperation[AtmosphereClient]("live") summary "An atmosphere endpoint containing an endless stream of mission update messages"
  atmosphere("/live", operation(liveOp)) {
    new AtmosphereLive(tryLogin())
  }
}

class SharedMissionController(implicit swagger: Swagger) extends ActiveRecordController[Mission]("mission", swagger, Mission) {

  /**
   * We allow reading vehicles if the vehicle is not protected or the user has suitable permissions
   */
  override protected def requireReadAccess(o: Mission) = {
    val userId = for {
      v <- o.vehicle
      uid <- v.userId
    } yield {
      uid
    }

    warn("FIXME: allowing anyone to read missions to make tlog/kmz download work")
    //requireAccessCode(userId.getOrElse(-1L), o.viewPrivacy, ApiController.defaultVehicleViewAccess)
    super.requireReadAccess(o)
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
      // "Content-Type"        -> (file.contentType.getOrElse("application/octet-stream")),
      "Content-Disposition" -> ("attachment; filename=\"" + filename + "\"")))
  }

  roField("messages.tlog") { (o) =>
    contentType = Mission.mimeType
    OkWithFilename(o.tlogBytes.getOrElse(haltNotFound()), o.tlogId.get.toString + ".tlog")
  }

  // Helper class for generating json
  case class MessageJson(time: Long, msg: String)

  roField[Seq[MessageJson]]("messages.json") { (o) =>
    val m = getModel(o)
    var msgs = m.messages

    params.get("page_size").foreach { numrecs =>
      msgs = msgs.take(numrecs.toInt)
    }

    // FIXME - instead of passing msg content as string, it should be a json object
    msgs.map { a => MessageJson(a.time, a.msg.toString) }
  }

  private def getModel(o: Mission) = o.model.getOrElse(haltNotFound("no tlog found"))

  /// A recommended end user visible (HTML) view to see this mission
  private def viewUrl(o: Mission) = publicUriBase.resolve("/view/" + o.id)

  roField("messages.kml") { (o) =>
    contentType = "application/vnd.google-earth.kml+xml"

    // FIXME - we should pull our static content (icons etc... from a cdn)
    new KMLFactory(getModel(o)).toKMLBytes(uriBase)
  }

  roField("messages.kmz") { (o) =>
    contentType = "application/vnd.google-earth.kmz"

    // FIXME - we should pull our static content (icons etc... from a cdn)
    new KMLFactory(getModel(o)).toKMZBytes(uriBase, false)
  }

  roField("messages.gmaps.kmz") { (o) =>
    contentType = "application/vnd.google-earth.kmz"

    // FIXME - we should pull our static content (icons etc... from a cdn)
    new KMLFactory(getModel(o)).toKMZBytes(uriBase, true)
  }

  roField("messages.geo.json") { (o) =>
    // FIXME - we should pull our static content (icons etc... from a cdn)
    new GeoJSONFactory(getModel(o)).toGeoJSON()
  }

  /// This is a temporary endpoint to support the old droneshare API - it will be getting refactored substantially
  roField("dseries") { (o) =>
    val model = getModel(o)
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

  roField("parameters.json") { (o) =>
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
    val sorted = unsorted.sortWith { case (a, b) => a.id < b.id }
    sorted
  }

  private def genParams(o: Mission, complete: Boolean) = {
    contentType = "application/vnd.diydrones.param+text"

    val model = getModel(o)

    val unfiltered = model.parameters
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
    val sorted = unsorted.sorted
    val header = "# Auto generated from " + viewUrl(o)
    val r = header + "\n" + sorted.mkString("\n")
  }

  /// Parameters as a loadable param file
  roField("parameters.complete") { (o) =>
    genParams(o, true)
  }

  /// Parameters but only with the sharable bits
  roField("parameters.share") { (o) =>
    genParams(o, false)
  }

}

