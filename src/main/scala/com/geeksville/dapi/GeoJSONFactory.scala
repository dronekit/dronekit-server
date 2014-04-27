package com.geeksville.dapi

import com.geeksville.json.GeoJSON
import org.json4s.JsonAST.JObject

class GeoJSONFactory(model: PlaybackModel) {
  import model._

  def toGeoJSON(): JObject = {
    import GeoJSON._

    val bbox = new BoundingBox

    val wpts = waypointsForMap.map { wp =>
      bbox.addPoint(wp.location)

      if (wp.isHome)
        makeMarker(wp.location, "Home", symbol = Some("building"))
      else
        makeMarker(wp.location, "Waypoint #" + wp.seq, symbol = Some("marker"))
    }

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
        modeMarkers = makeMarker(p.loc, newModeName, symbol = Some("marker")) :: modeMarkers
        advanceMode()
      }

      bbox.addPoint(p.loc)
      p.loc
    }
    val lines = makeFeature(makeLineString(locations))

    addBoundingBox(makeFeatureCollection(
      Seq(makeFeatureCollection(wpts), makeFeatureCollection(modeMarkers), makeFeatureCollection(Seq(lines)))), bbox)
  }

}