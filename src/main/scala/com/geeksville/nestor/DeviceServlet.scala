/**
 * *****************************************************************************
 * Copyright 2013 Kevin Hester
 *
 * See LICENSE.txt for license details.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.geeksville.nestor

import org.scalatra._
import scalate.ScalateSupport
import org.bson.types.ObjectId
import com.novus.salat._
import com.novus.salat.global._
import org.json4s.JsonAST.JString
import java.io.InputStream
import com.geeksville.mavlink.BinaryMavlinkReader
import com.geeksville.flight.Location
import com.geeksville.flight.VehicleSimulator
import com.geeksville.util.FileTools
import java.io.ByteArrayInputStream
import com.geeksville.logback.Logging
import com.geeksville.mavlink.TimestampedMessage
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer
import com.geeksville.mavlink.DataReducer
import java.net.URI
import java.net.URL
import org.scalatra.servlet.FileUploadSupport
import com.google.common.io.ByteStreams
import java.util.Date

case class MessageJson(time: Long, msg: String)
case class ParameterJson(id: String, value: String, doc: String, rangeOk: Boolean, range: Option[Seq[Float]])

/**
 * The userId (if not null) and password must match the user record in the DB
 *
 * If userId is null this is an anonymous upload
 */
case class UploadCompletedJson(userId: Option[String] = None, userPass: Option[String] = None, key: String)

class DeviceServlet extends NestorStack with Logging with FileUploadSupport /* with CorsSettings */ {

  import org.json4s.native.Serialization
  import org.json4s.native.Serialization.{ read, write }
  private implicit val formats = Serialization.formats(org.json4s.NoTypeHints)

  val tlogMime = TLogChunk.mimeType
  val msgJsonMime = "application/json"
  val paramJsonMime = "application/json"
  val summaryJsonMime = "application/json"

  /**
   * Common behavior for any gets using id as a key
   */
  protected def getById(transformers: RouteTransformer*)(action: TLogChunk => ActionResult) = get(transformers: _*) {
    val id = params("id")
    val chunk = getChunksById(id)

    // convert response to json and return as OK
    chunk match {
      case Some(x) =>
        try {
          action(x)
        } catch {
          case ex: Exception =>
            ex.printStackTrace()
            println("Unexpected error: " + ex)
            InternalServerError(ex)
        }
      case None =>
        NotFound("Item with id " + id + " not found");
    }
  }

  /**
   * Decode the ID string and return either a particular chunk file or all chunk files.  Possibly with data reduction (FIXME)
   */
  private def getChunksById(id: String) = {
    TLogChunkDAO.findOneById(id)
  }

  /**
   * Get the raw records from a particular tlog file ( eventually match based on file suffix to give back file as a kmz instead of tlog)
   * per http://www.scalatra.org/2.2/guides/http/routes.html
   */
  getById("/tlog/:id.tlog") { chunk =>
    contentType = tlogMime

    // Ok(grater[TLogChunk].toJSON(x))
    chunk.bytes.map { b =>
      Ok(b)
    }.getOrElse { NotFound("tlog missing") }
  }

  getById("/tlog/:id.kml") { chunk =>
    contentType = "application/vnd.google-earth.kml+xml"

    PlaybackModel.fromBytes(chunk).map { model =>
      Ok(model.toKMLBytes(uriBase))
    }.getOrElse { NotFound("tlog missing") }
  }

  getById("/tlog/:id.kmz") { chunk =>
    contentType = "application/vnd.google-earth.kmz"

    // Ok(grater[TLogChunk].toJSON(x))
    PlaybackModel.fromBytes(chunk).map { model =>
      Ok(model.toKMZBytes(uriBase, false))
    }.getOrElse { NotFound("tlog missing") }
  }

  getById("/tlog/:id.gmaps.kmz") { chunk =>
    contentType = "application/vnd.google-earth.kmz"

    // Ok(grater[TLogChunk].toJSON(x))
    PlaybackModel.fromBytes(chunk).map { model =>
      Ok(model.toKMZBytes(uriBase, true))
    }.getOrElse { NotFound("tlog missing") }
  }

  getById("/tlog/:id/messages.json") { chunk =>
    contentType = msgJsonMime

    // Ok(grater[TLogChunk].toJSON(x))
    PlaybackModel.fromBytes(chunk).map { model =>

      val msgs = model.messages // .take(100).toSeq // FIXME, temp limit for testing
      val msgj = msgs.map { a => MessageJson(a.time, a.msg.toString) }
      val r = grater[MessageJson].toCompactJSONArray(msgj)
      Ok(r)
    }.getOrElse { NotFound("tlog missing") }
  }

  getById("/tlog/:id/summary.json") { chunk =>
    contentType = summaryJsonMime

    Ok(grater[MissionSummary].toCompactJSON(chunk.summary))
  }

  getById("/tlog/:id/parameters.json") { chunk =>
    contentType = paramJsonMime

    // Ok(grater[TLogChunk].toJSON(x))
    PlaybackModel.fromBytes(chunk).map { model =>

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
      val r = grater[ParameterJson].toCompactJSONArray(sorted)
      Ok(r)
    }.getOrElse { NotFound("tlog missing") }
  }

  private def genParams(chunk: TLogChunk, complete: Boolean) = {
    contentType = "application/param+text"

    // Ok(grater[TLogChunk].toJSON(x))
    PlaybackModel.fromBytes(chunk).map { model =>

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
      val url = publicUriBase.resolve("/view/" + chunk.id).toString
      val header = "# Auto generated from " + url
      val r = header + "\n" + sorted.mkString("\n")
      Ok(r)
    }.getOrElse { NotFound("tlog missing") }
  }

