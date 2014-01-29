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
import java.io.ByteArrayOutputStream
import com.geeksville.util.CSVWriter
import scala.collection.mutable.HashSet
import com.geeksville.util.DateUtil
import java.io.PrintWriter
import java.io.FileOutputStream
import scala.collection.mutable.ListBuffer
import simplex3d.math._
import simplex3d.math.double._
import simplex3d.math.double.functions._
import org.mavlink.messages.ardupilotmega.msg_raw_imu
import org.mavlink.messages.ardupilotmega.msg_global_position_int
import java.util.GregorianCalendar
import org.mavlink.messages.ardupilotmega.msg_scaled_pressure

class MainServlet extends NestorStack {

  /*
  get("/testinline") {
    <html>
      <body>
        <h1>Hello, world!</h1>
        Say<a href="hello-scalate">hello to Scalate</a>
        .
      </body>
    </html>
  }
  */

  get("/browse") {
    redirect(url("/view/pkulu2"))
  }

  /**
   * Standard data included with most CSV reports
   */
  private def standardCols(tlog: TLogChunk) = {
    val summary = tlog.summary

    Seq("date" -> DateUtil.isoDateFormat.format(summary.startTime),
      "id" -> tlog.id,
      "vehicleType" -> summary.vehicleTypeGuess,
      "ownerId" -> summary.ownerGuess)
  }

  /**
   * Extract the parameters as a CSVable row
   */
  private def tlogToParamRow(tlog: TLogChunk): Option[Seq[(String, Any)]] = {
    PlaybackModel.fromBytes(tlog).flatMap { model =>
      val params = model.parameters.flatMap { param =>
        for {
          id <- param.getId
          v <- param.getValue
        } yield {
          id -> v
        }
      }

      Some(standardCols(tlog) ++ params)
    }
  }

  private def csvGenerator(cb: (TLogChunk) => Option[Seq[(String, Any)]]) = {
    val maxResults = 10000 // For testing use a lower #
    println("Reading parameters")

    // One row per tlog, each record is a (tlog, list params as tuples)
    val rows = TLogChunkDAO.tlogsRecent(maxResults).flatMap { tlog =>
      //println(s"Loading model for $tlog")
      try {
        cb(tlog)
      } catch {
        case ex: Exception =>
          println(s"Skipping due to $ex")
          None
      }
    }

    println("Generating CSV")
    // CSV requires all column names to be known in advance, so merge all param names, being careful to preserve order
    val seenCols = new HashSet[String]
    val colNames = ListBuffer[String]()
    rows.foreach { r =>
      r.foreach {
        case (id, _) =>
          if (!seenCols.contains(id)) {
            colNames += id
            seenCols += id
          }
      }
    }

    val outStr = new StringBuilder
    val csvOut = new CSVWriter(outStr, colNames)

    rows.foreach { r =>
      csvOut.emit(r: _*)
    }

    val resultStr = outStr.toString

    // For debugging
    val o = new PrintWriter(new FileOutputStream("/tmp/big.csv"))
    o.println(resultStr)
    o.close()

    println("Returning CSV")
    resultStr
  }

  get("/report/parameters.csv") {
    contentType = "text/csv"
    csvGenerator(tlogToParamRow)
  }

