package com.geeksville.dapi.auth

import com.geeksville.hull.Hull
import io.hull.util.HullUtils
import org.scalatra.ScalatraBase
import org.scalatra.auth.ScentryStrategy
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import com.geeksville.dapi.model.{DBToken, User}
import grizzled.slf4j.Logging

/**
 * If the frontend has a hull user ID that we recognize, then use it
 * @param app
 */
class HullStrategy(protected val app: ScalatraBase)
  extends ScentryStrategy[User] {


  /**
   * Determine whether the strategy should be run for the current request.
   */
  override def isValid(implicit request: HttpServletRequest) = getHullCookie.isDefined

  private def getHullCookie(implicit request: HttpServletRequest): Option[String] = {
    // FIXME - possibly a bit expensive
    val cookies = Option(request.getCookies).getOrElse(Array.empty)
    cookies.find { c =>
      c.getName == "hull_" + Hull.appId
    }.map(_.getValue)
  }

  /**
   * Return the oauth user which was previous stored for us
   */
  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
    getHullCookie.flatMap { cookie =>
      Option(HullUtils.authenticateUser(cookie, Hull.appSecret)).map { hullId =>
        User.findOrCreateExternalUser(hullId, User.hullProviderCode)
      }
    }
  }

}

