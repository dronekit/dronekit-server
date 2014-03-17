package com.geeksville.dapi.model

import com.github.aselab.activerecord.Datestamps
import com.github.aselab.activerecord.annotations._
import org.squeryl.annotations.Transient
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import com.github.aselab.activerecord.dsl._

/**
 * A mission recorded from a vehicle
 */
case class Mission(name: String) extends DapiRecord {
  /**
   * What vehicle made me?
   */
  lazy val vehicle = belongsTo[Vehicle]
  val vehicleId: Option[Long] = None
}

object Mission extends DapiRecordCompanion[Mission]
