package com.geeksville.dapi.oauth

import org.scalatra.ScalatraBase
import org.scalatra.auth.ScentryStrategy
import javax.servlet.http.{ HttpServletResponse, HttpServletRequest }
import com.geeksville.dapi.model.{DBToken, User}
import grizzled.slf4j.Logging

/**
 * If some other component (i.e. OAuthSupport has previously found our user, then use it)
 * @param app
 */
class OAuthStrategy(protected val app: ScalatraBase)
  extends ScentryStrategy[User] {


  /**
   * Determine whether the strategy should be run for the current request.
   */
  override def isValid(implicit request: HttpServletRequest) = getOAuthUser.isDefined

  private def getOAuthUser(implicit request: HttpServletRequest): Option[User] = {
    Option(request.getAttribute("oauthUser").asInstanceOf[User])
  }

  /**
   *  Return the oauth user which was previous stored for us
   */
  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
    getOAuthUser
  }

}

object OAuthStrategy {
  /**
   * Stuff away a user object into this request, so that OAuthStrat can later find it
   * @param request
   * @param user
   */
  def setOAuthUser(request: HttpServletRequest, user: User) {
    request.setAttribute("oauthUser", user)
  }
}
