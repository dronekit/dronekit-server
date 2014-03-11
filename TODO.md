# TODO

* Apply tridge comments
* Investigate mavproxy internals
* Kill TCP actor when client disconnects
* create new flight log records as flight is received

# Medium term fixme

* Renable the REST api (fix swagger cruft first)
* Set column attributes (indexing etc) for tables: https://github.com/aselab/scala-activerecord/wiki/Using-Models
* Investigate optimistic vs pessimistic ActiveRecord locking

* Bail on client if his first message is not to log in
* Validate logins
* Validate access to vehicles
* Use sequence # to check for & report dropped packets

# Long term TODO

* Use SASL? http://docs.oracle.com/javase/1.5.0/docs/guide/security/sasl/sasl-refguide.html
* change mavlink parsing (MAVLinkReader) to use ByteStrings rather than byte arrays - to make for less heap allocs

# Discarded ideas

* Create two actor systems TrustedSystem and UntrustedSystem in server (per http://doc.akka.io/docs/akka/2.2.3/java/remoting.html ).  Only public API objects are in the untrusted system.
* Populate that system with a PublicVehicleAPI actor.  Initially all vehicles will call into this restricted actor.  Eventually a non akka based API
will use this object.

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
