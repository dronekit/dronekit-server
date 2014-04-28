package com.geeksville.dapi

import com.geeksville.akka.MockAkka
import akka.actor.Props
import com.geeksville.dapi.test.SimGCSClient
import com.geeksville.dapi.temp.NestorImporter
import com.geeksville.dapi.temp.DoImport
import com.geeksville.dapi.model.Tables
import com.geeksville.akka.AkkaReflector
import scala.concurrent.Await
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.xml.Elem
import org.scalatra.atmosphere._
import org.scalatra.swagger.SwaggerSupport
import org.scalatra.swagger.Swagger
import com.geeksville.dapi.test.PlaybackGCSClient

/**
 * Special admin operations
 */
class AdminController(implicit val swagger: Swagger) extends DroneHubStack with AtmosphereSupport with SwaggerSupport {

  // This override is necessary for the swagger docgen to make correct paths
  override protected val applicationName = Some("api/v1/admin")
  protected lazy val applicationDescription = s"Adminstrator API operations."

  lazy val system = MockAkka.system

  lazy val nestorImport = system.actorOf(Props(new NestorImporter), "importer")

  lazy val akkaReflect = system.actorOf(Props(new AkkaReflector), "akkaReflect")

  def host() = multiParams("host").headOption.getOrElse("localhost")

  before() {
    requireAdmin()
    requireServiceAuth("admin")
  }

  private lazy val logOp = apiOperation[AtmosphereClient]("log") summary "An atmosphere endpoint container server log messages"
  atmosphere("/log", operation(logOp)) {
    new AdminLive(tryLogin())
  }

  private lazy val importOp =
    (apiOperation[String]("import")
      summary "Migrate flights from the old droneshare"
      parameters (
        pathParam[Int]("count").description(s"Number of old flights to import")))

  get("/import/:count", operation(importOp)) {
    nestorImport ! DoImport(params("count").toInt)
    "started import"
  }

  private lazy val simOp = apiOperation[String]("sim") summary "Simulate a flight"

  get("/sim/huge", operation(simOp)) {
    val h = host
    lazy val simClient = system.actorOf(Props(new PlaybackGCSClient(h)))
    simClient ! PlaybackGCSClient.RunTest("bigtest")
    "started sim"
  }

  get("/sim/full", operation(simOp)) {
    val h = host
    lazy val simClient = system.actorOf(Props(new PlaybackGCSClient(h)))
    simClient ! PlaybackGCSClient.RunTest("test")
    "started sim"
  }

  get("/sim/std/:keep/:numVehicles/:numSecs", operation(simOp)) {
    val keep = params("keep").toBoolean
    val numVehicles = params("numVehicles").toInt
    val numSecs = params("numSecs").toInt
    val h = host
    lazy val simClient = system.actorOf(Props(new SimGCSClient(h, keep)))
    simClient ! SimGCSClient.RunTest(numVehicles, numSecs)
    "started sim"
  }

  // FIXME -very dangerous remove before production
  get("/db/reset", operation(apiOperation[String]("dbReset") summary "Reseed the DB")) {
    Tables.reset
    "DB Reset completed"
  }

  // Intentionallt not documented
  get("/db/create") {
    Tables.create
    "DB created"
  }

  get("/debugInfo", operation(apiOperation[String]("akka") summary "akka debugging information")) {
    implicit val timeout = Timeout(5 seconds)

    akkaReflect ! AkkaReflector.PollMsg
    Thread.sleep(5000) // Super hackish way to give 5 secs for actors to reply with their ids

    val future = akkaReflect ? AkkaReflector.GetHtmlMsg
    Await.result(future, timeout.duration).asInstanceOf[Elem]
  }
}