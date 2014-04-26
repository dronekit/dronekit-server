package com.geeksville.dapi

import com.geeksville.json.GeoJSON
import org.json4s.JsonAST.JObject

class GeoJSONFactory(model: PlaybackModel) {
  import model._

  def toGeoJSON(): JObject = {
    import GeoJSON._

    val wpts = waypointsForMap.map { wp =>
      if (wp.isHome)
        makeMarker(wp.location, "Home", symbol = Some("building"))
      else
        makeMarker(wp.location, "Waypoint #" + wp.seq, symbol = Some("marker"))
    }

    val lines = makeFeature(makeLineString(positions.view.map(_.loc)))
    makeFeatureCollection(wpts ++ Seq(lines))
  }

}