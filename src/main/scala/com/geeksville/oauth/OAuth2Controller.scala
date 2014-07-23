package com.geeksville.oauth

import scalaoauth2.provider.DataHandler
import org.scalatra.ScalatraServlet
import com.geeksville.scalatra.ControllerExtras
import scalaoauth2.provider.TokenEndpoint
import scalaoauth2.provider.AuthorizationRequest

class ScalatraOAuth2Controller[U](createHandler: () => DataHandler[U]) extends ScalatraServlet with ControllerExtras {
  post("/access_token") {
    request
    // issueAccessToken(createHandler())
  }

  def authRequest = {
    val hdrs = request.headers.map { case (k, v) => k -> Seq(v) }.toMap
    val params = request.parameters.map { case (k, v) => k -> Seq(v) }.toMap
    AuthorizationRequest(hdrs, params)
  }

  /*
    implicit def play2oauthRequest(request: RequestHeader): AuthorizationRequest = {
    AuthorizationRequest(request.headers.toMap, request.queryString)
  }
    
  /**
   * Issue access token in DataHandler process and return the response to client.
   *
   * @param dataHandler Implemented DataHander for register access token to your system.
   * @param request Playframework is provided HTTP request interface.
   * @tparam A play.api.mvc.Request has type.
   * @return Request is successful then return JSON to client in OAuth 2.0 format.
   *         Request is failed then return BadRequest or Unauthorized status to client with cause into the JSON.
   */
  def issueAccessToken[A, U](dataHandler: DataHandler[U]): Result = {
    TokenEndpoint.handleRequest(request, dataHandler) match {
      case Left(e) if e.statusCode == 400 => BadRequest(responseOAuthErrorJson(e)).withHeaders(responseOAuthErrorHeader(e))
      case Left(e) if e.statusCode == 401 => Unauthorized(responseOAuthErrorJson(e)).withHeaders(responseOAuthErrorHeader(e))
      case Right(r) => Ok(Json.toJson(responseAccessToken(r))).withHeaders("Cache-Control" -> "no-store", "Pragma" -> "no-cache")
    }
  }
*/ }