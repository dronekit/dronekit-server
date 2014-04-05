package com.geeksville.dapi.auth

import org.scalatra.auth.{ ScentryConfig, ScentrySupport }
import org.scalatra.{ ScalatraBase }
import org.slf4j.LoggerFactory
import com.geeksville.dapi.model.User
import com.geeksville.scalatra.ControllerExtras

trait AuthenticationSupport extends ScalatraBase with ScentrySupport[User] with ControllerExtras {
  self: ScalatraBase =>

  protected def fromSession = { case id: String => User.find(id).get }
  protected def toSession = { case usr: User => usr.login }

  // For now we just keep the defaults
  override protected val scentryConfig = (new ScentryConfig {}).asInstanceOf[ScentryConfiguration]
  /* protected val scentryConfig = (new ScentryConfig {
    override val login = "/sessions/new"
  }).asInstanceOf[ScentryConfiguration]
  */

  /// Subclasses can call this method to ensure that the request is aborted if the user is not logged in
  protected def requireLogin() = {
    if (!isAuthenticated) {
      // For HTML: redirect(scentryConfig.login)
      haltUnauthorized()
    }
  }

  /**
   * If an unauthenticated user attempts to access a route which is protected by Scentry,
   * run the unauthenticated() method on the UserPasswordStrategy.
   */
  override protected def configureScentry = {
    // Set the callback for what to do if a user is not authenticated
    scentry.unauthenticated {
      // DISABLED - we expect to talk only to JSON clients - so no redirecting to login pages.
      // scentry.strategies("UserPassword").unauthenticated()
      haltUnauthorized()
    }
  }

  /**
   * Register auth strategies with Scentry. Any controller with this trait mixed in will attempt to
   * progressively use all registered strategies to log the user in, falling back if necessary.
   */
  override protected def registerAuthStrategies = {
    scentry.register("UserPassword", app => new UserPasswordStrategy(app))
    scentry.register("RememberMe", app => new RememberMeStrategy(app))
  }

}