import com.typesafe.sbt.SbtStartScript

scalaVersion in ThisBuild := "2.10.3" // To match version used by scala-ide

EclipseKeys.createSrc in ThisBuild := EclipseCreateSrc.Default + EclipseCreateSrc.Resource + EclipseCreateSrc.Managed // Include resources dir in eclipse classpath

EclipseKeys.withSource in ThisBuild := true // Try to include source for libs

EclipseKeys.relativizeLibs in ThisBuild := false // Doesn't seem to work for lib directory

libraryDependencies += "com.typesafe" % "config" % "1.0.2" withSources()

libraryDependencies += "org.simplex3d" %% "simplex3d-math-double" % "2.4.7" withSources()

libraryDependencies ++= Seq(
  "com.github.aselab" %% "scala-activerecord" % "0.2.3",
  "com.github.aselab" %% "scala-activerecord-scalatra" % "0.2.3",
  "com.h2database" % "h2" % "1.3.170"  // See Supported databases
)

// seq(jelasticSettings:_*)

// JelasticKeys.email in JelasticKeys.deploy := sys.env.get("JELASTIC_USERNAME").getOrElse(
// "kevinh@geeksville.com"
// )

// JelasticKeys.password in JelasticKeys.deploy := sys.env.get("JELASTIC_PWD").getOrElse(
// "jell4kat" // sys error "Please export JELASTIC_PWD in your shell!"
// )

//JelasticKeys.apiHoster := "app.jelastic.servint.net"

//JelasticKeys.environment in JelasticKeys.deploy := "nestor"

publishTo := None

//JelasticKeys.context in JelasticKeys.deploy := "ROOT"

//JelasticKeys.comment in JelasticKeys.deploy := "Kevin was here"

seq(SbtStartScript.startScriptForClassesSettings: _*)

net.virtualvoid.sbt.graph.Plugin.graphSettings




