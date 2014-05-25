package com.geeksville.dapi.auth

import org.scalatra.auth.{ ScentryConfig, ScentrySupport }
import org.scalatra.{ ScalatraBase }
import org.slf4j.LoggerFactory
import com.geeksville.dapi.model.User
import com.geeksville.scalatra.ControllerExtras
import org.scalatra.auth.strategy.BasicAuthSupport
import java.util.Date
import com.newrelic.api.agent.NewRelic
import com.geeksville.util.Using._
import com.geeksville.mailgun.MailgunClient
import com.geeksville.scalatra.ScalatraTools
import com.geeksville.dapi.Global
import com.github.aselab.activerecord.RecordInvalidException
import org.scalatra.json.NativeJsonSupport
import com.geeksville.dapi.MailTools

trait AuthenticationSupport extends ScalatraBase with ScentrySupport[User] with BasicAuthSupport[User] with ControllerExtras with NativeJsonSupport {
  self: ScalatraBase =>

  protected def fromSession = {
    case id: String =>
      User.find(id).get
  }
  protected def toSession = { case usr: User => usr.login }

  protected def rememberMe = scentry.strategies("Remember").asInstanceOf[RememberMeStrategy]

  val realm = "Drone"

  // For now we just keep the defaults
  override protected val scentryConfig = (new ScentryConfig {}).asInstanceOf[ScentryConfiguration]
  /* protected val scentryConfig = (new ScentryConfig {
    override val login = "/sessions/new"
  }).asInstanceOf[ScentryConfiguration]
  */

  /**
   * Attempt to log in the user (but do not throw if they are not logged in)
   */
  protected def tryLogin(names: String*) = {
    val r = scentry.authenticate(names: _*)
    r.foreach { u =>
      NewRelic.setUserName(u.login)

      // FIXME - we really need a scheme to cache all these db objects
      u.lastLoginAddr = request.getRemoteAddr
      u.lastLoginDate = new Date
      u.save
    }
    r
  }

  /// Subclasses can call this method to ensure that the request is aborted if the user is not logged in
  protected def requireLogin(names: String*) = tryLogin(names: _*).getOrElse {
    logger.debug("Aborting request: user not logged in")
    haltUnauthorized("You are not logged in")
  }

  /**
   * Aborts request if user doesn't have admin permissions
   */
  protected def requireAdmin(names: String*) = {
    val r = requireLogin(names: _*)
    if (!r.isAdmin) {
      logger.error(s"Hack attempt? $r is not an admin!")
      haltUnauthorized("Insufficient permissions")
    }
    r
  }

  /**
   * If an unauthenticated user attempts to access a route which is protected by Scentry,
   * run the unauthenticated() method on the UserPasswordStrategy.
   */
  override protected def configureScentry = {
    // Set the callback for what to do if a user is not authenticated
    scentry.unauthenticated {
      // DISABLED - we expect to talk only to JSON clients - so no redirecting to login pages.
      scentry.strategies("Password").unauthenticated()
      //scentry.strategies("Basic").unauthenticated()
    }
  }

  /**
   * Register auth strategies with Scentry. Any controller with this trait mixed in will attempt to
   * progressively use all registered strategies to log the user in, falling back if necessary.
   */
  override protected def registerAuthStrategies = {
    // We are temporarily guarding the entire site with http basic
    // scentry.register("Basic", app => new UserHttpBasicStrategy(app, realm))

    scentry.register("Password", app => new UserPasswordStrategy(app))
    scentry.register("Remember", app => new RememberMeStrategy(app))
  }

  //
  // Stuff for account creation
  //

  def createUserAndWelcome(login: String, password: String, fullName: Option[String], email: Option[String]) = {

    if (User.find(login).isDefined)
      haltConflict("login already exists")

    val r = try {
      User.create(login, password, email, fullName)
    } catch {
      // Failed validation
      case ex: RecordInvalidException =>
        haltBadRequest(ex.getMessage)
    }
    try {
      if (email.isDefined)
        MailTools.sendWelcomeEmail(r)
      r
    } catch {
      case ex: Exception =>
        // If we failed sending the email delete the new user record
        r.delete()

        throw ex
    }
  }
}