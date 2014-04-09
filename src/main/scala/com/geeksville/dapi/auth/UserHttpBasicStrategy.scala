package com.geeksville.dapi.auth

import org.scalatra.auth.strategy.{ BasicAuthStrategy, BasicAuthSupport }
import org.scalatra.auth.{ ScentrySupport, ScentryConfig }
import org.scalatra.{ ScalatraBase }
import com.geeksville.dapi.model.User

/**
 * Uses http basic auth to limit access
 */
class UserHttpBasicStrategy(protected override val app: ScalatraBase, realm: String)
  extends BasicAuthStrategy[User](app, realm) {

  protected def validate(userName: String, password: String): Option[User] = {
    UserPasswordStrategy.getValidatedUser(userName, password)
  }

  protected def getUserId(user: User): String = user.login
}