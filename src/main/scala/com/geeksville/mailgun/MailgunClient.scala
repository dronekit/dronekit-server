package com.geeksville.mailgun

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

object MailgunClient {
  val monitor = true
}

class MailgunClient(myDomain: String = "droneshare.com")
  extends HttpClient(new HttpHost(if (MailgunClient.monitor) "***REMOVED***.my.apitools.com" else "api.mailgun.net", 443, "https")) {

  httpclient.getCredentialsProvider.setCredentials(
    new AuthScope(httpHost.getHostName(), httpHost.getPort()),
    new UsernamePasswordCredentials("api", "***REMOVED***"));

  def send(pairs: (String, String)*): JObject = {
    val transaction = new HttpPost(s"/v2/$myDomain/messages")

    // The underlying HTTP connection is still held by the response object
    // to allow the response content to be streamed directly from the network socket.
    // In order to ensure correct deallocation of system resources
    // the user MUST either fully consume the response content  or abort request
    // execution by calling CloseableHttpResponse#close().

    val nvps = pairs.map {
      case (key, v) =>
        new BasicNameValuePair(key, v)
    }.toList.asJava

    transaction.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

    callJson(transaction)
  }

  def sendText(from: String, to: String, subject: String, bodyText: String, tag: String = "default", testing: Boolean = false) = {
    var options = Seq("from" -> from, "to" -> to, "subject" -> subject, "text" -> bodyText, "o:tag" -> tag)

    if (testing)
      options = options :+ ("o:testmode" -> "true")

    send(options: _*)
  }

  /**
   * Send an html formatted email (text version will be auto generated)
   */
  def sendHtml(from: String, to: String, subject: String, bodyHtml: Node, tag: String = "default", testing: Boolean = false) = {
    val body = bodyHtml.toString
    println("Sending email: " + body)
    var options = Seq("from" -> from, "to" -> to, "subject" -> subject, "html" -> body, "o:tag" -> tag)

    if (testing)
      options = options :+ ("o:testmode" -> "true")

    send(options: _*)
  }

  /*
public static ClientResponse SendSimpleMessage() {
       Client client = Client.create();
       client.addFilter(new HTTPBasicAuthFilter("api",
                       "***REMOVED***"));
       WebResource webResource =
               client.resource("https://api.mailgun.net/v2/samples.mailgun.org" +
                               "/messages");
       MultivaluedMapImpl formData = new MultivaluedMapImpl();
       formData.add("from", "Excited User <me@samples.mailgun.org>");
       formData.add("to", "bar@example.com");
       formData.add("to", "baz@example.com");
       formData.add("subject", "Hello");
       formData.add("text", "Testing some Mailgun awesomness!");
       return webResource.type(MediaType.APPLICATION_FORM_URLENCODED).
               post(ClientResponse.class, formData);
}
* 
*/
}