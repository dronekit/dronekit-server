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
import com.geeksville.mavlink.MsgArmChanged
import com.geeksville.mavlink.MsgSystemStatusChanged

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
class SpaceSupervisor extends Actor with ActorLogging {
  import context._

  private val eventStream = new EventStream

  private implicit val formats = DefaultFormats

  /**
   * The LiveVehicleActors we are monitoring
   */
  private val actorToMission = HashMap[ActorRef, Mission]()

  protected def publishEvent(a: Any) { eventStream.publish(a) }

  // private def senderMission = actorToMission(sender).id
  private def senderVehicle = actorToMission(sender).vehicleId.get

  private def withMission(cb: Long => Unit) {
    actorToMission.get(sender).map { m => cb(m.id) }.getOrElse { log.warning(s"Ignoring from $sender") }
  }

  /**
   * FIXME - not sure if I should be publishing directly to atmosphere in this actor, but for now...
   */
  private def updateAtmosphere(typ: String, o: JValue) {
    val route = "/api/v1/mission/live"
    AtmosphereTools.broadcast(route, typ, o)
  }

  private def publishUpdate(typ: String, o: Product) {
    log.debug(s"Publishing space $typ, $o")
    publishEvent(o) // Tell any interested subscribers
    val v = Extraction.decompose(o)
    updateAtmosphere(typ, v)
  }

  override def receive = {

    //
    // Messages from LiveVehicleActors appear below
    //

    case Terminated(a) =>
      log.error(s"Unexpected death of a LiveVehicle, republishing...")
      actorToMission.remove(a).foreach { m =>
        publishEvent(MissionStop(m))
      }

    case x: MissionStart =>
      log.debug(s"Received start on $x")
      actorToMission(sender) = x.mission
      watch(sender)
      publishEvent(x)

    case x: MissionStop =>
      log.debug(s"Received stop on $x")
      unwatch(sender)
      actorToMission.remove(sender)
      publishEvent(x)

    case l: Location =>
      log.debug(s"Received: $l")
      withMission { senderMission =>
        publishUpdate("loc", LocationUpdate(senderMission, senderVehicle, l))
      }

    case StatusText(str, severe) =>
      log.debug(s"Received text: $str")
      withMission { senderMission =>
        publishUpdate("text", TextUpdate(senderMission, senderVehicle, str))
      }

    case x: MsgArmChanged =>
      publishUpdate("arm", x)

    case x: MsgSystemStatusChanged =>
      publishUpdate("sysstat", x)

    case x: Product =>
      publishUpdate("mystery", x)

    case x: MAVLinkMessage =>
    // Silently ignore to prevent logspam BIG FIXME - should not even publish this to us...
  }
}

object SpaceSupervisor {
  private implicit def context: ActorRefFactory = MockAkka.system
  private val actors = new NamedActorClient("space")

  /**
   * Find the supervisor responsible for a region of space
   *
   * FIXME - add grid identifer param
   */
  def find(name: String = "world") = actors.getOrCreate(name, Props(new SpaceSupervisor))
}