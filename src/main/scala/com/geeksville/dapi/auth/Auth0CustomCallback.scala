package com.geeksville.dapi.auth

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

import com.auth0.Auth0ServletCallback
import grizzled.slf4j.Logging

/**
 * Created by kevinh on 3/16/15.
 */
class Auth0CustomCallback extends Auth0ServletCallback with Logging {

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    debug("Auth0 callback invoked!")

    val store = getNonceStorage(req)
    if (store.getState == null) {
      debug("Initing nonce storage")
      store.setState("unused")
    }

    super.doGet(req, resp)
  }

  override def onSuccess(req: HttpServletRequest, resp: HttpServletResponse) {
    info("Auth0 callback success!")
    super.onSuccess(req, resp)
  }

  override def onFailure(req: HttpServletRequest, resp: HttpServletResponse, ex: Exception) {
    error(s"Auth0 callback failure: $ex")
    super.onFailure(req, resp, ex)
  }
}
