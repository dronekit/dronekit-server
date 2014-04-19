package com.geeksville.dapi

import org.scalatra.atmosphere._
import grizzled.slf4j.Logging

/**
 * This class is the connection object that gets created when each client connects to the /mission/live atmosphere endpoint
 */
class AtmosphereLive extends AtmosphereClient with Logging {
  def receive = {
    case Connected =>
      info("Atmosphere connected")
    case Disconnected(disconnector, Some(error)) =>
      info(s"Atmosphere Disconnected $disconnector")
    case Error(Some(ex)) =>
      error(s"Atmosphere error $ex")
    case TextMessage(text) =>
      debug(s"Received text from client $text")
    case JsonMessage(json) =>
      debug(s"Received JSON from client $json")
  }
}