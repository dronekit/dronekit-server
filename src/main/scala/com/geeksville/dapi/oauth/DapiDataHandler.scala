package com.geeksville.dapi.oauth

import com.geeksville.dapi.model.User
import scalaoauth2.provider.DataHandler
import scalaoauth2.provider.AccessToken
import scalaoauth2.provider.AuthInfo
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
    error(s"FIXME: implement validateClient id=$clientId, secret=$clientSecret, grantType=$grantType")
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
    authInfo.user.createToken(authInfo.clientId)
  }

  def getStoredAccessToken(authInfo: AuthInfo[User]): Option[AccessToken] = {
    warn(s"Getting access token for $authInfo")
    authInfo.user.getToken(authInfo.clientId).map(toAccessToken)
  }

  def refreshAccessToken(authInfo: AuthInfo[User], refreshToken: String): AccessToken = ???

  def findAuthInfoByCode(code: String): Option[AuthInfo[User]] = ???

  def findAuthInfoByRefreshToken(refreshToken: String): Option[AuthInfo[User]] = ???

  def findClientUser(clientId: String, clientSecret: String, scope: Option[String]): Option[User] = ???

  def findAccessToken(token: String): Option[AccessToken] = {
    DBToken.findByAccessToken(token).map(toAccessToken)
  }

  def findAuthInfoByAccessToken(token: AccessToken): Option[AuthInfo[User]] = {
    DBToken.findByAccessToken(token.token).map(toAuthInfo)
  }

}

