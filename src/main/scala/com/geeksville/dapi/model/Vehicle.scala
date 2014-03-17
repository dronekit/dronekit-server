package com.geeksville.dapi.model

import com.github.aselab.activerecord.Datestamps
import com.github.aselab.activerecord.annotations._
import org.squeryl.annotations.Transient
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import com.github.aselab.activerecord.dsl._

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