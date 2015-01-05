package com.geeksville.http

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
import org.apache.http.client.methods.HttpRequestBase
import scala.xml._
import org.apache.http.client.utils.URLEncodedUtils

/**
 * Standard client side glue for talking to HTTP services
 * Currently based on apache, but could use spray instead
 */
class HttpClient(val httpHost: HttpHost) {
  protected val httpclient = new DefaultHttpClient()
  // val myhttps = new Protocol("https", new MySSLSocketFactory(), 443);

  def close() {
    httpclient.getConnectionManager().shutdown()
  }

  def call(transaction: HttpRequestBase) = synchronized {
    try {
      val response = httpclient.execute(httpHost, transaction)

      val entity = response.getEntity()

      val msg = EntityUtils.toString(entity)
      EntityUtils.consume(entity)

      if (response.getStatusLine.getStatusCode != 200)
        throw new Exception("httpclient failure: " + response.getStatusLine())

      msg
    } finally {
      transaction.releaseConnection()
    }
  }

  /// Call something with a JSON response
  def callJson(transaction: HttpRequestBase) = {
    val msg = call(transaction)
    parse(msg).asInstanceOf[JObject]
  }

  /// Call something with XML response
  def callXml(transaction: HttpRequestBase) = {
    val msg = call(transaction)
    XML.loadString(msg)
  }
}

object HttpClient {
  /// Add a query string to an URL
  def addQueryString(url: String, params: (String, String)*) = {
    val nvps = params.map {
      case (key, v) =>
        new BasicNameValuePair(key, v)
    }.toList.asJava

    val encoded = URLEncodedUtils.format(nvps, "utf-8");
    url + "?" + encoded
  }
}
