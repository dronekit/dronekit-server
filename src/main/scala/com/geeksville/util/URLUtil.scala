package com.geeksville.util

import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.message.BasicNameValuePair
import java.net.URI
import scala.collection.JavaConverters._

object URLUtil {
  /// Camelcase convert a string, capitalizing first char
  def capitalize(s: String) = s.head.toUpper.toString + s.tail

  /// Add a query string to an URL
  def addQueryString(url: String, params: (String, String)*) = {
    val nvps = params.map {
      case (key, v) =>
        new BasicNameValuePair(key, v)
    }.toList.asJava

    val encoded = URLEncodedUtils.format(nvps, "utf-8")
    url + "?" + encoded
  }

  def parseQueryString(url: String) = {
    val uri = URI.create(url)
    val params = URLEncodedUtils.parse(uri, "utf-8").asScala

    val r = Map(params.map { p => (p.getName, p.getValue) }: _*)
    //println(s"***** $uri " + r.mkString(","))
    r
  }
}
