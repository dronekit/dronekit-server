package com.geeksville.mapbox

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
import scala.collection.JavaConverters._
import org.apache.http.impl.client.DefaultHttpClient
import org.json4s.native.JsonMethods._
import org.json4s.JsonAST.JObject
import com.geeksville.http.HttpClient
import org.json4s._
import grizzled.slf4j.Logging
import java.text.DecimalFormat
import com.geeksville.akka.MockAkka

object MapboxClient {
  val myDomain: String = MockAkka.config.getString("mapbox.domain")
  val myAccessToken: String = MockAkka.config.getString("mapbox.accessToken")

  val baseUrl = "api.tiles.mapbox.com"

  def withAccessToken(s: String) = s"$s?access_token=$myAccessToken"

  /// Generate an URL for a static map png
  def staticMapURL(latIn: Double, lonIn: Double, zoom: Integer, width: Integer, height: Integer, icon: String) = {
    // Four digits is pretty accurate and increases the chance of cache reuse
    val fmt = new DecimalFormat("##0.0000")
    val lat = fmt.format(latIn)
    val lon = fmt.format(lonIn)

    // http://api.tiles.mapbox.com/v4/{mapid}/{lon},{lat},{z}/{width}x{height}.{format}?access_token=<your access token>
    // http://api.tiles.mapbox.com/v4/{mapid}/{overlay}/auto/{width}x{height}.{format}?access_token=<your access token>
    val mapBoxURL = withAccessToken(s"http://$baseUrl/v4/$myDomain/pin-s-$icon+f44($lon,$lat,$zoom)/$lon,$lat,$zoom/${width}x$height.png")

    mapBoxURL
  }
}

// http://api.tiles.mapbox.com/v3/examples.map-zr0njcqy/geocode/-73.989,40.733.json
// lon, lat

class MapboxClient()
  extends HttpClient(new HttpHost(MapboxClient.baseUrl))
  with Logging {

  // Pull appart ids of the form state.23233
  private val IDRegex = "(.*)\\.(.*)".r

  /**
   * Returns a sequenct of pairs of the following form:
   * 02:37.320 INFO  com.geeksville.mapbox.MapboxTests  - Mapbox says: (street,Ala Moana Blvd)
   * 02:37.322 INFO  com.geeksville.mapbox.MapboxTests  - Mapbox says: (city,Honolulu)
   * 02:37.322 INFO  com.geeksville.mapbox.MapboxTests  - Mapbox says: (province,Hawaii)
   * 02:37.322 INFO  com.geeksville.mapbox.MapboxTests  - Mapbox says: (country,United States)
   *
   */
  def geocode(latIn: Double, lonIn: Double): Seq[(String, String)] = {
    // Four digits is pretty accurate and increases the chance of cache reuse
    val fmt = new DecimalFormat("##0.0000")
    val lat = fmt.format(latIn)
    val lon = fmt.format(lonIn)

    val transaction = new HttpGet(MapboxClient.withAccessToken(s"/v4/geocode/mapbox.places/$lon,$lat.json"))

    val json = callJson(transaction)
    val features = (json \ "features").asInstanceOf[JArray]

    if (features.arr.size == 0) {
      error(s"Geocoding failed to find features for lat $lat, lon $lon: $json")
      Seq.empty
    }
    else {
      val results = features.arr(0) // best match is first
      val contexts = (results \ "context").asInstanceOf[JArray]

      val r = contexts.arr.map { jobj =>
        val obj = jobj.asInstanceOf[JObject]
        val idfull = (obj \ "id").asInstanceOf[JString].s
        val name = (obj \ "text").asInstanceOf[JString].s

        val IDRegex(typ, id) = idfull
        typ -> name
      }

      if (r.length == 0)
        error(s"Geocoding failed to find location for lat $lat, lon $lon: $json")

      r
    }
  }

}
