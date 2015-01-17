package com.geeksville.dapi.oauth

import com.geeksville.dapi.model.User
import scala.collection.mutable
import scala.util.Random
import scalaoauth2.provider._
import grizzled.slf4j.Logging
import java.sql.Timestamp
import com.geeksville.dapi.model.DBToken
import com.github.aselab.activerecord.dsl._

class DapiDataHandler extends DataHandler[User] with Logging {

  implicit private def toAccessToken(t: DBToken) = {
    val expiresIn = t.expire.map { e =>
      (e.getTime - System.currentTimeMillis) / 1000L // # of seconds in the future
      // FIXME - handle negative expire times
    }
    AccessToken(t.accessToken.toString, t.refreshToken.map(_.toString), t.scope, expiresIn, t.createdAt)
  }

  implicit private def toAuthInfo(t: DBToken): AuthInfo[User] = {
    val redirectUri = None // FIXME
    AuthInfo(t.user, t.clientId, t.scope, redirectUri)
  }

  def validateClient(clientId: String, clientSecret: String, grantType: String): Boolean = {
    warn(s"FIXME: implement validateClient id=$clientId, secret=$clientSecret, grantType=$grantType")
    true
  }

  def findUser(login: String, password: String): Option[User] = {
    User.findByLoginOrEmail(login).flatMap { user =>
      if (user.isPasswordGood(password)) {
        info(s"login succeeded for $login")
        Some(user)
      } else {
        warn(s"bad password for $login")
        None
      }
    }
  }

  def createAccessToken(authInfo: AuthInfo[User]): AccessToken = {
    warn(s"Creating access token for $authInfo")
    authInfo.user.createToken(authInfo.clientId, authInfo.scope)
  }

  def getStoredAccessToken(authInfo: AuthInfo[User]): Option[AccessToken] = {
    warn(s"Getting access token for $authInfo")
    authInfo.user.getTokenByClientId(authInfo.clientId).map(toAccessToken)
  }

  def refreshAccessToken(authInfo: AuthInfo[User], refreshToken: String): AccessToken = {
    val dbToken = DBToken.findByRefreshToken(refreshToken).getOrElse(throw new InvalidToken("refreshToken not found"))
    if(authInfo.clientId != dbToken.clientId)
      throw new InvalidClient("Token not for your client")

    dbToken.refreshAccessToken()
    dbToken
  }

  /**
   * Look at the live list of short lived authorization codes - to find the AuthInfo that corresponds to that code
   * @param code
   * @return
   */
  def findAuthInfoByCode(code: String): Option[AuthInfo[User]] = DapiDataHandler.authorizationCodes.get(code)

  def findAuthInfoByRefreshToken(refreshToken: String): Option[AuthInfo[User]] =
    DBToken.findByRefreshToken(refreshToken).map(toAuthInfo)

  def findClientUser(clientId: String, clientSecret: String, scope: Option[String]): Option[User] =
    throw new Exception("We don't plan to support Client credentials")

  def findAccessToken(token: String): Option[AccessToken] = {
    DBToken.findByAccessToken(token).map(toAccessToken)
  }

  def findAuthInfoByAccessToken(token: AccessToken): Option[AuthInfo[User]] = {
    DBToken.findByAccessToken(token.token).map(toAuthInfo)
  }

}

object DapiDataHandler {

  /** A short lived map for users who have completed the first screen of granting permission for API access
    *
    * FIXME - make these expire after a little while
    */
  val authorizationCodes = mutable.HashMap[String, AuthInfo[User]]()


}
