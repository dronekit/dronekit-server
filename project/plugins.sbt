addSbtPlugin("com.mojolly.scalate" % "xsbt-scalate-generator" % "0.4.2")

addSbtPlugin("org.scalatra.sbt" % "scalatra-sbt" % "0.3.3")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.4.0")

resolvers += Resolver.url("sbt-plugin-snapshots",
  new URL("http://repo.scala-sbt.org/scalasbt/repo/"))(
    Resolver.ivyStylePatterns)

addSbtPlugin("com.bowlingx" %% "xsbt-wro4j-plugin" % "0.3.1")

// addSbtPlugin("com.github.casualjim" % "sbt-jelastic-deploy" % "0.1.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.10.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.10.2")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")

addSbtPlugin("com.github.sbt" %% "sbt-scalabuff" % "1.3.7")

// not yet working - addSbtPlugin("com.typesafe.sbt" % "sbt-atmos" % "0.3.2") // for akka debugging
