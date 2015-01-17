package com.geeksville.dapi

import com.geeksville.json.GeoJSON
import org.json4s.JsonAST.JObject
import com.geeksville.flight.LiveOrPlaybackModel
import grizzled.slf4j.Logging
import com.geeksville.flight.Location

class GeoJSONFactory(model: PlaybackModel) extends Logging {
  import model._
  import GeoJSON._

  // For wpt colors we bop through four different hues - one for each group of ten - blue, then purpleish, then redish, the brownish
  val wptColor = Seq("#000099", "#9900ff", "#990033", "#cc3300")
  val wptDisabledColor = Some("#9999D6") // Used when we suspect the wpts were too far from the current mission
  val tracklogShadow = lineStyles(color = Some("#444444"), width = Some(4))
  val wptLineStyle = lineStyles(color = Some("#0000FF"), opacity = Some(0.5))
  val wptDisabledLineStyle = lineStyles(color = Some("#9999D6"), opacity = Some(0.5))

  /**
   * Generates a JSON object with GEOJson
   * @return None if we can't make sen
   */
  def toGeoJSON(): Option[JObject] = {

    // State for advancing modes
    val modeIterator = modeChanges.iterator
    var nextMode: Option[(Long, String)] = None
    var curMode: Option[(Long, String)] = None
    def advanceMode() {
      curMode = nextMode
      nextMode = if (modeIterator.hasNext)
        Some(modeIterator.next)
      else
        None
    }
    advanceMode()

    // Process the tracklog
    // As we iterate through the locations, look to see if we've crossed a mode change timestamp and emit a proper marker
    var modeMarkers: List[JObject] = Nil
    var tracklogs: List[JObject] = Nil
    var curTracklog: List[Location] = Nil
    val tracklogBbox = new BoundingBox

    /// Complete any temp tracklog and convert to geojson
    def advanceTracklog() {
      // Generate the correctly colored tracklog

      // The lines along the tracklog
      if (!curTracklog.isEmpty) {
        val tracklogLineString = makeLineString(curTracklog)

        val curColor = curMode.flatMap { m => LiveOrPlaybackModel.htmlColorName(m._2) }.getOrElse("#00FF00")

        // Ugh - we want to draw a shadow on our tracklog - so we need to send the whole list of points _twice_
        val tracklogStyle = lineStyles(color = Some(curColor), width = Some(2))

        val tracklog = makeFeatureCollection(makeFeature(tracklogLineString, tracklogShadow), makeFeature(tracklogLineString, tracklogStyle))
        tracklogs = tracklog :: tracklogs
      }

      curTracklog = Nil
    }

    debug(s"Generating GeoJSON")
    positions.foreach { p =>

      val crossedModeChange = nextMode.map {
        case (t, m) =>
          t < p.time
      }.getOrElse(false)

      // We just finished a tracklog for the previous mode - terminate it
      if (crossedModeChange) {

        // Generate the mode marker
        val newModeName = nextMode.get._2
        val color = LiveOrPlaybackModel.htmlColorName(newModeName)
        modeMarkers = makeMarker(p.loc, "Mode change", description = Some(newModeName), size = "small", color = color, symbol = Some("triangle-stroked")) :: modeMarkers

        advanceTracklog()
        advanceMode()
      }

      // Add to our current work in progress tracklog
      curTracklog = p.loc :: curTracklog

      tracklogBbox.addPoint(p.loc)
    }

    // If we have any leftover in-process tracklog, show it
    advanceTracklog()

    val wptBbox = new BoundingBox(0.005)

    // we exclude home from our wpt bbox because later we check if all of the !home wpts are outside of the tracklog
    // if so, the wpts are probably from an old mission
    waypointsForMap.foreach { wp =>
      val isHome = wp.isHome || wp.seq == 0
      if (!isHome)
        wptBbox.addPoint(wp.location)
    }

    val disablingWaypoints = !wptBbox.intersectsWith(tracklogBbox)
    debug(s"disabling=$disablingWaypoints, from wpts $wptBbox and tlog $tracklogBbox")

    // Symbol names documented here: https://www.mapbox.com/maki/
    val wptMarkers = waypointsForMap.map { wp =>
      // If the waypoint has interesting text, show it
      val desc = if (wp.shortString != "Waypoint")
        Some(wp.shortString)
      else
        None

      // Some mission records seem to be forgetting to set the isHome bit
      val isHome = wp.isHome || wp.seq == 0
      val (name, symbol) = if (isHome)
        ("Home", "building")
      else
        // Just cycle the wpt numbers from 0 to ten over and over again
        //val symbol = if (wp.seq < 10) wp.seq.toString else "embassy"
        ("Waypoint #" + wp.seq, (wp.seq % 10).toString)

      val wcolor = if (isHome || !disablingWaypoints) {
        // Pick colors in groups of ten
        val colorBlock = (wp.seq / 10) % wptColor.size
        Some(wptColor(colorBlock))
      } else
        wptDisabledColor
      makeMarker(wp.location, name, color = wcolor, description = desc, symbol = Some(symbol))
    }

    // If we are disabling wpts don't draw a line from home to the first wpt
    val wptLines = {
      val toprocess = if (disablingWaypoints && waypointsForMap.size > 1)
        waypointsForMap.tail
      else
        waypointsForMap
      toprocess.map(_.location)
    }

    val modeLayer = makeFeatureCollection(modeMarkers: _*)
    val wlineStyle = if (disablingWaypoints)
      wptDisabledLineStyle
    else
      wptLineStyle
    val wptLayer = makeFeatureCollection(wptMarkers :+ makeFeature(makeLineString(wptLines), wlineStyle): _*)
    val topLevel = makeFeatureCollection(modeLayer :: wptLayer :: tracklogs: _*)

    val bbox = new BoundingBox(0.005)
    bbox.union(tracklogBbox)
    // bbox.union(wptBbox) We no longer grow to include the wpts because old wpts in vehicle look bad on the map
    if (bbox.isValid)
      Some(addBoundingBox(topLevel, bbox))
    else {
      warn(s"Can not make GeoJSON for $model")
      None
    }

  }

}
