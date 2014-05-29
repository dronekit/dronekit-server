package com.geeksville.dapi

import com.geeksville.json.GeoJSON
import org.json4s.JsonAST.JObject
import com.geeksville.flight.LiveOrPlaybackModel

class GeoJSONFactory(model: PlaybackModel) {
  import model._

  def toGeoJSON(): JObject = {
    import GeoJSON._

    val bbox = new BoundingBox(0.005)

    val wptColor = Some("#000099")
    val tracklogStyle = lineStyles(color = Some("#00FF00"), width = Some(2))
    val tracklogShadow = lineStyles(color = Some("#444444"), width = Some(4))
    val wptLineStyle = lineStyles(color = Some("#0000FF"), opacity = Some(0.5))

    // Symbol names documented here: https://www.mapbox.com/maki/
    val wptMarkers = waypointsForMap.map { wp =>
      bbox.addPoint(wp.location)

      // If the waypoint has interesting text, show it
      val desc = if (wp.shortString != "Waypoint")
        Some(wp.shortString)
      else
        None

      val (name, symbol) = if (wp.isHome)
        ("Home", "building")
      else
        // Just cycle the wpt numbers from 0 to ten over and over again
        //val symbol = if (wp.seq < 10) wp.seq.toString else "embassy"
        ("Waypoint #" + wp.seq, (wp.seq % 10).toString)

      makeMarker(wp.location, name, color = wptColor, description = desc, symbol = Some(symbol))
    }

    val wptLines = waypointsForMap.map(_.location)

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

    var modeMarkers: List[JObject] = Nil

    // As we iterate through the locations, look to see if we've crossed a mode change timestamp and emit a proper marker
    val locations = positions.view.map { p =>
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

      bbox.addPoint(p.loc)
      p.loc
    }

    // The lines along the tracklog
    val tracklogLineString = makeLineString(locations)

    // Ugh - we want to draw a shadow on our tracklog - so we need to send the whole list of points _twice_
    val tracklog = makeFeatureCollection(makeFeature(tracklogLineString, tracklogShadow), makeFeature(tracklogLineString, tracklogStyle))

    val modeLayer = makeFeatureCollection(modeMarkers: _*)
    val wptLayer = makeFeatureCollection(wptMarkers :+ makeFeature(makeLineString(wptLines), wptLineStyle): _*)
    val topLevel = makeFeatureCollection(modeLayer, wptLayer, tracklog)
    addBoundingBox(topLevel, bbox)
  }

}