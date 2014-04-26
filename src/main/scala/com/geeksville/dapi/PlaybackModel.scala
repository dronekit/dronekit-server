package com.geeksville.dapi

import com.geeksville.mavlink.TimestampedMessage
import org.mavlink.messages.ardupilotmega.msg_global_position_int
import scala.collection.mutable.ArrayBuffer
import java.io.File
import com.geeksville.flight.VehicleSimulator
import java.io.ByteArrayOutputStream
import com.geeksville.mavlink.BinaryMavlinkReader
import java.io.ByteArrayInputStream
import com.geeksville.flight.Location

import org.mavlink.messages.ardupilotmega.msg_gps_raw_int
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.net.URLEncoder
import com.geeksville.mavlink.DataReducer
import org.mavlink.messages.ardupilotmega.msg_mission_item
import com.geeksville.flight.Waypoint
import com.geeksville.flight.WaypointsForMap
import java.net.URI

import com.geeksville.flight.ParametersReadOnlyModel
import org.mavlink.messages.ardupilotmega.msg_heartbeat
import org.mavlink.messages.ardupilotmega.msg_param_value
import org.mavlink.messages.ardupilotmega.msg_vfr_hud

import org.mavlink.messages.MAV_TYPE
import com.geeksville.dapi.model.MissionSummary
import java.util.Date
import java.util.Calendar
import grizzled.slf4j.Logging
import org.json4s.JsonAST.JObject
import com.geeksville.json.GeoJSON
import com.geeksville.flight.LiveOrPlaybackModel

case class TimestampedLocation(time: Long, loc: Location)

/**
 * Models the state of N vehicles, built up by playing back a series of provided records.
 *
 * Can be used on one TLogChunk or a series of chunks.
 * DO NOT DEPEND on any web services in this class (will move to common someday?)
 */
class PlaybackModel extends WaypointsForMap with LiveOrPlaybackModel with ParametersReadOnlyModel with Logging {
  import LiveOrPlaybackModel._

  /**
   * a seq of usec_time -> location
   */
  val positions = ArrayBuffer[TimestampedLocation]()

  /// First found position
  var startPosition: Option[Location] = None
  var endPosition: Option[Location] = None

  var messages: scala.collection.Seq[TimestampedMessage] = Seq[TimestampedMessage]()
  var modeChangeMsgs = Seq[TimestampedMessage]()

  var numMessages = 0

  /// A MAV_TYPE vehicle code
  var vehicleType: Option[Int] = None
  var autopilotType: Option[Int] = None

  var maxG = 0.0
  var gcsType = "TBD"

  private val waypointOpt = ArrayBuffer[Option[Waypoint]]()

  /// All the waypoints we've seen
  def waypoints = waypointOpt.flatten.toSeq

  val parameters = ArrayBuffer[ROParamValue]()

  // Currently I only use GPS pos, because we don't properly adjust alt offsets (it seems like m.alt is not corrected for MSL)
  val useGlobalPosition = false

  /// Just the messages that happened while the vehicle was actively flying
  def inFlightMessages: Traversable[TimestampedMessage] = (for {
    s <- startOfFlightTime
    e <- endOfFlightTime
  } yield {
    messages.filter { m => m.time >= s && m.time <= e }
  }).getOrElse(Seq())

  private def checkTime(date: Date) = {
    val calendar = Calendar.getInstance
    calendar.setTime(date)
    val y = calendar.get(Calendar.YEAR)

    if (y < 1975) {
      warn(s"Bogus timestamp in past: $date")
      None
    } else if (y > PlaybackModel.currentYear + 1) {
      warn("Bogus timestamp in future")
      None
    } else
      Some(date)
  }

  def summary = {
    // There is a problem of some uploads containing crap time ranges.  If encountered don't allow the summary to be created at all
    MissionSummary(startTime.flatMap { t => checkTime(new Date(t / 1000)) },
      currentTime.flatMap { t => checkTime(new Date(t / 1000)) },
      maxAltitude, maxGroundSpeed, maxAirSpeed, maxG, flightDuration,
      endPosition.map(_.lat), endPosition.map(_.lon), softwareVersion = buildVersion, softwareGit = buildGit)
  }

  def modeChanges = modeChangeMsgs.map { m =>
    val code = m.msg.asInstanceOf[msg_heartbeat].custom_mode.toInt
    //println("mapping mode code " + m.msg)
    m.time -> modeToString(code)
  }

  private def addPosition(raw: TimestampedMessage, l: Location) {
    if (l.lat != 0 && l.lon != 0) {
      if (!startPosition.isDefined)
        startPosition = Some(l)
      endPosition = Some(l)
      positions.append(TimestampedLocation(raw.time, l))
      l.alt.foreach { a => maxAltitude = math.max(maxAltitude, a) }
    }
  }

  private def loadMessage(raw: TimestampedMessage) {
    numMessages += 1

    // First update any standard live/delayed model stuff
    val msg = raw.msg
    if (updateModel.isDefinedAt(msg))
      updateModel.apply(msg)

    // Update useful summary information as we read
    msg match {
      case m: msg_global_position_int if useGlobalPosition =>
        addPosition(raw, VehicleSimulator.decodePosition(m))
      case m: msg_gps_raw_int =>
        VehicleSimulator.decodePosition(m).foreach { l => addPosition(raw, l) }

      case m: msg_mission_item =>
        val wp = Waypoint(m)
        // We fill any missing positions with None
        while (waypointOpt.size < wp.seq + 1)
          waypointOpt.append(None)
        waypointOpt(wp.seq) = Some(wp)
      case msg: msg_heartbeat =>
        val typ = msg.`type`
        // We don't care about the heartbeats from the GCS
        if (typ != MAV_TYPE.MAV_TYPE_GCS) {
          vehicleType = Some(typ)
          autopilotType = Some(msg.autopilot)
          if (modeChangeMsgs.isEmpty || modeChangeMsgs.last.msg.asInstanceOf[msg_heartbeat].custom_mode != msg.custom_mode)
            modeChangeMsgs = modeChangeMsgs :+ raw
        }
      case msg: msg_param_value =>
        // We fill any missing positions with None
        while (parameters.size < msg.param_index + 1)
          parameters.append(new ROParamValue)
        parameters(msg.param_index).raw = Some(msg)

      case _ =>
    }

  }

  /**
   * Load a series of messages
   */
  def loadMessages(m: scala.collection.Seq[TimestampedMessage]) {
    assert(messages.isEmpty) // FIXME, we don't yet support multiple tlog chunks (trying to avoid big allocs)
    messages = m

    m.foreach(loadMessage)
  }

  /**
   * Load messages from a raw mavlink tlog file
   */
  private def loadBytes(bytes: Array[Byte], reduced: Boolean) {
    var m: Iterable[TimestampedMessage] = (new BinaryMavlinkReader(bytes))

    if (reduced) {
      val reducer = new DataReducer
      m = m.filter(reducer.filter)
    }
    loadMessages(m.toSeq)
  }
}

object PlaybackModel {
  /**
   * Fully populate a model from bytes, or return None if bytes not available
   */
  def fromBytes(b: Array[Byte], reduced: Boolean = true) = {
    val model = new PlaybackModel
    model.loadBytes(b, reduced)
    model
  }

  val currentYear = Calendar.getInstance().get(Calendar.YEAR)

}