  /**
   * Would it be possible for you to         query this on drone share data?
   *
   * For all flights longer than 3 minutes
   * 1 minute into the flight and more than 1 minute before the flight ends
   * Get the max angular rate
   * get the max acceleration
   * get the system type (copter or plane)
   *
   * I'd like to build a histogram of those. It would allow us to better design for the actual application.
   *
   * From tridge:
   *  abs(degrees(RAW_IMU.xgyro*0.001))
   * abs(degrees(RAW_IMU.ygyro*0.001))
   * abs(degrees(RAW_IMU.zgyro*0.001))
   */
  get("/report/lorenz.csv") {
    def generator(tlog: TLogChunk) = {
      val minFlightMinutes = 3.0
      val summary = tlog.summary

      // Do a quick filter to try and avoid reading useless tlogs (the precalculated data currently is missing some of the fields Lorenz requested)
      if (summary.minutes < minFlightMinutes) {
        println(s"Trivally skipping $tlog - too short")
        None
      } else {
        PlaybackModel.fromBytes(tlog).flatMap { model =>
          val duration = model.flightDuration.getOrElse(0.0)
          if (duration < minFlightMinutes * 60) {
            println(s"Skipping $tlog - too short")
            None
          } else {
            var maxAccel = 0.0
            var maxRate = 0.0

            // We only care about packets >1 min from start and >1min from end
            val beginSecond = model.startOfFlightMessage.get.timeSeconds + 60
            val endSecond = model.endOfFlightMessage.get.timeSeconds - 60

            model.inFlightMessages filter { m =>
              m.timeSeconds >= beginSecond && m.timeSeconds < endSecond
            } foreach {
              _.msg match {
                case imu: msg_raw_imu =>
                  maxRate = math.max(maxRate, imu.xgyro * 0.001)
                  maxRate = math.max(maxRate, imu.ygyro * 0.001)
                  maxRate = math.max(maxRate, imu.zgyro * 0.001)
                  maxAccel = math.max(maxAccel, length(Vec3(imu.xacc * 0.001, imu.yacc * 0.001, imu.zacc * 0.001)))
                case _ =>
                // Ignore
              }
            }
            println(s"Found accel/rate=$maxAccel/$maxRate")
            Some(standardCols(tlog) :+ ("maxAcc" -> maxAccel) :+ ("maxRate" -> maxRate))
          }
        }
      }
    }

    contentType = "text/csv"
    csvGenerator(generator)
  }

  /**
   * A quick hack to find all tlogs using the mtk gps.
   * if  all records GLOBAL_POSITION_INT.vz ==0
   * and at least one record shows GLOBAL_POSITION_INT.vx != 0
   */
  get("/report/mtkonly.csv") {
    var numMtk = 0
    var numOther = 0 // FIXME yuck - refactor to not need this vars - the generator model is wrong

    def generator(tlog: TLogChunk) = {
      // FIXME - refactor to have a new filterByFlightLength primitive...

      val minFlightMinutes = 1.0
      val summary = tlog.summary

      // Do a quick filter to try and avoid reading useless tlogs (the precalculated data currently is missing some of the fields Lorenz requested)
      if (summary.minutes < minFlightMinutes) {
        println(s"Trivally skipping $tlog - too short")
        None
      } else {
        PlaybackModel.fromBytes(tlog).flatMap { model =>

          val duration = model.flightDuration.getOrElse(0.0)
          if (duration < minFlightMinutes * 60) {
            println(s"Skipping $tlog - too short")
            None
          } else {
            // Use view to do all this processing lazily - to bail as early as possible
            val posrecs = model.messages.view.flatMap {
              _.msg match {
                case m: msg_global_position_int => Some(m)
                case _ => None
              }
            }
            val allZzero = posrecs.forall(_.vz == 0.0)
            val someVx = posrecs.find(_.vx != 0.0).isDefined

            if (allZzero && someVx) {
              numMtk += 1
              Some(standardCols(tlog))
            } else {
              numOther += 1
              None
            }
          }
        }
      }
    }

    contentType = "text/csv"
    csvGenerator(generator)
    println(s"NUM MTK $numMtk vs $numOther")
  }

  /**
   * A quick hack to find all tlogs from PX4s with
   * at least one RAW_IMU message has abs(xacc), abs(yacc) and abs(zacc)
   * all greater than 20 m/s/s at the same time.
   * and INS_PRODUCT_ID==5 meaning it is a FMUv2
   */
  get("/report/px4fail.csv") {
    var numSuspect = 0
    var numOther = 0 // FIXME yuck - refactor to not need this vars - the generator model is wrong

    // We only care about logs after the Iris launch date (sept 1)
    val start = (new GregorianCalendar(2013, 8, 1)).getTime

    def generator(tlog: TLogChunk) = {
      // FIXME - refactor to have a new filterByFlightLength primitive...

      val summary = tlog.summary

      if (summary.startTime.before(start))
        None
      else
        PlaybackModel.fromBytes(tlog, false).flatMap { model =>

          val insProductId = model.parameters.find { p =>
            p.getId.getOrElse("") == "INS_PRODUCT_ID"
          }.flatMap {
            _.getInt
          }.getOrElse(-1)

          val isPx4v2 = insProductId == 5
          if (isPx4v2) {
            println(s"Found px4v2 in $tlog")

            def convAcc(a: Int) = { math.abs(a) * 0.001 * 9.81 }

            // Use view to do all this processing lazily - to bail as early as possible
            val highRec = model.messages.view.zipWithIndex.find {
              case (m, i) =>
                m.msg match {
                  case r: msg_raw_imu =>
                    convAcc(r.xacc) > 20 && convAcc(r.yacc) > 20 && convAcc(r.zacc) > 20
                  case _ =>
                    false
                }
            }

            if (highRec.isDefined) {
              println(s"Highrec $highRec in $tlog")
              numSuspect += 1
              Some(standardCols(tlog) :+ ("badRec" -> highRec.get))
            } else
              None
          } else {
            numOther += 1
            None
          }
        }
    }

    contentType = "text/csv"
    csvGenerator(generator)
    println(s"NUM Suspect $numSuspect vs $numOther")
  }

