package com.geeksville.dapi

import com.geeksville.json.GeoJSON
import org.json4s.JsonAST.JObject
import com.geeksville.flight.LiveOrPlaybackModel
import grizzled.slf4j.Logging

class GeoJSONFactory(model: PlaybackModel) extends Logging {
  import model._
  import GeoJSON._

  val wptColor = Some("#000099")
  val wptDisabledColor = Some("#9999D6") // Used when we suspect the wpts were too far from the current mission
  val tracklogStyle = lineStyles(color = Some("#00FF00"), width = Some(2))
  val tracklogShadow = lineStyles(color = Some("#444444"), width = Some(4))
  val wptLineStyle = lineStyles(color = Some("#0000FF"), opacity = Some(0.5))
  val wptDisabledLineStyle = lineStyles(color = Some("#9999D6"), opacity = Some(0.5))

  /**
   * @return None if we can't make sen
   */
  def toGeoJSON(): Option[JObject] = {

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

    // Process the tracklog
    // As we iterate through the locations, look to see if we've crossed a mode change timestamp and emit a proper marker
    var modeMarkers: List[JObject] = Nil
    val tracklogBbox = new BoundingBox
    val locations = positions.map { p =>
      val crossedModeChange = nextMode.map {
        case (t, m) =>
          t < p.time
      }.getOrElse(false)

      if (crossedModeChange) {
        val newModeName = nextMode.get._2

        val color = LiveOrPlaybackModel.htmlColorName(newModeName)
        modeMarkers = makeMarker(p.loc, "Mode change", description = Some(newModeName), size = "small", color = color, symbol = Some("triangle-stroked")) :: modeMarkers
        advanceMode()
      }

      tracklogBbox.addPoint(p.loc)
      p.loc
    }

    val wptBbox = new BoundingBox

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

      val wcolor = if (isHome || !disablingWaypoints)
        wptColor
      else
        wptDisabledColor
      makeMarker(wp.location, name, color = wcolor, description = desc, symbol = Some(symbol))
    }

    // If we are disabling wpts don't draw a line from home to the first wpt
    val wptLines = {
      val toprocess = if (disablingWaypoints)
        waypointsForMap.tail
      else
        waypointsForMap
      toprocess.map(_.location)
    }

    // The lines along the tracklog
    val tracklogLineString = makeLineString(locations)

    // Ugh - we want to draw a shadow on our tracklog - so we need to send the whole list of points _twice_
    val tracklog = makeFeatureCollection(makeFeature(tracklogLineString, tracklogShadow), makeFeature(tracklogLineString, tracklogStyle))

    val modeLayer = makeFeatureCollection(modeMarkers: _*)
    val wlineStyle = if (disablingWaypoints)
      wptDisabledLineStyle
    else
      wptLineStyle
    val wptLayer = makeFeatureCollection(wptMarkers :+ makeFeature(makeLineString(wptLines), wlineStyle): _*)
    val topLevel = makeFeatureCollection(modeLayer, wptLayer, tracklog)

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