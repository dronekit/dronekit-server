/**
 * *****************************************************************************
 * Copyright 2013 Kevin Hester
 *
 * See LICENSE.txt for license details.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.geeksville.nestor

import com.geeksville.mavlink.TimestampedMessage
import org.mavlink.messages.ardupilotmega.msg_global_position_int
import scala.collection.mutable.ArrayBuffer
import de.micromata.opengis.kml.v_2_2_0.Kml
import java.io.File
import de.micromata.opengis.kml.v_2_2_0.AltitudeMode
import com.geeksville.flight.VehicleSimulator
import java.io.ByteArrayOutputStream
import com.geeksville.mavlink.BinaryMavlinkReader
import java.io.ByteArrayInputStream
import de.micromata.opengis.kml.v_2_2_0.Point
import com.geeksville.flight.Location
import de.micromata.opengis.kml.v_2_2_0.Coordinate
import org.mavlink.messages.ardupilotmega.msg_gps_raw_int
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.net.URLEncoder
import com.geeksville.mavlink.DataReducer
import org.mavlink.messages.ardupilotmega.msg_mission_item
import com.geeksville.flight.Waypoint
import com.geeksville.flight.WaypointsForMap
import java.net.URI
import de.micromata.opengis.kml.v_2_2_0.Icon
import com.geeksville.flight.ParametersReadOnlyModel
import org.mavlink.messages.ardupilotmega.msg_heartbeat
import org.mavlink.messages.ardupilotmega.msg_param_value
import org.mavlink.messages.ardupilotmega.msg_vfr_hud
import de.micromata.opengis.kml.v_2_2_0.Folder
import de.micromata.opengis.kml.v_2_2_0.Container
import org.mavlink.messages.MAV_TYPE
import com.geeksville.flight.LiveOrPlaybackModel
import java.util.Date

case class TimestampedLocation(time: Long, loc: Location)

/**
 * Models the state of N vehicles, built up by playing back a series of provided records.
 *
 * Can be used on one TLogChunk or a series of chunks.
 * DO NOT DEPEND on any web services in this class (will move to common someday?)
 */
class PlaybackModel extends WaypointsForMap with LiveOrPlaybackModel with ParametersReadOnlyModel {
  import LiveOrPlaybackModel._

  /**
   * a seq of usec_time -> location
   */
  val positions = ArrayBuffer[TimestampedLocation]()

  private var firstMessage: Option[TimestampedMessage] = None
  private var lastMessage: Option[TimestampedMessage] = None

  /// First found position
  var startPosition: Option[Location] = None

  var messages: scala.collection.Seq[TimestampedMessage] = Seq[TimestampedMessage]()
  var modeChangeMsgs = Seq[TimestampedMessage]()

  var startOfFlightMessage: Option[TimestampedMessage] = None
  var endOfFlightMessage: Option[TimestampedMessage] = None

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

  /**
   * duration of flying portion in seconds
   */
  override def flightDuration = (for {
    s <- startOfFlightMessage
    e <- endOfFlightMessage
  } yield {
    val r = e.timeSeconds - s.timeSeconds
    println(s"Calculated flight duration of $r")
    r
  }).orElse {
    println("Can't find duration for flight")
    None
  }

  /// Just the messages that happened while the vehicle was actively flying
  def inFlightMessages: Traversable[TimestampedMessage] = (for {
    s <- startOfFlightMessage
    e <- endOfFlightMessage
  } yield {
    messages.filter { m => m.time >= s.time && m.time <= e.time }
  }).getOrElse(Seq())

  def summary(ownerId: String) = MissionSummary(new Date(startTime.getOrElse(0L) / 1000),
    new Date(currentTime.getOrElse(0L) / 1000), maxAltitude, maxGroundSpeed, maxAirSpeed, maxG,
    vehicleTypeName, humanAutopilotType.getOrElse("unknown"), gcsType, ownerId, flightDuration)

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
    if (!firstMessage.isDefined)
      firstMessage = Some(raw)
    lastMessage = Some(raw)

