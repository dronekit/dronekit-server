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

object MapboxClient {
  val monitor = true

  /// Generate an URL for a static map png
  def staticMapURL(latIn: Double, lonIn: Double, zoom: Integer, width: Integer, height: Integer, icon: String) = {
    // Four digits is pretty accurate and increases the chance of cache reuse
    val fmt = new DecimalFormat("##0.0000")
    val lat = fmt.format(latIn)
    val lon = fmt.format(lonIn)

    val mapBoxURL = s"http://mapbox-02278ec08110.my.apitools.com/v3/***REMOVED***/pin-s-$icon+f44($lon,$lat,$zoom)/$lon,$lat,$zoom/${width}x$height.png"

    mapBoxURL
  }
}

// http://api.tiles.mapbox.com/v3/examples.map-zr0njcqy/geocode/-73.989,40.733.json
// lon, lat

class MapboxClient(myDomain: String = "***REMOVED***")
  extends HttpClient(new HttpHost(if (MapboxClient.monitor) "mapbox-02278ec08110.my.apitools.com" else "api.tiles.mapbox.com"))
  with Logging {

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

    val transaction = new HttpGet(s"/v3/$myDomain/geocode/$lon,$lat.json")

    val json = callJson(transaction)
    val results = (json \ "results")(0).asInstanceOf[JArray]

    val r = results.arr.map { jobj =>
      val obj = jobj.asInstanceOf[JObject]
      val typ = (obj \ "type").asInstanceOf[JString].s
      val name = (obj \ "name").asInstanceOf[JString].s

      typ -> name
    }

    if (r.length == 0)
      error(s"Geocoding failed to find location for lat $lat, lon $lon: $json")

    r
  }

}