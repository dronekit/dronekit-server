package com.geeksville.dapi.oauth

import com.geeksville.dapi.model.User
import scalaoauth2.provider.DataHandler
import scalaoauth2.provider.AccessToken
import scalaoauth2.provider.AuthInfo

class DapiDataHandler extends DataHandler[User] {
  def validateClient(clientId: String, clientSecret: String, grantType: String): Boolean = ???

  def findUser(username: String, password: String): Option[User] = ???

  def createAccessToken(authInfo: AuthInfo[User]): AccessToken = ???

  def getStoredAccessToken(authInfo: AuthInfo[User]): Option[AccessToken] = ???

  def refreshAccessToken(authInfo: AuthInfo[User], refreshToken: String): AccessToken = ???

  def findAuthInfoByCode(code: String): Option[AuthInfo[User]] = ???

  def findAuthInfoByRefreshToken(refreshToken: String): Option[AuthInfo[User]] = ???

  def findClientUser(clientId: String, clientSecret: String, scope: Option[String]): Option[User] = ???

  def findAccessToken(token: String): Option[AccessToken] = ???

  def findAuthInfoByAccessToken(accessToken: AccessToken): Option[AuthInfo[User]] = ???

}
