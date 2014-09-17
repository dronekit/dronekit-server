package com.geeksville.dapi

import com.geeksville.dapi.model.Mission
import com.geeksville.flight.Location
import akka.actor.Actor
import akka.actor.ActorLogging
import com.geeksville.akka.EventStream
import com.geeksville.flight.StatusText
import com.geeksville.flight.MsgModeChanged
import akka.actor.ActorRef
import scala.collection.mutable.HashMap
import akka.actor.Terminated
import org.scalatra.atmosphere._
import org.json4s.DefaultFormats
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import com.geeksville.akka.NamedActorClient
import akka.actor.Props
import com.geeksville.akka.MockAkka
import akka.actor.ActorContext
import akka.actor.ActorRefFactory
import com.geeksville.scalatra.AtmosphereTools
import org.json4s.Extraction
import org.mavlink.messages.MAVLinkMessage
import org.mavlink.messages.ardupilotmega.msg_attitude
import com.geeksville.mavlink.MsgArmChanged
import com.geeksville.mavlink.MsgSystemStatusChanged
import com.github.aselab.activerecord.dsl._
import com.geeksville.dapi.model.Vehicle
import com.geeksville.dapi.model.MissionSummary
import com.geeksville.flight.MsgRcChannelsChanged
import com.geeksville.flight.MsgServoOutputChanged
import com.geeksville.flight.MsgSysStatusChanged
import com.geeksville.util.Throttled
import com.geeksville.akka.DebuggableActor
import com.geeksville.util.RingBuffer
import com.geeksville.json.GeeksvilleFormats
import com.geeksville.dapi.model.DroneModelFormats
import com.geeksville.scalatra.ScalatraTools
import com.geeksville.dapi.model.User
import com.geeksville.util.AnalyticsService
import com.geeksville.util.ThreadTools._

/// for json encoding
private case class Attitude(roll: Double, pitch: Double, yaw: Double)

/**
 * This actor is responsible for keeping a model of current and recent flights in its region of space.
 *
 * Initially I'm only making one instance of this actor, but as usage grows we will create numerous instances - where
 * each instance is responsible for a particular region of space (plus one top level supervisor that provides a planet
 * wide summary.
 *
 * It is important that this class collect enough state so that it can quickly serve up API queries from MDS (or similar)
 * without requiring any database operations.  When the server first boots this cache/model is initially empty (though
 * we could seed it by making a utility that does a query on missions and pushes out a bunch of state updates).
 *
 * LiveVehicleActors send messages to this actor, and the REST atmosphere reader stuff listens for publishes from this actor.
 *
 * FIXME - make different atmosphere endpoints for different regions - pair each endpoint with the SpaceSupervisor
 * responsible for that region.  For the time being I just use one region for the whole planet
 */
class SpaceSupervisor extends DebuggableActor with ActorLogging {
  import context._
  import SpaceSupervisor._

  private val msgLogThrottle = new Throttled(5 * 60 * 1000)

  private val eventStream = new EventStream

  private implicit val formats = DefaultFormats ++ GeeksvilleFormats ++ DroneModelFormats

  // FIXME - we need to add a periodic MissionUpdate message.  Also we need to only keep the
  // last maxVehicles arround
  /// We only show the most recent X vehicles in our region
  //val maxLiveMissions = 20

  // How many recent missions should we keep
  val maxStoppedMissions = 50

  /// We keep the last N messages from each vehicle (for reply to new clients)
  /// 20 is too many - it swamps slow web app startup - perhaps we can revisit once FE has been optimized
  val maxRecordsPerVehicle = 20

  private case class AtmosphereUpdate(typ: String, payload: JValue)

  private val recentMissions = new RingBuffer[MissionHistory](maxStoppedMissions)

  /**
   * This is the state we keep for each vehicle connection.
   */
  private class MissionHistory(val missionId: Long) {
    var mission: Option[Mission] = None
    private val history = new RingBuffer[AtmosphereUpdate](maxRecordsPerVehicle)

    def numMessages = history.size

    def setSummary(m: Mission) {
      mission = Some(m)
    }

    def addStop(mopt: Option[Mission]) {
      mopt.foreach { m =>
        // Update with latest data
        mission = Some(m)
      }
      recentMissions += this // We will be getting removed from our collection soon
    }

    def addUpdate(u: AtmosphereUpdate) {
      history += u
    }

    /// Get a suitable set of update messages which are suitable to send to a client
    def updates(u: Option[User]): Iterable[AtmosphereUpdate] = {
      // If we don't have a start - don't send anything
      mission.map { m =>
        val start = Seq(AtmosphereUpdate("start", Extraction.decompose(SpaceEnvelope(m.id, Option(m)))))

        // If user doesn't have read access to this mission hide it from what we provide to the client
        if (m.isReadAccessAllowed(u, false))
          (start ++ history)
        else
          Iterable.empty

      }.getOrElse(Iterable.empty)
    }
  }

