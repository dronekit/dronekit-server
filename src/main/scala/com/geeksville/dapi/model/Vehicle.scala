package com.geeksville.dapi.model

import com.github.aselab.activerecord.ActiveRecord
import com.github.aselab.activerecord.ActiveRecordCompanion
import com.github.aselab.activerecord.Datestamps
import com.github.aselab.activerecord.annotations._
import org.squeryl.annotations.Transient
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import com.github.aselab.activerecord.dsl._

/**
 * Behavior common to all Dapi records
 */
abstract class DapiRecord extends ActiveRecord with Datestamps

trait DapiRecordCompanion[T <: ActiveRecord] extends ActiveRecordCompanion[T] {
}

/**
 * A vehicle model
 *
 * @param uuid is selected by the client on first connection
 */
case class Vehicle(@Required @Unique uuid: UUID) extends DapiRecord {
  /**
   * Who owns me?
   */
  lazy val user = belongsTo[User]
  val userId: Option[Long] = None

  /**
   * A user specified name for this vehicle (i.e. my bixler)
   */
  var name: String = ""

  /**
   * All the missions this vehicle has made
   */
  lazy val vehicles = hasMany[Mission]
}

object Vehicle extends DapiRecordCompanion[Vehicle] {
  /**
   * Find by ID but using a string encoding (i.e. UUID or somesuch)
   * For now I just convert the int to its base-10 representation
   */
  def find(id: UUID): Option[Vehicle] = this.where(_.uuid === id).headOption
}

case class User(@Required @Unique login: String, email: Option[String], fullName: Option[String]) extends DapiRecord {
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
  def find(id: String): Option[User] = this.where(_.login === id).headOption
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