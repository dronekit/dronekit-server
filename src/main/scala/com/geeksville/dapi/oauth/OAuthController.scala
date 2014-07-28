package com.geeksville.dapi.oauth

import com.geeksville.oauth.OAuthSupport
import com.geeksville.dapi.DroneHubStack

class OAuthController extends DroneHubStack with OAuthSupport {
  val handler = new DapiDataHandler

  post("/access_token") {
    issueAccessToken(handler)
  }

}