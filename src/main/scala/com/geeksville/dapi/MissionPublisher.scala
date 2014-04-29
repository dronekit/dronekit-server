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

// Only SpaceSupervisor sends the following messages
case class SpaceEnvelope(missionId: Long, payload: Option[Product])
case class SpaceSummary(vehicle: Option[Vehicle], mission: Mission)

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
