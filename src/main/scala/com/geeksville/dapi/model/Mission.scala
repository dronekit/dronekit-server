package com.geeksville.dapi.model

import com.github.aselab.activerecord.Datestamps
import com.github.aselab.activerecord.annotations._
import org.squeryl.annotations.Transient
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import com.github.aselab.activerecord.dsl._
import com.geeksville.dapi.AccessCode

/**
 * A mission recorded from a vehicle
 */
case class Mission() extends DapiRecord {
  /**
   * What vehicle made me?
   */
  lazy val vehicle = belongsTo[Vehicle]
  val vehicleId: Option[Long] = None

  // As specified by the user
  var notes: Option[String] = None

  /**
   * Is the client currently uploading data to this mission
   */
  var isLive: Boolean = false

  @Transient
  var keep: Boolean = true

  var viewPrivacy: Int = AccessCode.DEFAULT_VALUE

  var controlPrivacy: Int = AccessCode.DEFAULT_VALUE

  /**
   * If the tlog is stored to s3 this is the ID
   */
  var tlogId: Option[String] = None

}

object Mission extends DapiRecordCompanion[Mission] {
  def create(vehicle: Vehicle) = {
    val r = Mission().create
    vehicle.missions += r
    r.save
    vehicle.save // FIXME - do I need to explicitly save?
    r
  }
}
