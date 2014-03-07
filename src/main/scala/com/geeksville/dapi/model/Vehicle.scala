package com.geeksville.dapi.model

import com.github.aselab.activerecord.ActiveRecord
import com.github.aselab.activerecord.ActiveRecordCompanion
import com.github.aselab.activerecord.Datestamps

/**
 * Behavior common to all Dapi records
 */
abstract class DapiRecord extends ActiveRecord with Datestamps

/**
 * A vehicle model
 *
 * @param name a user specified name for the vehicle (i.e. My Bixler)
 */
case class Vehicle(name: String) // extends DapiRecord

object Vehicle {} // extends ActiveRecordCompanion[Vehicle]

case class Location(lat: Double, lon: Double, alt: Double)
case class Attitude(pitch: Double, yaw: Double, roll: Double)

/**
 * A mission recorded from a vehicle
 */
case class Mission(name: String)

// FIXME - unify with real model
case class Mavlink(time: Long, id: Int, payload: List[Byte])