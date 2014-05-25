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
  val maxStoppedMissions = 20

  /// We keep the last N messages from each vehicle (for reply to new clients)
  /// 20 is too many - it swamps slow web app startup - perhaps we can revisit once FE has been optimized
  val maxRecordsPerVehicle = 5

  private case class AtmosphereUpdate(typ: String, payload: JValue)

  private val recentMissions = new RingBuffer[MissionHistory](maxStoppedMissions)

  // Send live and then ended flights
  private def allMissions = actorToMission.values ++ recentMissions

  /**
   * This is the state we keep for each vehicle connection.
   */
  private class MissionHistory(val missionId: Long) {
    private var vehicle: Option[Vehicle] = None
    private var mission: Option[Mission] = None
    private val history = new RingBuffer[AtmosphereUpdate](maxRecordsPerVehicle)

    def numMessages = history.size

    def setSummary(m: SpaceSummary) {
      mission = Some(m.mission)
      vehicle = m.vehicle
    }

    def addStop(mopt: Option[SpaceSummary]) {
      mopt.foreach { m =>
        // Update with latest data
        mission = Some(m.mission)
        vehicle = m.vehicle
      }
      recentMissions += this // We will be getting removed from our collection soon
    }

    def addUpdate(u: AtmosphereUpdate) {
      history += u
    }

    /// Get a suitable set of update messages which are suitable to send to a client
    def updates = {
      // Always include a start msg if we can
      val summary = mission.map { m =>
        AtmosphereUpdate("start", Extraction.decompose(SpaceSummary(vehicle, m)))
      }

      summary ++ history
    }
  }

  /**
   * The LiveVehicleActors we are monitoring
   */
  private val actorToMission = HashMap[ActorRef, MissionHistory]()

  protected def publishEvent(a: Any) { eventStream.publish(a) }

  // private def senderMission = actorToMission(sender).id
  // private def senderVehicle = actorToMission(sender).vehicleId.get

  private def withMission(s: ActorRef)(cb: MissionHistory => Unit) {
    actorToMission.get(s).map { m => cb(m) }.getOrElse {
      log.warning(s"Ignoring from $s")
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

  private def publishUpdate(typ: String, p: Product = null, preferredSender: ActorRef = sender) {
    withMission(preferredSender) { senderMission =>

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
  }

  private def handleStop(aref: ActorRef, mopt: Option[Mission]) {
    publishUpdate("stop", preferredSender = aref)

    // Move the mission to recent flights
    val summary = mopt.map { mission => SpaceSummary(mission.vehicle, mission) }
    actorToMission.remove(aref).foreach { info =>
      info.addStop(summary)
    }
  }

  def receive = {

    //
    // Messages from REST endpoints appear below
    //

    case SendToAtmosphereMessage(dest) =>
      log.info(s"Resending to new client $dest")

      allMissions.foreach { info =>
        //log.debug(s"Resending from $info")
        info.updates.foreach { u =>
          //log.debug(s"Resending $u")
          AtmosphereTools.sendTo(dest, u.typ, u.payload)
        }
      }

    //
    // Messages from LiveVehicleActors appear below
    //

    case Terminated(a) =>
      log.error(s"Unexpected death of a LiveVehicle, republishing...")
      handleStop(a, None)

    case MissionStart(mission) =>
      log.debug(s"Received start of $mission from $sender")
      val info = new MissionHistory(mission.id)
      val summary = SpaceSummary(mission.vehicle, mission)
      info.setSummary(summary)
      actorToMission(sender) = info
      watch(sender)
      publishUpdate("start", summary)

    case MissionUpdate(mission) =>
      // log.debug(s"Applying mission update $mission")
      val history = actorToMission(sender)
      val summary = SpaceSummary(mission.vehicle, mission)
      history.setSummary(summary)
      publishUpdate("update", summary)

    case MissionStop(mission) =>
      log.debug(s"Received stop of $mission")
      unwatch(sender)
      handleStop(sender, Some(mission))

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
  case class SendToAtmosphereMessage(dest: AtmosphereLive)

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