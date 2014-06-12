package com.geeksville.dapi

import com.geeksville.dapi.model.Mission
import com.geeksville.flight.Location
import akka.actor.Actor
import com.geeksville.akka.EventStream
import com.geeksville.mavlink.MsgSystemStatusChanged
import akka.actor.ActorRef
import com.geeksville.dapi.model.Vehicle
import com.geeksville.dapi.model.MissionSummary

// Note: we only send full mission objects at start and end because they might be large and expensive to send over RPC
case class MissionStart(mission: Mission)
case class MissionStop(mission: Mission)

// Tell the space supervisor that a mission has been deleted
case class MissionDelete(missionId: Long)

// Every few minutes we will send an update - so that summary stats are available to newly connected browsers
case class MissionUpdate(mission: Mission)

// Only SpaceSupervisor sends the following messages
case class SpaceEnvelope(missionId: Long, payload: Option[Product])

/**
 * The following messages are published by vehicles (not space supervisor)
 *
 * case class MsgHeartbeatLost(id: Int)
 * case class MsgHeartbeatFound(id: Int)
 * case class MsgArmChanged(armed: Boolean)
 * //case class MsgSystemStatusChanged(stat: Option[Int])
 *
 * The following (throttled) messages are published by VehicleModel and should be received by SpaceSupervisor
 * StatusText
 * MsgSysStatusChanged
 * MsgModeChanged
 * Location
 *
 */