  // Do an initial fetch of the most recent missions on disk
  initSpace()

  /**
   * The LiveVehicleActors we are monitoring
   */
  private val actorToMission = HashMap[ActorRef, MissionHistory]()

  // Send live and then ended flights 
  private def allMissions = actorToMission.values ++ recentMissions

  protected def publishEvent(a: Any) { eventStream.publish(a) }

  // private def senderMission = actorToMission(sender).id
  // private def senderVehicle = actorToMission(sender).vehicleId.get

  private def withMission(s: ActorRef)(cb: MissionHistory => Unit) {
    actorToMission.get(s).map { m => cb(m) }.getOrElse {
      log.warning(s"Ignoring from $s")
    }
  }

  def initSpace() {
    import com.geeksville.dapi.test.SimGCSClient.loginName

    Mission.collection.orderBy(_.createdAt desc).limit(20).foreach { mission =>
      if (!mission.cleanupOrphan()) {
        log.info(s"Seeding space with $mission")
        val info = new MissionHistory(mission.id)
        info.addStop(Some(mission)) // This is a kinda yucky way to do this - it implicitly adds to recent missions
      }
    }
  }

  override def toString = {
    val numLive = actorToMission.size
    val numRecent = recentMissions.size
    val nummsg = allMissions.map(_.numMessages).reduceOption(_ + _).getOrElse(0)
    s"SpaceSupervisor: $numLive live, $numRecent recent, $nummsg messages"
  }

  /**
   * FIXME - not sure if I should be publishing directly to atmosphere in this actor, but for now...
   */
  private def updateAtmosphere(typ: String, o: JValue) {
    if (!ScalatraTools.isTesting) {
      val route = "/api/v1/mission/live"
      AtmosphereTools.broadcast(route, typ, o)
    }
  }

  /**
   * @param pIn is the payload to send, if not specified we will send the mission object
   */
  private def publishMission(typ: String, senderMission: MissionHistory, pIn: Product = null) {
    val p = if (pIn == null)
      senderMission.mission
    else
      pIn

    //log.debug(s"Publishing $typ")
    msgLogThrottle.withIgnoreCount { numIgnored: Int =>
      log.debug(s"Published space $typ, $p (and $numIgnored others)")
    }

    val o = SpaceEnvelope(senderMission.missionId, Option(p))
    publishEvent(o) // Tell any interested subscribers
    val v = Extraction.decompose(o)
    senderMission.addUpdate(AtmosphereUpdate(typ, v))
    //log.debug(s"To client: " + v)
    updateAtmosphere(typ, v)
  }

  private def publishUpdate(typ: String, p: Product = null, preferredSender: ActorRef = sender) {
    withMission(preferredSender) { senderMission =>
      publishMission(typ, senderMission, p)
    }
  }

  private def handleStop(aref: ActorRef, mopt: Option[Mission]) {
    publishUpdate("stop", preferredSender = aref)

    // Move the mission to recent flights
    val summary = mopt
    actorToMission.remove(aref).foreach { info =>
      info.addStop(summary)
    }
  }

  /**
   * Someone deleted a mission - make sure to scrub it from space, and notify any clients which might be showing it
   */
  private def handleDelete(id: Long) {
    log.debug(s"Perhaps deleting $id")
    val indexToDelete = recentMissions.zipWithIndex.find {
      case (history, index) =>
        (history.missionId == id)
    }.map(_._2)

    indexToDelete.foreach { i =>
      val history = recentMissions.remove(i)
      log.debug(s"Publishing delete $id")
      publishMission("delete", history)
    }
  }

  def sendMissionSummary(typ: String, mission: Mission) = {
    publishUpdate(typ, mission)
    mission
  }

  private def handleSendAll(dest: AtmosphereLive, user: Option[User]) {
    log.info(s"Resending to new client $dest")

    // BEFORE sending other starts the client expects any "user" flight to be sent
    // Also send the most recent flight for this user (if possible)
    val latestFlight = user.flatMap(_.newestMission)
    latestFlight.foreach { mission =>
      log.debug(s"Send user's flight $mission")

      // FIXME this copypasta is nasty
      val o = SpaceEnvelope(mission.id, Option(mission))
      AtmosphereTools.sendTo(dest, "user", Extraction.decompose(o))
    }

    val missions = allMissions
    log.debug(s"Sending ${missions.size} old missions to atmosphere client")
    missions.foreach { info =>
      //log.debug(s"Resending from $info")
      catchIgnore {
        info.updates(user).foreach { u =>
          //log.debug(s"Resending $u")
          AtmosphereTools.sendTo(dest, u.typ, u.payload)
        }
      }
    }
  }

