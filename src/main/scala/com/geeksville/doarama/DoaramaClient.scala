package com.geeksville.doarama

import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.util.EntityUtils
import java.util.ArrayList
import org.apache.http.NameValuePair
import org.apache.http.message.BasicNameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.HttpHost
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import com.geeksville.util.Using._
import scala.collection.JavaConverters._
import org.apache.http.impl.client.DefaultHttpClient
import org.json4s.native.JsonMethods._
import org.json4s.JsonAST.JObject
import com.geeksville.http.HttpClient
import scala.xml.Node
import org.apache.http.entity.FileEntity
import org.json4s.JsonAST.JNumber
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.JsonAST.JObject
import grizzled.slf4j.Logging
import java.io.File
import org.apache.http.entity.StringEntity
import org.apache.http.client.methods.HttpRequestBase
import java.io.InputStream
import org.apache.http.entity.InputStreamEntity
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.InputStreamBody
import org.apache.http.entity.mime.content.AbstractContentBody
import org.apache.http.entity.mime.content.ByteArrayBody

object DoaramaClient {
  val monitor = true
}

class DoaramaClient(val userId: String)
  extends HttpClient(new HttpHost(if (DoaramaClient.monitor) "doarama-02278ec08110.my.apitools.com" else "www.doarama.com", 443, "https")) with Logging {

  private val apiName = "droneshare"
  private val apiKey = "***REMOVED***"

  private val baseUrl = s"/api/0.2/"
  val igcMimeType = "text/plain"

  private def addHeaders[T <: HttpRequestBase](transaction: T) = {
    transaction.addHeader("api-name", apiName)
    transaction.addHeader("api-key", apiKey)
    transaction.addHeader("user-id", userId)
    transaction.addHeader("Accept", "application/json")
    transaction
  }

  private def newPost(opcode: String) = addHeaders(new HttpPost(baseUrl + opcode))
  private def newGet(opcode: String) = addHeaders(new HttpGet(baseUrl + opcode))

  def uploadIGC(file: InputStream): Long = uploadIGC(new InputStreamBody(file, igcMimeType, "log.igc"))
  def uploadIGC(file: Array[Byte]): Long = uploadIGC(new ByteArrayBody(file, igcMimeType, "log.igc"))

  private def uploadIGC(file: AbstractContentBody): Long = {
    debug(s"Uploading IGC file")
    val transaction = newPost("activity")

    val entity = new MultipartEntity()
    entity.addPart("gps_track", file)
    transaction.setEntity(entity)

    val obj = callJson(transaction)
    debug(s"upload response: $obj")
    val r = (obj \ "id").asInstanceOf[JInt].num

    r.toLong
  }

  def setActivityInfo(activityId: Long) {
    // Per https://www.doarama.com/api/0.2/activityType
    // an activity type of 30 is drone

    val transaction = newPost(s"activity/$activityId")

    transaction.addHeader("Content-Type", "application/json")
    transaction.setEntity(new StringEntity("""{"activityTypeId":30}"""))

    val obj = call(transaction)
    debug(s"setActivityResponse: $obj")
  }

  def createVisualization(activityId: Long) = {

    val transaction = newPost(s"visualisation")

    transaction.addHeader("Content-Type", "application/json")
    transaction.setEntity(new StringEntity(s"""{"activityIds": [$activityId] }"""))

    val obj = callJson(transaction)
    debug(s"createVisualizationResponse: $obj")
    val r = (obj \ "id").asInstanceOf[JInt].num

    r.toLong
  }

  /// This bundles up all of the steps which must be done 'in advance' to make a visualization
  /// @return a visualization ID
  def createAnonymousVisualization(src: Array[Byte]) = {
    val activity = uploadIGC(src)

    setActivityInfo(activity)
    createVisualization(activity)
  }

  /**
   * Return a display URL
   * Client can later add &name=kevinh&avatar=http://www.gravatar.com/avatar/37d8c908a5a3ada380135b2621bf6f61.jpg
   * plus an encoded ?d=mm in the avatar URL to show avatar/username
   */
  def getDisplayURL(visId: Long) = {

    val transaction = newGet(s"visualisation/$visId/url")

    val obj = callJson(transaction)
    debug(s"getURL response: $obj")
    val r = (obj \ "url").asInstanceOf[JString].s

    r
  }
}