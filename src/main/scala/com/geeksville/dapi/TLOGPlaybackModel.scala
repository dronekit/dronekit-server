package com.geeksville.dapi

import com.geeksville.mavlink._
import org.mavlink.messages.ardupilotmega.msg_global_position_int
import scala.collection.mutable.ArrayBuffer
import java.io.File
import com.geeksville.flight.VehicleSimulator
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import com.geeksville.flight.Location
import org.mavlink.messages.ardupilotmega.msg_gps_raw_int
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.net.URLEncoder
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
import java.sql.Timestamp

/**
 * @param time in usecs
 */
case class TimestampedLocation(time: Long, loc: Location)

/**
 * Models the state of N vehicles, built up by playing back a series of provided records.
 *
 * Can be used on one TLogChunk or a series of chunks.
 * DO NOT DEPEND on any web services in this class (will move to common someday?)
 */
class TLOGPlaybackModel extends PlaybackModel with LiveOrPlaybackModel with Logging {
  import LiveOrPlaybackModel._

  /**
   * a seq of usec_time -> location
   */
  val positions = ArrayBuffer[TimestampedLocation]()

  var messages: scala.collection.Seq[TimestampedMessage] = Seq[TimestampedMessage]()
  var modeChangeMsgs = Seq[TimestampedMessage]()

  var numMessages = 0

  /// A MAV_TYPE vehicle code
  var vehicleType: Option[Int] = None
  private var heartbeatAutopilotType: Option[Int] = None

  var gcsType = "TBD"

  private val waypointOpt = ArrayBuffer[Option[Waypoint]]()

  /// All the waypoints we've seen
  def waypoints = waypointOpt.flatten.toSeq

  val parameters = ArrayBuffer[ROParamValue]()

  override val errors: ArrayBuffer[(Long, ErrorCode)] = ArrayBuffer.empty

  override def abstractMessages = messages.flatMap { m => MavlinkBasedMessage.tryCreate(m.msg).map(TimestampedAbstractMessage(m.time, _)) }

  override def autopilotType = hardwareToAutopilotType.orElse(heartbeatAutopilotType)

  /// Just the messages that happened while the vehicle was actively flying
  def inFlightMessages: Traversable[TimestampedMessage] = (for {
    s <- startOfFlightTime
    e <- endOfFlightTime
  } yield {
    messages.filter { m => m.time >= s && m.time <= e }
  }).getOrElse(Seq())

  def modeChanges = modeChangeMsgs.map { m =>
    val code = m.msg.asInstanceOf[msg_heartbeat].custom_mode.toInt
    //println("mapping mode code " + m.msg)
    m.time -> modeToString(code)
  }

  override def modelType = "TLOG"

  private def addPosition(raw: TimestampedMessage, l: Location) {
    if (l.isValid) {
      positions.append(TimestampedLocation(raw.time, l))
      l.alt.foreach { a => maxAltitude = math.max(maxAltitude, a) }
    }
  }

  private def loadMessage(raw: TimestampedMessage) {
    numMessages += 1
    perhapsUpdateModel(raw)

    // First update any standard live/delayed model stuff
    val msg = raw.msg
    perhapsUpdateModel(msg)

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
          heartbeatAutopilotType = Some(msg.autopilot)
          if (modeChangeMsgs.isEmpty || modeChangeMsgs.last.msg.asInstanceOf[msg_heartbeat].custom_mode != msg.custom_mode)
            modeChangeMsgs = modeChangeMsgs :+ raw
        }
      case msg: msg_param_value =>
        // We fill any missing positions with None
        if (msg.param_index != 65535) { // Some vehicles send param msgs with bogus indexes
          while (parameters.size < msg.param_index + 1)
            parameters.append(new ROParamValue)
          parameters(msg.param_index).raw = Some(msg)
        }

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

object TLOGPlaybackModel {
  /**
   * Fully populate a model from bytes, or return None if bytes not available
   */
  def fromBytes(b: Array[Byte], reduced: Boolean = true) = {
    val model = new TLOGPlaybackModel
    model.loadBytes(b, reduced)
    model
  }

  val currentYear = Calendar.getInstance().get(Calendar.YEAR)

}
