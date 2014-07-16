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
import java.io.OutputStream
import com.geeksville.flight.IGCWriter
import com.geeksville.util.Using
import com.geeksville.mavlink.TimestampedAbstractMessage
import com.geeksville.mavlink.AbstractMessage

/**
 * These are common methods that must be support by all mission model files (tlog, dataflash log etc...)
 */
trait PlaybackModel extends WaypointsForMap with HasVehicleType with HasSummaryStats with ParametersReadOnlyModel with Logging {

  // FIXME -unify with tlog messages
  var abstractMessages: Seq[TimestampedAbstractMessage] = Seq.empty

  /// A JSON readable string showing model type: TLOG, Dataflash
  def modelType: String

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

  final def summary = {
    // There is a problem of some uploads containing crap time ranges.  If encountered don't allow the summary to be created at all
    val start = startTime.flatMap { t => checkTime(new Timestamp(t / 1000)) }
    val end = currentTime.flatMap { t => checkTime(new Timestamp(t / 1000)) }
    val duration = flightDuration
    warn(s"Creating NEW summary, start=$start, end=$end, duration=$duration")

    MissionSummary(start,
      end,
      maxAltitude, maxGroundSpeed, maxAirSpeed, maxG, duration,
      endPosition.map(_.lat), endPosition.map(_.lon),
      parameters.size,
      softwareVersion = buildVersion, softwareGit = buildGit)
  }

  /// timestamp usecs -> mode name
  def modeChanges: Seq[(Long, String)]

  def positions: Seq[TimestampedLocation]

  def waypoints: Seq[Waypoint]

  def parameters: Iterable[ROParamValue]

  /**
   * Generate an IGC file representation of this model
   */
  def toIGC(out: OutputStream) {
    debug(s"Emitting IGC for $this")

    // val pilotName: String, val gliderType: String, val pilotId: String
    Using.using(new IGCWriter(out, "pilotName", humanVehicleType + "/" + humanAutopilotType, "pilotId")) { writer =>
      positions.foreach { p =>
        writer.emitPosition(p.loc, p.time)
      }
    }
  }
}

