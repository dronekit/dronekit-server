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

  get("/report/parameters.csv") {
    val maxResults = 10000
    println("Reading parameters")

    // One row per tlog, each recors is a (tlog, list params as tuples)
    val tlogToParams = TLogChunkDAO.tlogsRecent(maxResults).flatMap { tlog =>
      println(s"Loading model for $tlog")
      try {
        val model = new PlaybackModel
        model.loadBytes(tlog.bytes)
        val params = model.parameters.flatMap { param =>
          for {
            id <- param.getId
            v <- param.getValue
          } yield {
            id -> v
          }
        }
        Some(tlog.summary -> params)
      } catch {
        case ex: Exception =>
          println(s"Skipping due to $ex")
          None
      }
    }

    println("Generating CSV")
    // CSV requires all column names to be known in advance, so merge all param names
    val allParamNames = new HashSet[String]
    tlogToParams.foreach {
      case (tlog, params) =>
        params.foreach {
          case (id, _) =>
            allParamNames += id
        }
    }
    val colNames = Seq("date", "vehicleType", "ownerId") ++ allParamNames

    val outStr = new StringBuilder
    val csvOut = new CSVWriter(outStr, colNames)

    tlogToParams.foreach {
      case (summary, params) =>
        val stdCols = Seq("date" -> DateUtil.isoDateFormat.format(summary.startTime),
          "vehicleType" -> summary.vehicleTypeGuess,
          "ownerId" -> summary.ownerGuess)
        csvOut.emit(stdCols ++ params: _*)
    }

    println("Returning CSV")
    contentType = "text/csv"
    outStr.toString

    val o = new PrintWriter(new FileOutputStream("/tmp/big.csv"))
    o.println(outStr.toString)
    o.close()
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
