package com.geeksville.dapi.oauth

import com.geeksville.dapi.model.{DBToken, User}
import com.geeksville.http.HttpClient
import com.geeksville.oauth.OAuthSupport
import com.geeksville.dapi.DroneHubStack
import scala.xml._
import scalaoauth2.provider.AuthInfo


class OAuthController extends DroneHubStack with OAuthSupport {
  val handler = new DapiDataHandler


  post("/access_token") {
    /*
    FIXME - implement scopes (use existing user_read etc...) plus offline for allowing refresh tokens to work
    FIXME - check expiration of tokens
    FIXME - have scalatra auth do the right thing when user is logged in _only_ via a token
     */
    issueAccessToken(handler)
  }

  /**
   * Show the user the HTML page where we ask them for their consent to let a particular
   * app see their data.  we will eventually redirect the user
   * to redirect_uri (with either code or an error msg provided)
   *
   * A number of query parameters should be included in this get:
   * redirect_uri: where we send the user at the end of this flow
   * client_id: the appid for this app
   * response_type: must be 'code'
   * scope: a space separated list of scopes requested
   * state: an option set string of data which will be passed back to the redirect_uri
   *
   * When we eventually redirect the user to redirect_uri the following query parameters will be added:
   * code: the code that should be submitted to /access_token to get your access token
   * error: if the user declined to approve the app
   * state: any state which was passed in with the initial request
   */
  get("/auth") {

    val redirect = params.get("redirect_uri").getOrElse(haltBadRequest("needs redirect_uri"))
    val clientId = params.get("client_id").getOrElse(haltBadRequest("needs client_id"))
    val responseType = params.get("response_type").getOrElse(haltBadRequest("needs response_type"))
    val scope = params.get("scope").getOrElse(haltBadRequest("needs scope"))
    val stateOpt = params.get("state")

    requireLogin() // FIXME - redirect the user to the droneshare login page?

    if(responseType != "code")
      haltBadRequest("Invalid response_type")

    // FIXME - IMPORTANT - check that the redirect URL matches what is stored for this apikey

    setSession("preAuthInfo", AuthInfo[User](user, clientId, Some(scope), Some(redirect)))
    setSession("preAuthState", stateOpt)

    val appName = "FIXME-Appname-"

    // FIXME - validate scope names
    val scopeNames = Seq("user read", "mission write")

    val resp = <html><body>
      <form method="POST">
      { appName } would like to be granted the following permissions on your behalf:
      <ul>
        { scopeNames.map { n => <li> { n } </li> } }
      </ul>

        <input type="submit" name="notApproved" value="NO">Hell no!</input>
        <input type="submit" name="approved" value="OK">Sure!</input>
      </form></body></html>

    info(s"Returning $resp")
    resp
  }

  /**
   * A non public(ish) URL which handles the POST when the user finishes the HTML form for app approval
   */
  post("/auth") {
    // FIXME - check for something that proves we provided the form (IMPORTANT!)

    val authInfo: AuthInfo[User] = getSession("preAuthInfo").getOrElse(haltUnauthorized("Oauth info not found"))
    val stateOpt: Option[String] = getSession("preAuthState").getOrElse(haltUnauthorized("state info not found"))

    val baseUrl = authInfo.redirectUri.getOrElse(haltBadRequest("No redirect specified"))
    var responseParams = if(params.get("approved").isDefined) {
      val code = DBToken.createRandomCode()
      DapiDataHandler.authorizationCodes += (code -> authInfo)

      Seq("code" -> code)
    }
    else {
      Seq("error" -> "no approval")
    }

    stateOpt.foreach { state =>
      responseParams = ("state" -> state) +: responseParams
    }

    val url  = HttpClient.addQueryString(baseUrl, responseParams: _*)
    info(s"Redirecting to $url")
    redirect(url)
  }
}
