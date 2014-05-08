package com.geeksville.dapi.auth

import org.scalatra.ScalatraBase
import org.scalatra.auth.ScentryStrategy
import javax.servlet.http.{ HttpServletResponse, HttpServletRequest }
import org.slf4j.LoggerFactory
import com.geeksville.dapi.model.User
import grizzled.slf4j.Logging

class UserPasswordStrategy(protected val app: ScalatraBase)(implicit request: HttpServletRequest, response: HttpServletResponse)
  extends ScentryStrategy[User] {

  val logger = LoggerFactory.getLogger(getClass)

  override def name: String = "UserPassword"

  private def login = app.params.getOrElse("login", "")
  private def password = app.params.getOrElse("password", "")

  /**
   * *
   * Determine whether the strategy should be run for the current request.
   */
  override def isValid(implicit request: HttpServletRequest) = {
    logger.debug("UserPasswordStrategy: determining isValid: " + (login != "" && password != "").toString())
    login != "" && password != ""
  }

  /**
   *  In real life, this is where we'd consult our data store, asking it whether the user credentials matched
   *  any existing user. Here, we'll just check for a known login/password combination and return a user if
   *  it's found.
   */
  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
    logger.debug("attempting authentication")

    UserPasswordStrategy.getValidatedUser(login, password)
  }

  /**
   * What should happen if the user is currently not authenticated?
   */
  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    //app.redirect("/sessions/new")
  }

}

object UserPasswordStrategy extends Logging {
  /**
   * First we try to find the user by login name, if that fails we check to see if the client provided an
   * email address.
   */
  def getValidatedUser(login: String, password: String) = {
    var userOpt = User.find(login).orElse {
      logger.warn(s"Username $login not found, now searching for email $login")
      User.findByEmail(login)
    }

    userOpt.filter { user =>
      if (user.isPasswordGood(password)) {
        logger.info(s"login succeeded for $login")
        true
      } else {
        logger.warn(s"bad password for $login")
        false
      }
    }
  }
}
