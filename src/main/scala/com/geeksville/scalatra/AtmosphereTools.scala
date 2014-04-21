package com.geeksville.scalatra

import org.json4s.JsonDSL._
import org.scalatra.atmosphere.AtmosphereClient
import org.scalatra.atmosphere.JsonMessage
import org.json4s.JsonAST._
import scala.concurrent.ExecutionContext

object AtmosphereTools {

  /**
   * the lowest common denominator for atmosphere seems to be Netscape style eventstreams.  So always use that format for messages
   */
  def broadcast(route: String, typ: String, data: JValue)(implicit context: ExecutionContext) {

    val o = ("type" -> typ) ~ ("data" -> data)
    AtmosphereClient.broadcast(route, JsonMessage(o))
  }
}