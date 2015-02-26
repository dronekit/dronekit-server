package com.geeksville.dapi

import com.geeksville.akka.MockAkka
import akka.actor.Props
import com.geeksville.dapi.test.SimGCSClient
import com.geeksville.dapi.model.{Vehicle, User, Tables, Mission}
import com.geeksville.akka.AkkaReflector
import org.json4s.JsonAST.JInt
import scala.collection.mutable
import scala.concurrent.Await
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.xml.Elem
import org.scalatra.atmosphere._
import org.scalatra.swagger.SwaggerSupport
import org.scalatra.swagger.Swagger
import com.geeksville.dapi.test.PlaybackGCSClient
import org.scalatra.CorsSupport
import org.scalatra.AsyncResult
import _root_.akka.actor.{ActorRef, Actor, Props, ActorSystem}
import _root_.akka.util.Timeout
import _root_.akka.pattern.ask
import org.scalatra.FutureSupport
import org.scalatra.CorsSupport
import com.github.aselab.activerecord.dsl._


case class StatJson(numUsers: Long, numMissions: Long, numVehicles: Long,
                    longestMission: Double,
                    maxGS: Double,
                    maxAlt: Double,
                    vehicleTypes: scala.collection.Map[String, Int], apTypes: scala.collection.Map[String, Int])


/**
 * Special admin operations
 */
class AdminController(implicit val swagger: Swagger) extends DroneHubStack with CorsSupport with AtmosphereSupport with SwaggerSupport {

  // This override is necessary for the swagger docgen to make correct paths
  override protected val applicationName = Some("api/v1/admin")
  protected lazy val applicationDescription = s"Adminstrator API operations."

  lazy val akkaReflect = system.actorOf(Props(new AkkaReflector), "akkaReflect")

  def host() = multiParams("host").headOption.getOrElse("localhost")

  before() {
    requireAdmin()
    requireServiceAuth("admin")
  }

  private lazy val logOp = apiOperation[AtmosphereClient]("log") summary "An atmosphere endpoint container server log messages"
  atmosphere("/log", operation(logOp)) {
    try {
      new AdminLive(tryLogin())
    } catch {
      case ex: Exception =>
        haltUnauthorized(ex.getMessage)
    }
  }

  private lazy val simOp = apiOperation[String]("sim") summary "Simulate a flight"

  import akka.actor._
  import akka.pattern.ask

  post("/sim/huge", operation(simOp)) {
    val h = host
    lazy val simClient = system.actorOf(Props(new PlaybackGCSClient(h)))
    simClient ? PlaybackGCSClient.RunTest("bigtest")
  }

  post("/sim/full", operation(simOp)) {
    val h = host
    lazy val simClient = system.actorOf(Props(new PlaybackGCSClient(h)))
    simClient ? PlaybackGCSClient.RunTest("test")
  }

  post("/sim/std/:numVehicles/:numSecs", operation(simOp)) {
    // val keep = params("keep").toBoolean
    val numVehicles = params("numVehicles").toInt
    val numSecs = params("numSecs").toInt
    // val h = host
    val simClient = Global.simGCSClient
    simClient ? SimGCSClient.RunTest(numVehicles, numSecs)
  }

  post("/sim/std/stopAll", operation(simOp)) {
    val simClient = Global.simGCSClient
    simClient ? SimGCSClient.StopAllTests
  }

  /*
  // FIXME -very dangerous remove before production
  post("/db/reset", operation(apiOperation[String]("dbReset") summary "Reseed the DB")) {
    Tables.reset
    "DB Reset completed"
  }

  // Intentionallt not documented
  post("/db/create") {
    Tables.create
    "DB created"
  }
  *
  */

  post("/db/fix") {
    Mission.collection.foreach { m =>
      m.deleteIfUninteresting()
    }
  }

  get("/db/delete-duplicates") {
    val numdeleted = Vehicle.foldLeft(0) { (count, v) =>
      count + v.deleteWorstMissions()
    }
    warn(s"Total # missions to delete: $numdeleted")
    JInt(numdeleted)
  }

  /// To make testing newrelic integration easier
  get("/throw-exception") {
    throw new Exception("Test exception")
  }

  get("/statistics") {

    val vehicleTypes = new mutable.HashMap[String, Int]()
    val apTypes = new mutable.HashMap[String, Int]()
    Vehicle.foreach { v =>
      val vtype = v.vehicleType.getOrElse("unknown")
      vehicleTypes(vtype) = vehicleTypes.getOrElse(vtype, 0) + 1

      val aptype = v.autopilotType.getOrElse("unknown")
      apTypes(aptype) = apTypes.getOrElse(aptype, 0) + 1
    }

    var longestMission = 0.0
    var maxGS = 0.0
    var maxAlt = 0.0
    val wantMissionSummary = true
    if(wantMissionSummary)
      Mission.foreach { m =>
        m.regenSummary()
        longestMission = math.max(m.summary.flightDuration.getOrElse(0.0), longestMission)
        maxGS = math.max(m.summary.maxGroundSpeed, maxGS)
        maxAlt = math.max(m.summary.maxAlt, maxAlt)
      }

    val r = StatJson(User.size,
      Mission.size,
      Vehicle.size,
      longestMission,
      maxGS,
      maxAlt,
      vehicleTypes,
      apTypes
    )

    val json = toJSON(r)
    info("Returning: " + pretty(render(json)))
    json
  }

  get("/debugInfo", operation(apiOperation[String]("akka") summary "akka debugging information")) {
    val timeout = Timeout(5 seconds)

    akkaReflect ! AkkaReflector.PollMsg
    Thread.sleep(5000) // Super hackish way to give 5 secs for actors to reply with their ids

    akkaReflect ?(AkkaReflector.GetHtmlMsg, timeout)
  }
}
