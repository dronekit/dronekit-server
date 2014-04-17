# TODO

* Investigate mavproxy internals
* Use reference counting for some actors

# Medium term fixme

* Exceptions in GCSActor should not cause restart of the actor
* Renable the REST api (fix swagger cruft first)
* Set column attributes (indexing etc) for tables: https://github.com/aselab/scala-activerecord/wiki/Using-Models
* Investigate optimistic vs pessimistic ActiveRecord locking

* Bail on client if his first message is not to log in
* Validate logins
* Validate access to vehicles
* Use sequence # to check for & report dropped packets

# Long term TODO

* Do geo queries? http://www.scribd.com/doc/2569355/Geo-Distance-Search-with-MySQL
* Add indexes for columns we need indexed
* Remove well known DB psw from application.conf
* Lower the mysql privs granted to the dapi mysql user
* Use SASL? http://docs.oracle.com/javase/1.5.0/docs/guide/security/sasl/sasl-refguide.html
* change mavlink parsing (MAVLinkReader) to use ByteStrings rather than byte arrays - to make for less heap allocs

# Discarded ideas

* Create two actor systems TrustedSystem and UntrustedSystem in server (per http://doc.akka.io/docs/akka/2.2.3/java/remoting.html ).  Only public API objects are in the untrusted system.
* Populate that system with a PublicVehicleAPI actor.  Initially all vehicles will call into this restricted actor.  Eventually a non akka based API
will use this object.

# setup mailgun

curl -s --user 'api:***REMOVED***' \
    https://api.mailgun.net/v2/sandbox91d351510d0a440882ecfaa1c65be642.mailgun.org/messages \
    -F from='Mailgun Sandbox <postmaster@sandbox91d351510d0a440882ecfaa1c65be642.mailgun.org>' \
    -F to='Kevin Hester <kevin@3drobotics.com>'\
    -F subject='Hello Kevin Hester' \
    -F text='Congratulations Kevin Hester, you just sent an email with Mailgun!  You are truly awesome! You can see a record of this email in your logs: https://mailgun.com/cp/log You can send up to 300 emails/day from this sandbox server. Next, you should add your own domain so you can send 10,000 emails/month for free.'

public static ClientResponse SendSimpleMessage() {
    Client client = Client.create();
    client.addFilter(new HTTPBasicAuthFilter("api",
                "***REMOVED***"));
    WebResource webResource =
        client.resource("https://api.mailgun.net/v2/sandbox91d351510d0a440882ecfaa1c65be642.mailgun.org/messages");
    MultivaluedMapImpl formData = new MultivaluedMapImpl();
    formData.add("from", "Mailgun Sandbox <postmaster@sandbox91d351510d0a440882ecfaa1c65be642.mailgun.org>");
    formData.add("to", "Kevin Hester <kevin@3drobotics.com>");
    formData.add("subject", "Hello Kevin Hester");
    formData.add("text", "Congratulations Kevin Hester, you just sent an email with Mailgun!  You are truly awesome!  You can see a record of this email in your logs: https://mailgun.com/cp/log .  You can send up to 300 emails/day from this sandbox server.  Next, you should add your own domain so you can send 10,000 emails/month for free.");
    return webResource.type(MediaType.APPLICATION_FORM_URLENCODED).
                                                post(ClientResponse.class, formData);
}

expected response

{
  "message": "Queued. Thank you.",
  "id": "<20140416171814.3136.15870@sandbox91d351510d0a440882ecfaa1c65be642.mailgun.org>"
}

# Check in swagger fix:

fails here:
  def findScalaSig(clazz: Class[_]): Option[ScalaSig] =
    parseClassFileFromByteCode(clazz).orElse(findScalaSig(clazz.getDeclaringClass))

java.lang.NullPointerException: null
	at org.scalatra.swagger.reflect.ScalaSigReader$$anonfun$findScalaSig$1.apply(ScalaSigReader.scala:133) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.ScalaSigReader$$anonfun$findScalaSig$1.apply(ScalaSigReader.scala:133) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at scala.Option.orElse(Option.scala:257) ~[scala-library-2.10.3.jar:0.13.1]
	at org.scalatra.swagger.reflect.ScalaSigReader$.findScalaSig(ScalaSigReader.scala:133) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.ScalaSigReader$$anonfun$findScalaSig$1.apply(ScalaSigReader.scala:133) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.ScalaSigReader$$anonfun$findScalaSig$1.apply(ScalaSigReader.scala:133) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at scala.Option.orElse(Option.scala:257) ~[scala-library-2.10.3.jar:0.13.1]
	at org.scalatra.swagger.reflect.ScalaSigReader$.findScalaSig(ScalaSigReader.scala:133) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.ScalaSigReader$.findClass(ScalaSigReader.scala:41) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.ScalaSigReader$.org$scalatra$swagger$reflect$ScalaSigReader$$read$1(ScalaSigReader.scala:35) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.ScalaSigReader$$anonfun$org$scalatra$swagger$reflect$ScalaSigReader$$read$1$1.apply(ScalaSigReader.scala:35) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.ScalaSigReader$$anonfun$org$scalatra$swagger$reflect$ScalaSigReader$$read$1$1.apply(ScalaSigReader.scala:35) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at scala.Option.getOrElse(Option.scala:120) ~[scala-library-2.10.3.jar:0.13.1]
	at org.scalatra.swagger.reflect.ScalaSigReader$.org$scalatra$swagger$reflect$ScalaSigReader$$read$1(ScalaSigReader.scala:35) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.ScalaSigReader$.readField(ScalaSigReader.scala:37) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.Reflector$$anonfun$2.apply(Reflector.scala:92) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.Reflector$$anonfun$2.apply(Reflector.scala:91) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at scala.collection.TraversableLike$$anonfun$map$1.apply(TraversableLike.scala:244) ~[scala-library-2.10.3.jar:0.13.1]
	at scala.collection.TraversableLike$$anonfun$map$1.apply(TraversableLike.scala:244) ~[scala-library-2.10.3.jar:0.13.1]
	at scala.collection.mutable.ResizableArray$class.foreach(ResizableArray.scala:59) ~[scala-library-2.10.3.jar:0.13.1]
	at scala.collection.mutable.ArrayBuffer.foreach(ArrayBuffer.scala:47) ~[scala-library-2.10.3.jar:0.13.1]
	at scala.collection.TraversableLike$class.map(TraversableLike.scala:244) ~[scala-library-2.10.3.jar:0.13.1]
	at scala.collection.AbstractTraversable.map(Traversable.scala:105) ~[scala-library-2.10.3.jar:0.13.1]
	at org.scalatra.swagger.reflect.Reflector$.fields$1(Reflector.scala:91) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.Reflector$.fields$1(Reflector.scala:102) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.Reflector$.fields$1(Reflector.scala:102) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.Reflector$.properties$1(Reflector.scala:105) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.Reflector$.createDescriptor(Reflector.scala:159) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.Reflector$$anonfun$describe$2.apply(Reflector.scala:44) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.Reflector$$anonfun$describe$2.apply(Reflector.scala:44) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.package$Memo.apply(package.scala:16) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.reflect.Reflector$.describe(Reflector.scala:44) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.Swagger$.collectModels(Swagger.scala:51) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.Swagger$.collectModels(Swagger.scala:40) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.swagger.SwaggerSupportSyntax$class.registerModel(SwaggerSupport.scala:351) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at com.geeksville.dapi.ApiController.registerModel(ApiController.scala:10) ~[classes/:na]
	at org.scalatra.swagger.SwaggerSupport$class.apiOperation(SwaggerSupport.scala:464) ~[scalatra-swagger_2.10-2.2.2.jar:2.2.2]
	at com.geeksville.dapi.ApiController.apiOperation(ApiController.scala:10) ~[classes/:na]
	at com.geeksville.dapi.ApiController.getOp(ApiController.scala:86) ~[classes/:na]
	at com.geeksville.dapi.ApiController.<init>(ApiController.scala:94) ~[classes/:na]
	at com.geeksville.dapi.VehicleController.<init>(VehicleController.scala:11) ~[classes/:na]
	at ScalatraBootstrap.init(ScalatraBootstrap.scala:50) ~[classes/:na]
	at org.scalatra.servlet.ScalatraListener.configureCycleClass(ScalatraListener.scala:67) ~[scalatra_2.10-2.2.2.jar:2.2.2]
	at org.scalatra.servlet.ScalatraListener.contextInitialized(ScalatraListener.scala:23) ~[scalatra_2.10-2.2.2.jar:2.2.2]
	at org.eclipse.jetty.server.handler.ContextHandler.callContextInitialized(ContextHandler.java:771) [jetty-server-8.1.8.v20121106.jar:8.1.8.v20121106]
	at org.eclipse.jetty.servlet.ServletContextHandler.callContextInitialized(ServletContextHandler.java:424) [jetty-servlet-8.1.8.v20121106.jar:8.1.8.v20121106]
	at org.eclipse.jetty.server.handler.ContextHandler.startContext(ContextHandler.java:763) [jetty-server-8.1.8.v20121106.jar:8.1.8.v20121106]
	at org.eclipse.jetty.servlet.ServletContextHandler.startContext(ServletContextHandler.java:249) [jetty-servlet-8.1.8.v20121106.jar:8.1.8.v20121106]
	at org.eclipse.jetty.webapp.WebAppContext.startContext(WebAppContext.java:1250) [jetty-webapp-8.1.8.v20121106.jar:8.1.8.v20121106]
	...
	at com.geeksville.scalatra.JettyLauncher.main(JettyLauncher.scala) [classes/:na]
	...
