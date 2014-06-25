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
import com.geeksville.util.Using._
import com.geeksville.dataflash.DFReader
import java.io.PipedOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.PipedInputStream
import com.geeksville.util.ThreadTools

case class ResultJSON(name: String, status: String, message: String, data: Option[String])

class AnalysisFactory(bytes: Array[Byte], val isText: Boolean) extends Logging {

  val toolPath = "/home/kevinh/development/drone/ardupilot/Tools/LogAnalyzer/LogAnalyzer.py"
  val toolArgs = "-q -s -x - -" // quiet, xml to std out, read from stdin

  private def binToText() = {
    val reader = new DFReader
    warn(s"Converting .bin to .log")
    val messages = reader.parseBinary(new ByteArrayInputStream(bytes))

    // Return our messages as valid log lines through a piped inputstream
    val ins = new PipedInputStream()
    val outs = new PipedOutputStream(ins)

    ThreadTools.start("bin-to-log") { () =>
      using(new PrintWriter(new OutputStreamWriter(outs))) { out =>
        reader.toText(out)
      }
    }

    ins
  }

  def toJSON(): Option[JObject] = {
    warn("Running analysis tool")

    // If we need to convert from binary, do that in a child thread
    val stream = if (isText)
      new ByteArrayInputStream(bytes)
    else
      binToText()

    using(stream) { instream =>
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
      val j = ResultJSON(r \ "name" text, r \ "status" text, r \ "message" text, r \ "data" map (_.text) headOption)
      Extraction.decompose(j).asInstanceOf[JObject]
    }
    println(s"Returning json: $resultsJson")
    JObject("results" -> JArray(resultsJson.toList))
  }
}