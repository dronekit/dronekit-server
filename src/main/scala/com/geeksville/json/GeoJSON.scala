package com.geeksville.json

import org.json4s.Formats
import org.json4s.DefaultFormats
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.native.Serialization
import com.geeksville.flight.Location
import org.json4s.CustomSerializer
import org.json4s.Extraction

object GeoJSON {

  class LocationSerializer extends CustomSerializer[Location](format => (
    {
      case JArray(s) =>
        throw new Exception("Not implemented")
    },
    {
      case x: Location =>
        var s = List(JDouble(x.lon), JDouble(x.lat))
        x.alt.foreach { a =>
          s = s :+ JDouble(a)
        }
        JArray(s): JArray
    }))

  // Sets up automatic case class to JSON output serialization
  implicit val jsonFormats: Formats = DefaultFormats ++ GeeksvilleFormats + new LocationSerializer

  def makePoint(coords: Location): JObject = {
    val j = Extraction.decompose(coords)
    ("type" -> "Point") ~ ("coordinates" -> j)
  }

  def makeLineString(coords: Seq[Location]): JObject = {
    ("type" -> "LineString") ~ ("coordinates" -> Extraction.decompose(coords))
  }

  def makePolygon(coords: Seq[Location]): JObject = {
    ("type" -> "Polygon") ~ ("coordinates" -> Extraction.decompose(coords))
  }

  def makeMultiLineString(coords: Seq[Seq[Location]]): JObject = {
    ("type" -> "MultiLineString") ~ ("coordinates" -> Extraction.decompose(coords))
  }

  def makeGeometryCollection(geos: Seq[JObject]) = {
    ("type" -> "GeometryCollection") ~ ("geometries" -> Extraction.decompose(geos))
  }

  def makeFeatureCollection(f: JObject*) = {
    ("type" -> "FeatureCollection") ~ ("features" -> Extraction.decompose(f))
  }

  def makeFeature(geo: JObject, properties: JValue = JNull): JObject = {
    ("type" -> "Feature") ~ ("geometry" -> geo) ~ ("properties" -> properties)
  }

  /// Make line style properties based on teh following rules
  // OPTIONAL: default "555555"
  // the color of a line as part of a polygon, polyline, or
  // multigeometry
  //
  // value must follow COLOR RULES
  //"stroke": "#555555",
  // OPTIONAL: default 1.0
  // the opacity of the line component of a polygon, polyline, or
  // multigeometry
  //
  // value must be a floating point number greater than or equal to
  // zero and less or equal to than one
  //"stroke-opacity": 1.0,
  // OPTIONAL: default 2
  // the width of the line component of a polygon, polyline, or
  // multigeometry
  //
  // value must be a floating point number greater than or equal to 0
  //"stroke-width": 2,
  // OPTIONAL: default "555555"
  // the color of the interior of a polygon
  //
  // value must follow COLOR RULES
  //"fill": "#555555",
  // OPTIONAL: default 0.6
  // the opacity of the interior of a polygon. implementations
  // may choose to set this to 0 for line features.
  //
  // value must be a floating point number greater than or equal to
  // zero and less or equal to than one
  //"fill-opacity": 0.5
  def lineStyles(color: Option[String] = None, width: Option[Double] = None, opacity: Option[Double] = None, fill: Option[String] = None): JObject = {
    ("stroke" -> color) ~ ("fill" -> fill) ~ ("stroke-width" -> width) ~ ("stroke-opacity" -> opacity)
  }

  /**
   * color such as "#ff4444"
   */
  def makeMarker(coords: Location, title: String, description: Option[String] = None, color: Option[String] = None, size: String = "medium", symbol: Option[String] = None) = {
    val geo = makePoint(coords)
    var ps = ("marker-size" -> size) ~
      ("title" -> title) ~
      ("description" -> description) ~
      ("marker-color" -> color) ~
      ("marker-symbol" -> symbol)

    val props: JObject = ps
    makeFeature(geo, props)
  }

  class BoundingBox(pad: Double) {
    case class LatLngAlt(var lat: Double, var lon: Double, var alt: Double)
    var southWest = LatLngAlt(Double.MaxValue, Double.MaxValue, Double.MaxValue)
    var northEast = LatLngAlt(Double.MinValue, Double.MinValue, Double.MinValue)

    /// Does this bounding box contain at least one good point?
    var isValid = false

    def range = Seq(southWest.lon, southWest.lat, southWest.alt,
      northEast.lon, northEast.lat, northEast.alt)

    def toJSON: JObject = ("bbox" -> range)

    /// Expand the bounding box to include the specified point
    def addPoint(l: Location) {
      southWest.lat = math.min(l.lat - pad, southWest.lat)
      southWest.lon = math.min(l.lon - pad, southWest.lon)
      southWest.alt = math.min(l.alt.getOrElse(0.0), southWest.alt)

      northEast.lat = math.max(l.lat + pad, northEast.lat)
      northEast.lon = math.max(l.lon + pad, northEast.lon)
      northEast.alt = math.max(l.alt.getOrElse(0.0), northEast.alt)

      isValid = true
    }
  }

  def addBoundingBox(src: JObject, bbox: BoundingBox) = {

    src ~ bbox.toJSON
  }
}