package com.geeksville.dapi.auth

import org.scalatra.auth.strategy.{ BasicAuthStrategy, BasicAuthSupport }
import org.scalatra.auth.{ ScentrySupport, ScentryConfig }
import org.scalatra.{ ScalatraBase }
import com.geeksville.dapi.model.User
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Uses http basic auth to limit access
 */
class UserHttpBasicStrategy(app: ScalatraBase, realm: String)
  extends BasicAuthStrategy[User](app, realm) {

  override protected def validate(userName: String, password: String)(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
    UserPasswordStrategy.getValidatedUser(userName, password)
  }

  override protected def getUserId(user: User)(implicit request: HttpServletRequest, response: HttpServletResponse): String = user.login
}