    // Update useful summary information as we read
    raw.msg match {
      case m: msg_global_position_int if useGlobalPosition =>
        addPosition(raw, VehicleSimulator.decodePosition(m))
      case m: msg_gps_raw_int =>
        VehicleSimulator.decodePosition(m).foreach { l => addPosition(raw, l) }
      case m: msg_vfr_hud =>
        maxAirSpeed = math.max(m.airspeed, maxAirSpeed)
        maxGroundSpeed = math.max(m.groundspeed, maxGroundSpeed)

        if (m.throttle > 0) {
          if (!startOfFlightMessage.isDefined)
            startOfFlightMessage = Some(raw)
          endOfFlightMessage = Some(raw) // Currently we just use the last place the throttle was on - FIXME
        }
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
    messages = m.toSeq

    // FIXME - do this data reduction somewhere else
    loadMessages(messages)
  }

  def toCoord(l: Location) = {
    new Coordinate(l.lon, l.lat, l.alt.getOrElse(0))
  }

  private def colorToString(transparency: Int, tuple: (Int, Int, Int)) =
    "%02x%02x%02x%02x".format(transparency, tuple._1, tuple._2, tuple._3)

  /// Generate a KML model object
  /// @param limited if true this is for gmaps, so don't use anything fancy
  private def toKML(uri: URI, limitedIn: Boolean = false) = {
    println(s"Creating KML for $uri")

    val kml = new Kml()

    val doc = kml.createAndSetDocument

    // Google maps seems to die if we have icons and more than this amount of points
    val limited = limitedIn && positions.size > 5000

    // For the tracklog
    {
      val style = doc.createAndAddStyle().withId("modeUnknown")
      //style.createAndSetIconStyle().withColor("a1ff00ff").withScale(1.399999976158142).withIcon(new Icon().withHref("http://myserver.com/icon.jpg"));
      //style.createAndSetLabelStyle().withColor("7fffaaff").withScale(1.5);
      style.createAndSetLineStyle().withColor("7f00ffff").withWidth(4.0)
      style.createAndSetPolyStyle().withColor("1f00ff00") // .withColorMode(ColorMode.RANDOM);
    }

    def modeToStyleName(modename: String) = if (modeToColorMap.contains(modename))
      "mode" + modename
    else
      "modeUnknown"

    // For the various standard mode colors
    modeToColorMap.foreach {
      case (modename, color) =>
        val style = doc.createAndAddStyle().withId(modeToStyleName(modename))
        //style.createAndSetIconStyle().withColor("a1ff00ff").withScale(1.399999976158142).withIcon(new Icon().withHref("http://myserver.com/icon.jpg"));
        //style.createAndSetLabelStyle().withColor("7fffaaff").withScale(1.5);
        val cstr = colorToString(0x7f, color)
        //println(s"making $modename with $cstr")
        style.createAndSetLineStyle().withColor(colorToString(0x7f, color)).withWidth(4.0)
        style.createAndSetPolyStyle().withColor(colorToString(0x1f, color)) // .withColorMode(ColorMode.RANDOM);
    }

    // For the waypoints
    if (!limited) {
      val style = doc.createAndAddStyle().withId("blueLine")
      // .withColor("a1ff00ff").withScale(1.399999976158142)
      //style.createAndSetLabelStyle().withColor("7fffaaff").withScale(1.5);
      style.createAndSetLineStyle().withColor("7fff0000").withWidth(4.0)
      // style.createAndSetPolyStyle().withColor("7f00ff00") // .withColorMode(ColorMode.RANDOM);
    }

    def makeIcon(name: String, imgName: String) {
      val style = doc.createAndAddStyle().withId(name)

      val iconurl = uri.resolve("/images/" + imgName + ".png").toString
      println("base: " + uri + " -> " + iconurl)
      style.createAndSetIconStyle().withIcon(new Icon().withHref(iconurl)).withScale(1.0)
    }

    def makePlace(parent: Folder, name: String, p: Location) = {
      val placemark = parent.createAndAddPlacemark.withName(name)

      placemark.createAndSetPoint.getCoordinates.add(toCoord(p))

      placemark
    }

    if (!limited) {
      val folder = doc.createAndAddFolder.withName("Waypoints")

      makeIcon("regWaypoint", "blue_dot")
      makeIcon("homeWaypoint", "lz_blue")
      val wpts = waypointsForMap
      if (waypoints.size > 0 && waypoints(0).isHome)
        makePlace(folder, "Home", waypoints(0).location).setStyleUrl("#homeWaypoint")

      wpts.foreach { wp =>
        if (!wp.isHome)
          makePlace(folder, "Waypoint #" + wp.seq, wp.location).setStyleUrl("#regWaypoint") // FIXME make a blank icon, just use the name 
      }

      val waypointcoords = folder.createAndAddPlacemark.withOpen(true).withStyleUrl("#blueLine")
        .createAndSetLineString().getCoordinates

      wpts.foreach { w => waypointcoords.add(toCoord(w.location)) }

      makePlace(folder, "Start", startPosition.get)
      makePlace(folder, "End", endPosition.get)
    }

    val modeFolder = if (!limited) {
      Some(doc.createAndAddFolder.withName("Mode Changes"))
    } else
      None

    // State for advancing light styles
    var linecoords: java.util.List[Coordinate] = null

    // Start a new line color with the color correct for the given mode name (or the default color if nothing else)
    def startNewLine(modeName: String) = {
      val styleName = modeToStyleName(modeName)

      //println("starting new line " + modeName)
      linecoords = doc.createAndAddPlacemark.withName(modeName).withOpen(true).withStyleUrl("#" + styleName)
        .createAndSetLineString().withTessellate(true).withAltitudeMode(AltitudeMode.ABSOLUTE).withExtrude(true).getCoordinates
    }

    // Create a default line
    startNewLine("Start")

    // State for advancing modes
    val modeIterator = modeChanges.iterator
    var nextMode: Option[(Long, String)] = None
    def advanceMode() {
      nextMode = if (modeIterator.hasNext)
        Some(modeIterator.next)
      else
        None
    }
    advanceMode()

    println("Emitting positions: " + positions.size)
    //Thread.dumpStack()
    positions.foreach { p =>
      val crossedModeChange = nextMode.map {
        case (t, m) =>
          t < p.time
      }.getOrElse(false)
      if (crossedModeChange) {
        val newModeName = nextMode.get._2
        modeFolder.foreach(makePlace(_, newModeName, p.loc))
        startNewLine(newModeName)
        advanceMode()
      }
      linecoords.add(toCoord(p.loc))
    }

    kml
  }

