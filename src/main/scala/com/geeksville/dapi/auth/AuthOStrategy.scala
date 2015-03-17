package com.geeksville.dapi.auth

import com.auth0.Auth0User
import com.geeksville.auth0.Auth0Client
import com.geeksville.dapi.model.User
import org.scalatra.ScalatraBase
import org.scalatra.auth.ScentryStrategy
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import com.geeksville.dapi.model.{DBToken, User}
import grizzled.slf4j.Logging
import scala.collection.JavaConverters._

/**
 * If the frontend has a Auth0 user ID that we recognize, then use it
 * @param app
 *
 */
class Auth0Strategy(protected val app: ScalatraBase)
  extends ScentryStrategy[User] with Logging {

  /// For archival purposes this is the custom user migration JS code we have installed at
  /// https://manage.auth0.com/#/connections/database/legacy-backend-user-db/plug
  private val jsCode =
    """
      |function login (email, password, callback) {
      |
      |  request.post({
      |    url:  'https://api.3drobotics.com/api/v1/auth/login',
      |    // url:  'http://66.91.200.15:8080/api/v1/auth/login',
      |    form: {
      |      login: email,
      |      password: password
      |    },
      |    headers: {
      |      Authorization: 'DroneApi apikey="8dd7514c.auth0migrate"'
      |    }
      |    //for more options check:
      |    //https://github.com/mikeal/request#requestoptions-callback
      |  }, function (err, response, body) {
      |
      |    console.log('In 3DR callback');
      |    if (err) return callback(err);
      |
      |    if (response.statusCode === 403 || response.statusCode === 401)
      |      return callback(
      |        new WrongUsernameOrPasswordError(email, "3DR said invalid username/password"));
      |
      |    if (response.statusCode < 200 || response.statusCode > 299)
      |      return callback(new ValidationError(response.statusCode,
      |        "3DR Rejected " + response.statusCode));
      |
      |    console.log('got body: ' + body);
      |    // callback(new Error("disabled"));
      |    var user = JSON.parse(body);
      |
      |    callback(null,   {
      |      user_id:     user.login,
      |      nickname:    ('fullName' in user) ? user.fullName : email,
      |      email:       ('email' in user) ? user.email : email
      |    });
      |
      |  });
      |
      |}
    """.stripMargin

  val auth0client = new Auth0Client()

  /**
   * Look for an auth0 access_token in either the X-Auth-3DR header (recommended) or the
   * auth_3dr query string (for easy testing).  If found, we will then attempt to make a auth0 user
   * object from that access token.
   *
   * @param request
   * @return
   */
  private def getUserFromHeaders(implicit request: HttpServletRequest): Option[Auth0User] = {
    val inHeader = request.getHeaders("X-Auth-3DR").asScala.toTraversable.headOption
    def inQueryParams = app.params.get("auth-3dr")

    val token = inHeader.orElse(inQueryParams)

    debug(s"Received auth token in header: $token")
    token.map(auth0client.fetchUser)
  }

  /**
   * Determine whether the strategy should be run for the current request.
   */
  override def isValid(implicit request: HttpServletRequest) = {
    val hasUser = if(request.getSession.getAttribute(Auth0Client.userSessionKey) != null)
      true
    else {
      val u = getUserFromHeaders(request)
      if(u.isDefined) {
        request.getSession.setAttribute(Auth0Client.userSessionKey, u.get)
        true
      }
      else
        false
    }

    debug(s"Auth0 says $hasUser")
    hasUser
  }

  /**
   * Return the oauth user which was previous stored for us
   */
  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {

    debug("Authenticating with auth0")
    Option(Auth0User.get(request)).map { auser =>
      User.findOrCreateExternalUser(auser.getUserId, User.auth0ProviderCode, email = Option(auser.getEmail))
    }
  }

}

