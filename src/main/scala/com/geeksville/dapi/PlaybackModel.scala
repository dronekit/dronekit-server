package com.geeksville.dapi

import com.geeksville.flight.Waypoint
import com.geeksville.flight.WaypointsForMap
import com.geeksville.dapi.model.MissionSummary
import com.geeksville.flight.HasVehicleType
import com.geeksville.flight.Location
import com.geeksville.flight.ParametersReadOnlyModel

/**
 * These are common methods that must be support by all mission model files (tlog, dataflash log etc...)
 */
trait PlaybackModel extends WaypointsForMap with HasVehicleType with ParametersReadOnlyModel {

  def summary: MissionSummary

  /// timestamp usecs -> mode name
  def modeChanges: Seq[(Long, String)]

  def positions: Seq[TimestampedLocation]

  def waypoints: Seq[Waypoint]

  def parameters: Seq[ROParamValue]

  def startTime: Option[Long]

  def startPosition: Option[Location]
  def endPosition: Option[Location]
}

