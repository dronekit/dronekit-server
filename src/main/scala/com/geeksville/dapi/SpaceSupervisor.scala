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
 */
class SpaceSupervisor extends Actor with ActorLogging {
  val eventStream = new EventStream

  /**
   * The LiveVehicleActors we are monitoring
   */
  private val actorToMission = HashMap[ActorRef, Mission]()

  protected def publishEvent(a: Any) { eventStream.publish(a) }

  private def senderMission = actorToMission(sender).id
  private def senderVehicle = actorToMission(sender).vehicleId.get

  override def receive = {
    case Terminated(a) =>
      log.error(s"Unexpected death of a LiveVehicle, republishing...")
      context.unwatch(a)
      actorToMission.remove(a).foreach { m =>
        publishEvent(MissionStop(m))
      }

    case x: MissionStart =>
      log.debug(s"Received start on $x")
      actorToMission(sender) = x.mission
      context.watch(sender)
      publishEvent(x)

    case x: MissionStop =>
      log.debug(s"Received stop on $x")
      context.unwatch(sender)
      actorToMission.remove(sender)
      publishEvent(x)

    case l: Location =>
      log.debug(s"Received: $l")
      publishEvent(LocationUpdate(senderMission, senderVehicle, l))

    case StatusText(str, severe) =>
      log.debug(s"Received text: $str")
      publishEvent(TextUpdate(senderMission, senderVehicle, str))

    case MsgModeChanged(mode) =>
      log.debug(s"Received mode: $mode")
      publishEvent(ModeUpdate(senderMission, senderVehicle, mode))
  }
}

object SpaceSupervisor {

  /**
   * Find the supervisor responsible for a region of space
   *
   * FIXME - add grid identifer param
   */
  def find() = {

  }
}