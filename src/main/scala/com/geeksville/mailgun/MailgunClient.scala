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
import com.ridemission.rest.JObject

class MailgunClient(myDomain: String = "sandbox91d351510d0a440882ecfaa1c65be642.mailgun.org") {
  private val httpclient = new DefaultHttpClient()
  // val myhttps = new Protocol("https", new MySSLSocketFactory(), 443);

  private val targetHost = new HttpHost("api.mailgun.net", 443, "https");
  httpclient.getCredentialsProvider.setCredentials(
    new AuthScope(targetHost.getHostName(), targetHost.getPort()),
    new UsernamePasswordCredentials("api", "***REMOVED***"));

  def close() {
    httpclient.getConnectionManager().shutdown()
  }

  def send(pairs: (String, String)*): JObject = {
    val transaction = new HttpPost(s"/v2/$myDomain/messages")
    try {
      // The underlying HTTP connection is still held by the response object
      // to allow the response content to be streamed directly from the network socket.
      // In order to ensure correct deallocation of system resources
      // the user MUST either fully consume the response content  or abort request
      // execution by calling CloseableHttpResponse#close().

      val nvps = pairs.map {
        case (key, v) =>
          new BasicNameValuePair(key, v)
      }.toList.asJava

      transaction.setEntity(new UrlEncodedFormEntity(nvps));

      val response = httpclient.execute(targetHost, transaction)

      System.out.println(response.getStatusLine())
      val entity = response.getEntity()
      // do something useful with the response body
      // and ensure it is fully consumed
      val msg = EntityUtils.toString(entity)
      EntityUtils.consume(entity)
      System.out.println(msg)
      parse(msg).asInstanceOf[JObject]
    } finally {
      transaction.releaseConnection()
    }
  }

  def sendTo(from: String, to: String, subject: String, bodyText: String, tag: String = "default") =
    send("from" -> from, "to" -> to, "subject" -> subject, "text" -> bodyText, "o:tag" -> tag)

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