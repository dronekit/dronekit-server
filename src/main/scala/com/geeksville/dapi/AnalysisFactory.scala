package com.geeksville.dapi

import org.json4s.JsonAST.JObject
import grizzled.slf4j.Logging
import scala.xml._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.DefaultFormats
import org.json4s.Formats
import scala.sys.process._
import java.io.ByteArrayInputStream
import com.geeksville.util.Using

case class ResultJSON(name: String, status: String, message: String, data: Option[String])

class AnalysisFactory(bytes: Array[Byte], isText: Boolean) extends Logging {

  val toolPath = "/home/kevinh/development/drone/ardupilot/Tools/LogAnalyzer/LogAnalyzer.py"
  val toolArgs = "-q -x - -" // quiet, xml to std out, read from stdin

  def toJSON(): Option[JObject] = {
    warn("Running analysis tool")

    Using.using(new ByteArrayInputStream(bytes)) { instream =>
      val result = (toolPath + " " + toolArgs) #< instream !!

      debug(s"Analysis returned $result")

      Some(AnalysisFactory.decodeToolResponse(result))
    }
  }
}

object AnalysisFactory {
  protected implicit val jsonFormats: Formats = DefaultFormats

  /// Parse the output of LogAnalyser.py
  def decodeToolResponse(str: String) = {
    val xml = XML.loadString(str)

    val results = xml \\ "result"

    val resultsJson = results.map { r =>
      val j = ResultJSON(r \ "name" toString, r \ "status" toString, r \ "message" toString, r \ "data" map (_.toString) headOption)
      Extraction.decompose(j).asInstanceOf[JObject]
    }
    println(s"Returning json: $resultsJson")
    JObject("results" -> JArray(resultsJson.toList))
  }
}