  private def getInitialJSON(user: Option[User]) = {
    val all = try {
      log.info(s"Getting initial JSON $user")

      // BEFORE sending other starts the client expects any "user" flight to be sent
      // Also send the most recent flight for this user (if possible)
      val latestFlight = user.flatMap(_.newestMission)
      val usersJson = latestFlight.map { mission =>
        log.debug(s"Send user's flight $mission")

        // FIXME this copypasta is nasty
        val o = SpaceEnvelope(mission.id, Option(mission))
        Extraction.decompose(o)
      }

      val missions = allMissions
      log.debug(s"Getting ${missions.size} old missions in initial JSON")
      val othersJson = missions.flatMap { info =>
        //log.debug(s"Resending from $info")

        // We might throw while parsing these - return what we can
        catchOrElse(Iterable.empty: Iterable[JValue]) {
          info.updates(user).map { u =>
            //log.debug(s"Resending $u")
            u.payload
          }
        }
      }

      usersJson.toList ++ othersJson
    } catch {
      case ex: Exception =>
        AnalyticsService.reportException(s"Failed sending old atmosphere messages to $user", ex)
        List.empty
    }

    JObject("updates" -> JArray(all))
  }

  def receive = {

    //
    // Messages from REST endpoints appear below
    //

    case SendToAtmosphereMessage(dest, user) =>
      try {
        handleSendAll(dest, user)
      } catch {
        case ex: Exception =>
          AnalyticsService.reportException(s"Failed sending old atmosphere messages to $dest", ex)
      }

    case GetInitialJSON(user) =>
      val r = getInitialJSON(user)
      log.debug(s"Initial JSON sent")
      sender ! r

    //
    // Messages from LiveVehicleActors appear below
    //

    case Terminated(a) =>
      log.error(s"Unexpected death of a LiveVehicle, republishing...")
      handleStop(a, None)

    case MissionStart(mission) =>
      log.debug(s"Received start of $mission from $sender")
      val info = new MissionHistory(mission.id)
      actorToMission(sender) = info
      info.setSummary(sendMissionSummary("start", mission))
      watch(sender)

    case MissionUpdate(mission) =>
      // log.debug(s"Applying mission update $mission")
      val history = actorToMission(sender)
      history.setSummary(sendMissionSummary("update", mission))

    case MissionStop(mission) =>
      log.debug(s"Received stop of $mission")
      unwatch(sender)
      handleStop(sender, Some(mission))

    case MissionDelete(missionId) =>
      handleDelete(missionId)

    case l: Location =>
      publishUpdate("loc", l)

    case l: StatusText =>
      publishUpdate("text", l)

    case l: MsgArmChanged =>
      publishUpdate("arm", l)

    case l: MsgModeChanged =>
      publishUpdate("mode", l)

    case l: MsgSystemStatusChanged =>
      publishUpdate("sysstat", l)

    case MsgSysStatusChanged =>
    case MsgRcChannelsChanged =>
    case x: MsgServoOutputChanged =>
    // Silently ignore to prevent logspam BIG FIXME - should not even publish this to us...

    // This catches our debug msg stuff if we don't filter our check to only listen to expected senders
    case x: Product if actorToMission.contains(sender) =>
      publishUpdate("mystery", x)

    case x: msg_attitude =>
      import com.geeksville.util.MathTools._
      val att = Attitude(toDeg(x.pitch), toDeg(x.yaw), toDeg(x.roll))
      publishUpdate("att", att)

    case x: MAVLinkMessage =>
    // Silently ignore to prevent logspam BIG FIXME - should not even publish this to us...
  }
}

object SpaceSupervisor {
  private implicit def context: ActorRefFactory = MockAkka.system
  private val actors = new NamedActorClient("space")

  /// Resend any old messages to this new client
  case class SendToAtmosphereMessage(dest: AtmosphereLive, user: Option[User])

  /// Return the initial JSON that shows the entire contents of space (used for non atomosphere based map views)
  case class GetInitialJSON(user: Option[User])

  // Most space notifications come due to subscribing to particlar live vehicles
  // However, if someone manually uploads a tlog via the REST api, we want to consider that
  // a new mission as well.
  def tellMission(space: ActorRef, m: Mission) {
    space ! MissionStart(m)
    space ! MissionStop(m)
  }

  /**
   * Find the supervisor responsible for a region of space
   *
   * FIXME - add grid identifer param
   */
  def find(name: String = "world") = actors.getOrCreate(name, Props(new SpaceSupervisor))
}