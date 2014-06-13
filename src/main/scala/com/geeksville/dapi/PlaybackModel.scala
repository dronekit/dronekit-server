package com.geeksville.dapi

import com.geeksville.flight.Waypoint
import com.geeksville.flight.WaypointsForMap
import com.geeksville.dapi.model.MissionSummary
import com.geeksville.flight.HasVehicleType
import com.geeksville.flight.Location
import com.geeksville.flight.ParametersReadOnlyModel
import grizzled.slf4j.Logging
import java.util.Calendar
import java.sql.Timestamp
import com.geeksville.flight.HasSummaryStats

/**
 * These are common methods that must be support by all mission model files (tlog, dataflash log etc...)
 */
trait PlaybackModel extends WaypointsForMap with HasVehicleType with HasSummaryStats with ParametersReadOnlyModel with Logging {

  private def checkTime(date: Timestamp) = {
    val calendar = Calendar.getInstance
    calendar.setTime(date)
    val y = calendar.get(Calendar.YEAR)

    if (y < 1975) {
      warn(s"Bogus timestamp in past: $date")
      None
    } else if (y > TLOGPlaybackModel.currentYear + 1) {
      warn("Bogus timestamp in future")
      None
    } else
      Some(date)
  }

  def summary = {
    // There is a problem of some uploads containing crap time ranges.  If encountered don't allow the summary to be created at all
    val start = startTime.flatMap { t => checkTime(new Timestamp(t / 1000)) }
    val end = currentTime.flatMap { t => checkTime(new Timestamp(t / 1000)) }
    warn(s"Creating NEW summary, start=$start, end=$end")

    MissionSummary(start,
      end,
      maxAltitude, maxGroundSpeed, maxAirSpeed, maxG, flightDuration,
      endPosition.map(_.lat), endPosition.map(_.lon),
      parameters.size,
      softwareVersion = buildVersion, softwareGit = buildGit)
  }

  /// timestamp usecs -> mode name
  def modeChanges: Seq[(Long, String)]

  def positions: Seq[TimestampedLocation]

  def waypoints: Seq[Waypoint]

  def parameters: Seq[ROParamValue]

  def startTime: Option[Long]

  def startPosition: Option[Location]
  def endPosition: Option[Location]
}

