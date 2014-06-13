package com.geeksville.dapi

import de.micromata.opengis.kml.v_2_2_0.Coordinate
import de.micromata.opengis.kml.v_2_2_0.Icon
import de.micromata.opengis.kml.v_2_2_0.Folder
import de.micromata.opengis.kml.v_2_2_0.Container
import com.geeksville.flight.Location
import java.net.URI
import de.micromata.opengis.kml.v_2_2_0.Kml
import com.geeksville.flight.LiveOrPlaybackModel
import de.micromata.opengis.kml.v_2_2_0.AltitudeMode
import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.net.URLEncoder

class KMLFactory(model: PlaybackModel) {
  import model._
  import LiveOrPlaybackModel._

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

      // makePlace(folder, "Start", startPosition.get)
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