  def toKMLBytes(uri: URI) = {
    val kml = toKML(uri)
    val byteStream = new ByteArrayOutputStream()
    kml.marshal(byteStream)
    byteStream.toByteArray
  }

  def toKMZBytes(uri: URI, limited: Boolean) = {
    val kml = toKML(uri, limited)

    // FIXME - return as a marshalAsKmz, or an outputstream
    val byteStream = new ByteArrayOutputStream()
    val out = new ZipOutputStream(byteStream)
    out.setComment("KMZ-file created with DroneShare. Visit us: http://www.droneshare.com");
    out.putNextEntry(new ZipEntry(URLEncoder.encode("doc.kml", "UTF-8")))
    kml.marshal(out)
    out.closeEntry()
    out.close()
    byteStream.toByteArray
  }
}

object PlaybackModel {
  /**
   * Fully populate a model from bytes, or return None if bytes not available
   */
  def fromBytes(tlog: TLogChunk, reduced: Boolean = true) = {
    tlog.bytes.map { b =>
      val model = new PlaybackModel
      model.loadBytes(b, reduced)
      model
    }
  }
}
/*
"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            +
            "<kml xmlns=\"http://www.opengis.net/kml/2.2\">"
            +
            "<Document>"
            +
            "<name>Gaggle KML</name>"
            +
            "<open>1</open>"
            +
            "<description>Gaggle KML file</description>"
            +
            "<Style id=\"yellowLineGreenPoly\">" +
            "<LineStyle>" +
            "<color>7f00ffff</color>" +
            "<width>4</width>" +
            "</LineStyle>" +
            "<PolyStyle>" +
            "<color>7f00ff00</color>" +
            "</PolyStyle>" +
            "</Style>" +
            "<Placemark>" +
            "<name>Flight</name>" +
            "<visibility>1</visibility>" +
            // createDescription() +
            "<styleUrl>#yellowLineGreenPoly</styleUrl>" +
            "<LineString>" +
            "<extrude>1</extrude>" +
            "<tessellate>1</tessellate>" +
            "<altitudeMode>absolute</altitudeMode>" +
            "<coordinates>"
*/