  /**
   * Find
   * SCALED_PRESSURE.temperature >= 5000 and abs(diff(SCALED_PRESSURE.temperature)) >= 100
   *
   * we're looking for cases where the temperature is above 50 degrees and
   * the temperature changes by 1 degree in a single sample, with a sample
   * rate of 2Hz or more (so 2 degrees/second).
   */
  get("/report/badtemps.csv") {
    var numFound = 0
    var numOther = 0 // FIXME yuck - refactor to not need this vars - the generator model is wrong

    def generator(tlog: TLogChunk) = {
      // FIXME - refactor to have a new filterByFlightLength primitive...

      val summary = tlog.summary

      // Do a quick filter to try and avoid reading useless tlogs (the precalculated data currently is missing some of the fields Lorenz requested)
      PlaybackModel.fromBytes(tlog).flatMap { model =>

        // Use view to do all this processing lazily - to bail as early as possible
        val temprecs = model.messages.view.flatMap {
          _.msg match {
            case m: msg_scaled_pressure => Some(m)
            case _ => None
          }
        }
        val pairs = temprecs.toSeq.sliding(2, 1)
        val suspect = pairs.find {
          case Seq(single) =>
            println("Ignoring last")
            false // Ignore the last entry
          case Seq(prev, cur) =>
            if (cur.temperature >= 5000) {
              println(s"Found high temp ${cur.temperature}")
              val diff = math.abs(cur.temperature - prev.temperature)
              if (diff >= 100) {
                println("Found big change")
                true
              } else
                false
            } else
              false
        }

        if (suspect.isDefined) {
          numFound += 1
          Some(standardCols(tlog))
        } else {
          numOther += 1
          None
        }
      }
    }

    contentType = "text/csv"
    csvGenerator(generator)
    println(s"NUM found $numFound vs $numOther")
  }
  /**
   * Our top level browse a flight page
   */
  get("/view/:id") {
    contentType = "text/html"
    val id = params("id")

    TLogChunkDAO.findOneById(id) match {
      case Some(tlog) =>
        scaml("tlogview",
          "tlog" -> tlog,
          "tlogbase" -> uriBase.resolve("/api/tlog/" + id).toString,
          // Use .gmaps.kmz if you want a limited URL
          // "mapsUrl" -> uriBase.resolve("/api/tlog/" + id + ".kmz").toString  
          "mapsUrl" -> publicUriBase.resolve("/api/tlog/" + id + ".gmaps.kmz").toString)
      case None =>
        scaml("failview",
          "message" -> "No tracklog found for ID '%s'".format(id))
    }
  }

  /**
   * Our list flights for a user page
   */
  get("/user/:id") {
    contentType = "text/html"
    val id = params("id")

    scaml("userview", "userId" -> id)
  }

  get("/recent") {
    contentType = "text/html"

    scaml("recentflights")
  }

  get("/upload.html") {
    contentType = "text/html"
    scaml("uploadview",
      "awsKey" -> S3Client.credentials.getAWSAccessKeyId,
      "s3pair" -> S3Client.s3Policy)
  }

  /*
  get("/testtemplate") {
    contentType = "text/html"

    // layoutTemplate("bob.ssp", "message" -> "Hello, World!")
    ssp("bob", "message" -> "Hello, World!")
  }
  */

  get("/testcoffee") {
    <html>
      <body>
        <h1>
          This is
          <a href="http://scalatra.org/2.2/guides/resources/coffeescript.html">resources/coffeescript</a>
          !
        </h1>
        <script type="text/javascript" src="compiled/scripts.js"></script>
      </body>
    </html>
  }
}
