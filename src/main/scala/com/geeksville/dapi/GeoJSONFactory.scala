package com.geeksville.dapi

import com.geeksville.json.GeoJSON
import org.json4s.JsonAST.JObject
import com.geeksville.flight.LiveOrPlaybackModel

class GeoJSONFactory(model: PlaybackModel) {
  import model._

  def toGeoJSON(): JObject = {
    import GeoJSON._

    val bbox = new BoundingBox

    val wptColor = Some("#0000ff")

    // Symbol names documented here: https://www.mapbox.com/maki/
    val wptMarkers = waypointsForMap.map { wp =>
      bbox.addPoint(wp.location)

      if (wp.isHome)
        makeMarker(wp.location, "Home", color = wptColor, symbol = Some("building"))
      else {
        val symbol = if (wp.seq < 10) wp.seq.toString else "embassy"
        makeMarker(wp.location, "Waypoint #" + wp.seq, color = wptColor, symbol = Some(symbol))
      }
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
        modeMarkers = makeMarker(p.loc, newModeName, color = color, symbol = Some("triangle-stroked")) :: modeMarkers
        advanceMode()
      }

      bbox.addPoint(p.loc)
      p.loc
    }
    val tracklog = makeFeatureCollection(makeFeature(makeLineString(locations)))
    val modeLayer = makeFeatureCollection(modeMarkers: _*)
    val wptLayer = makeFeatureCollection(wptMarkers :+ makeFeature(makeLineString(wptLines)): _*)
    val topLevel = makeFeatureCollection(modeLayer, wptLayer, tracklog)
    addBoundingBox(topLevel, bbox)
  }

}