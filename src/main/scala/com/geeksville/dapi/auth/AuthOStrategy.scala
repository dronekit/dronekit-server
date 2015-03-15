package com.geeksville.dapi.auth

import com.auth0.Auth0User
import com.geeksville.dapi.model.User
import org.scalatra.ScalatraBase
import org.scalatra.auth.ScentryStrategy
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import com.geeksville.dapi.model.{DBToken, User}
import grizzled.slf4j.Logging

/**
 * If the frontend has a Auth0 user ID that we recognize, then use it
 * @param app
 */
class Auth0Strategy(protected val app: ScalatraBase)
  extends ScentryStrategy[User] {


  /**
   * Determine whether the strategy should be run for the current request.
   */
  override def isValid(implicit request: HttpServletRequest) = {
    val r = Auth0User.get(request) != null
    println(s"*** Auth0 says $r")
    r
  }

  /**
   * Return the oauth user which was previous stored for us
   */
  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {

    println("*** Authenticating with auth0")
    Option(Auth0User.get(request)).map { auser =>
      User.findOrCreateExternalUser(auser.getUserId, User.auth0ProviderCode, email = Option(auser.getEmail))
    }
  }

}

