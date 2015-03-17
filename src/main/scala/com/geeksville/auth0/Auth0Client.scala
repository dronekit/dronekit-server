package com.geeksville.auth0

import com.auth0.Auth0User
import grizzled.slf4j.Logging
import us.monoid.web.{JSONResource, Resty}

/** An attempt to migrate away from the crufty lib provided by auth0
  *
 * Created by kevinh on 3/17/15.
 */
class Auth0Client(private val auth0domain: String = "3dr.auth0.com") extends Logging {
  /**
   * Override this method to specify a different Resty client. For example, if
   * you want to add a proxy, this would be the place to set it
   *
   *
   * @return { @link Resty} that will be used to perform all requests to Auth0
   */
  protected val resty: Resty = new Resty

  private def getUri(path: String): String = {
    return String.format("https://%s%s", auth0domain, path)
  }



  def fetchUser(accessToken: String): Auth0User = {
    val userInfoUri: String = getUserInfoUri(accessToken)

    val json: JSONResource = resty.json(userInfoUri)
    debug(s"Received json user from auth0: $json")
    new Auth0User(json.toObject)
  }

  private def getTokenUri: String = getUri("/oauth/token")

  private def getUserInfoUri(accessToken: String): String = getUri("/userinfo?access_token=" + accessToken)
}

object Auth0Client {
  val userSessionKey = "user"
}
