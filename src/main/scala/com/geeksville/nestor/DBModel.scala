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

import com.novus.salat._
import com.novus.salat.global._
import com.novus.salat.annotations._
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO
import java.util.Date
import java.io.ByteArrayInputStream
import com.geeksville.util.Using._
import com.geeksville.util.FileTools
import com.geeksville.util.CacheUtil._
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.cache.Cache
import scala.util.Random
import com.geeksville.util.Base32
import com.google.common.io.ByteStreams
import java.text.SimpleDateFormat

case class UserRecord(@Key("_id") id: String, password: String, email: Option[String])

object UserRecordDAO extends SalatDAO[UserRecord, String](DBClient.db("user"))

/* To gen a db object
val dbo = grater[Company].asDBObject(company)

To convert back
val company_* = grater[Company].asObject(dbo)
* 
* To use DAO http://repo.novus.com/salat-presentation/#11
*/

case class MongoLoc(x: Double, y: Double)

/**
 * Stats which cover an entire flight (may span multiple tlog chunks)
 */
case class MissionSummary(startTime: Date,
  endTime: Date,
  maxAlt: Double = 0.0,
  maxGroundSpeed: Double = 0.0,
  maxAirSpeed: Double = 0.0,
  maxG: Double = 0.0,
  vehicleType: String = "",
  autopilotType: String = "",
  gcsType: String = "",
  ownerId: String = "",
  flightDuration: Option[Double] = None) {

  /// A short title sutable for sharing on G+ or facebook
  def titleString = {

    if (ownerId.isEmpty)
      "A droneshare flight"
    else
      ownerId + "'s " + vehicleTypeGuess + " flight"
  }

  def ownerGuess = if (ownerId.isEmpty)
    "anon"
  else
    ownerId

  def vehicleTypeGuess = if (vehicleType.isEmpty)
    "drone"
  else
    vehicleType

  def minutes = (endTime.getTime - startTime.getTime) / 1000.0 / 60

  /// A detailed description for facebook
  def descriptionString = {
    val fmt = new SimpleDateFormat("MMMMM d")
    val date = fmt.format(startTime)

    val name = if (ownerId.isEmpty)
      "a droneshare pilot"
    else
      ownerId

    val minutes = (endTime.getTime - startTime.getTime) / 1000 / 60

    """On %s, %s flew their %s for %s minutes""".format(date, name, vehicleTypeGuess, minutes.toLong)
  }
}

/**
 * One tlog chunk
 * FIXME - currently we assume it is an entire tlog file
 * FIXME - put the tlog raw data into its own table - just use chunk for metadata (do this after we preprocess into kmz)
 */
case class TLogChunk(startTime: Date,
  endTime: Date,
  startLoc: MongoLoc, // We keep location on each chunk so we can do quick map summary views
  endLoc: MongoLoc,
  numRecords: Int,
  summary: MissionSummary, // FIXME - eventually move to is own table
  @Key("_id") id: String) {

  /**
   * Fetch our bytes from S3 the first time they are needed
   */
  lazy val bytes: Option[Array[Byte]] = TLogChunk.getBytesByKey(id)
}

object TLogChunk {
  val mimeType = "application/vnd.nestor.tlog"

  val inUsePrefix = "in-use/"

  private val random = new Random(System.currentTimeMillis)
  private val bytesCache = CacheBuilder.newBuilder.maximumSize(1).build { (key: String) => readBytesByPath(inUsePrefix + key) }

  /**
   * Allocate a _unique_ tlog chunk id
   */
  private def allocateId() = {
    // FIXME - check for collisions
    // FIXME - someday switch to 64 bits, for now I keep the strings short
    Base32.encode(random.nextInt.toLong & 0xffffffffL)
  }

  def openInputStream(id: String) = {
    println("Asking S3 for " + id)
    S3Client.downloadStream(id)
  }

  private def readBytesByPath(id: String) = {
    using(openInputStream(id)) { s =>
      println("Reading bytes from S3")
      val r = ByteStreams.toByteArray(s)
      println("Done reading S3 bytes")
      r
    }
  }

  def gcHack() {
    //println("Doing skanky GC hack")
    //System.gc()
  }

  /**
   * Get bytes from the cache or S3
   */
  def getBytesByKey(id: String) = {
    gcHack()
    Option(bytesCache.getUnchecked(id))
  }

  /// Uploads are in a temp directory, don't use the cache
  def getUploadByPath(id: String) = readBytesByPath(id)

  /**
   * Create a chunk but use S3 for storage
   */
  def apply(startTime: Date,
    endTime: Date,
    startLoc: MongoLoc,
    endLoc: MongoLoc,
    numRecords: Int,
    summary: MissionSummary): TLogChunk = {
    val id = allocateId

    gcHack()

    // We now assume the file is already in S3
    //val fromBytes = new ByteArrayInputStream(bytes)
    //S3Client.uploadStream(inUsePrefix + id, fromBytes, mimeType: String, bytes.size)

    TLogChunk(startTime, endTime, startLoc, endLoc, numRecords, summary, id)
  }
}

object TLogChunkDAO extends SalatDAO[TLogChunk, String](DBClient.db("tlog")) {
  /**
   * Try to find all flights for a particular user name (use "anon" for the unknown user)
   */
  def tlogsForUser(userId: String, maxResults: Int) = {
    val mongoId = if (userId == "anon")
      ""
    else
      userId

    println("Looking for " + mongoId)

    // Show newest flights first
    val r = find(ref = MongoDBObject("summary.ownerId" -> mongoId))
      .sort(orderBy = MongoDBObject("startTime" -> -1))
      .limit(maxResults)
      .toList

    // println("found: " + r.mkString("\n"))
    r
  }

  def tlogsRecent(maxResults: Int): TraversableOnce[TLogChunk] = {
    println("Looking for recent")

    // Show newest flights first
    val r = find(ref = MongoDBObject())
      .sort(orderBy = MongoDBObject("startTime" -> -1))
      .limit(maxResults)

    // println("found: " + r.mkString("\n"))
    r
  }
}
