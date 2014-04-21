package com.geeksville.dapi

import org.scalatra.atmosphere._
import grizzled.slf4j.Logging
import com.geeksville.dapi.model.User

/**
 * This class is the connection object that gets created when each client connects to the /mission/live atmosphere endpoint
 *
 * if the user is logged in they will be provided.
 */
class PlatformAtmosphereClient(val user: Option[User]) extends AtmosphereClient with Logging {

  def receive = {
    case Connected =>
      info(s"Atmosphere $uuid connected as $user")
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

/**
 * This is used for clients that are showing live vehicle traffic
 */
class AtmosphereLive(user: Option[User]) extends PlatformAtmosphereClient(user) {
}

class AdminLive(user: Option[User]) extends PlatformAtmosphereClient(user) {
  if (!user.isDefined)
    throw new Exception("Not logged in")

  if (!user.get.isAdmin)
    throw new Exception("Not an admin")
}