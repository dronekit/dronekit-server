package com.geeksville.dapi

import org.json4s.JsonAST.JObject
import grizzled.slf4j.Logging
import scala.xml._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.DefaultFormats
import org.json4s.Formats

case class ResultJSON(name: String, status: String, message: String, data: Option[String])

class AnalysisFactory(bytes: Array[Byte], isText: Boolean) extends Logging {

  def toJSON(): Option[JObject] = {

    val str = bytes.map(_.toChar).mkString
    Some(AnalysisFactory.decodeToolResponse(str))
  }
}

object AnalysisFactory {
  protected implicit val jsonFormats: Formats = DefaultFormats

  /// Parse the output of LogAnalyser.py
  def decodeToolResponse(str: String) = {
    val xml = XML.loadString(str)

    val results = xml \\ "result"

    val resultsJson = results.map { r =>
      val j = ResultJSON(r \ "name" toString, r \ "name" toString, r \ "name" toString, r \ "name" map (_.toString) headOption)
      Extraction.decompose(j).asInstanceOf[JObject]
    }
    println(s"Returning json: $resultsJson")
    JObject("results" -> JArray(resultsJson.toList))
  }
}