  /// Parameters as a loadable param file
  getById("/tlog/:id.complete.param") { chunk =>
    genParams(chunk, true)
  }

  /// Parameters but only with the sharable bits
  getById("/tlog/:id.share.param") { chunk =>
    genParams(chunk, false)
  }

  getById("/tlog/:id/dseries.json") { chunk =>
    contentType = msgJsonMime

    // Ok(grater[TLogChunk].toJSON(x))
    PlaybackModel.fromBytes(chunk).map { model =>

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
          val jarray = JArray(array.toList)
          JObject("label" -> s._1, "data" -> jarray, "color" -> i)
      }
      //val r = JArray(List(JInt(4)))
      Ok(compact(render(r)))
    }.getOrElse { NotFound("tlog missing") }
  }

  private def toMongoLoc(l: Location) = {
    MongoLoc(l.lon, l.lat) // Note: Mongo expects x,y (or lon,lat) not the other way around  
  }

  private def inputToTLog(is: InputStream) = bytesToTLog(ByteStreams.toByteArray(is), "")

  private def bytesToTLog(bytes: Array[Byte], ownerId: String) = {
    // FIXME someday support some metadata besides raw tlogs

    //fixme - support a block level read chunks from input stream ?
    logger.debug("Extracting bytes")

    // Scan the files at upload to see if they are valid
    val reader = new BinaryMavlinkReader(bytes)
    val model = new PlaybackModel
    model.loadMessages(reader.toSeq)

    println("Num records: " + model.numMessages + " num bytes: " + bytes.size)
    if (model.numMessages < 2)
      None // Not enough records to be interesting
    else {
      for { l1 <- model.startPosition; l2 <- model.endPosition } yield {
        val start = new Date(model.startTime.getOrElse(0L) / 1000)
        val end = new Date(model.currentTime.getOrElse(0L) / 1000)

        val chunk = TLogChunk(start, end, toMongoLoc(l1), toMongoLoc(l2),
          model.numMessages, model.summary(ownerId))
        logger.debug("Generated: " + chunk)
        chunk
      }
    }
  }

  /**
   * Completes a file upload which was started with S3
   * JSON body of the upload is a UploadCompletedJson
   * The response is a tlogchunk id
   */
  post("/upload/froms3.json") {
    contentType = "application/json"

    // val stest = write[UploadCompletedJson](UploadCompletedJson("cat"))
    println("S3 upload: " + request.body)
    val args = read[UploadCompletedJson](request.body)
    val bytes = TLogChunk.getUploadByPath(args.key)

    try {
      bytesToTLog(bytes, args.userId.getOrElse("")) match {
        case Some(tlog) =>
          // Copy the data to our permanent home
          println("Moving to permanent S3 home: ")
          S3Client.copyObject(args.key, TLogChunk.inUsePrefix + tlog.id)
          TLogChunkDAO.insert(tlog) match {
            case Some(_) =>
              Ok(compact(render(JString(tlog.id): JValue)))
            case None =>
              InternalServerError(compact(render(JString("Failed to insert"): JValue)))
          }

        case None =>
          NotAcceptable(compact(render(JString("Not enough Mavlink found"): JValue)))
      }
    } catch {
      case ex: Throwable =>
        ex.printStackTrace()
        println("Failure in upload: " + ex)
        InternalServerError(compact(render(JString(ex.toString): JValue)))
    }
  }

  /**
   * Returns Either an error message or the tlog id
   */
  private def handleUpload(stream: InputStream) = {
    inputToTLog(stream).map { rec =>
      val id = TLogChunkDAO.insert(rec)

      id match {
        case Some(x) => Right(x.toString)
        case None => Left("Failed to insert")
      }
    }.getOrElse {
      Left("Not enough mavlink in file")
    }
  }

  /**
   * Handle an upload from a GCS - response with JSON
   * Upload a new tlog (eventually support uploading by chunks by accepting posts to tlog/id to append a new chunk to the given tlog
   * FIXME - nest all this stuff under a user namespace?  so each user has their own set of tlogs punkgeek/tlog/xxxx)
   */
  post("/upload/binary.json", request.getContentType == tlogMime) {

    contentType = "application/json"
    handleUpload(request.inputStream) match {
      case Right(x) => Ok(compact(render(JString(x): JValue)))
      case Left(x) => NotAcceptable(compact(render(JString(x): JValue)))
    }
  }

  /** Handle a http form POST with multipart file attachments */
  post("/upload/form.json") {
    contentType = "application/json"

    val file = fileParams("tlogs")

    handleUpload(file.getInputStream) match {
      case Right(x) =>
        println("Form upload succeeded: " + x)
        Ok(compact(render(JString(x): JValue)))
      case Left(x) =>
        println("Form upload failed: " + x)
        NotAcceptable(compact(render(JString(x): JValue)))
    }
  }

  /**
   * Handle upload from curl or someone else who expects an HTML response
   */
  post("/upload/binary.html", request.getContentType == tlogMime) {

    contentType = "text/html"
    handleUpload(request.inputStream) match {
      case Right(x) =>
        val r = <html>
                  <body>
                    Tlog accepted.  Now viewable<a href={ "/view/" + x }> here</a>
                  </body>
                </html>
        Ok(r)
      case Left(x) =>
        val r = <html>
                  <body>
                    Error:{ x }
                  </body>
                </html>
        NotAcceptable(r)
    }
  }

  /*
  delete("/articles/:id") {
    // delete the article with the specified :id
  }
  * 
  */
}
