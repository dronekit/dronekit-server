package com.geeksville.dapi.model

import com.github.aselab.activerecord.ActiveRecord
import com.github.aselab.activerecord.ActiveRecordCompanion
import com.github.aselab.activerecord.Datestamps
import com.github.aselab.activerecord.annotations._
import org.squeryl.annotations.Transient
import org.mindrot.jbcrypt.BCrypt

/**
 * Behavior common to all Dapi records
 */
abstract class DapiRecord extends ActiveRecord with Datestamps

trait DapiRecordCompanion[T <: ActiveRecord] extends ActiveRecordCompanion[T] {
  /**
   * Find by ID but using a string encoding (i.e. UUID or somesuch)
   * For now I just convert the int to its base-10 representation
   */
  def find(id: String): Option[T] = find(id.toLong)

}

/**
 * A vehicle model
 *
 * @param name a user specified name for the vehicle (i.e. My Bixler)
 */
case class Vehicle(name: String) extends DapiRecord {
  /**
   * Who owns me?
   */
  lazy val user = belongsTo[User]
  val userId: Option[Long] = None

  /**
   * All the missions this vehicle has made
   */
  lazy val vehicles = hasMany[Mission]
}

object Vehicle extends DapiRecordCompanion[Vehicle]

case class User(@Required fullName: String, @Required @Unique login: String) extends DapiRecord {
  @Transient
  @Length(min = 8, max = 30)
  var password: String = _

  var hashedPassword: String = _

  /**
   * All the vehicles this user owns
   */
  lazy val vehicles = hasMany[Vehicle]

  def isPasswordGood(test: String) = BCrypt.checkpw(password, hashedPassword)

  override def beforeSave() {
    hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
  }
}

object User extends DapiRecordCompanion[User] {
}

case class Location(lat: Double, lon: Double, alt: Double)
case class Attitude(pitch: Double, yaw: Double, roll: Double)

/**
 * A mission recorded from a vehicle
 */
case class Mission(name: String) extends DapiRecord {
  /**
   * What vehicle made me?
   */
  lazy val owner = belongsTo[Vehicle]
}

object Mission extends DapiRecordCompanion[Mission]

// FIXME - unify with real model
case class Mavlink(time: Long, id: Int, payload: List[Byte])