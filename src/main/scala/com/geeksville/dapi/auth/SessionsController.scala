package com.geeksville.dapi.auth

import com.geeksville.dapi.DroneHubStack
import com.ridemission.rest.JString
import org.json4s.Extraction
import org.json4s.Formats
import org.json4s.DefaultFormats
import com.geeksville.json.GeeksvilleFormats
import com.geeksville.json.ActiveRecordSerializer
import org.scalatra.swagger.SwaggerSupport
import org.scalatra.swagger.Swagger
import com.geeksville.dapi.model.User
import org.scalatra.swagger.StringResponseMessage
import org.scalatra.CorsSupport
import com.geeksville.dapi.model.UserJson
import com.geeksville.dapi.model.UserSerializer
import com.geeksville.util.Using._
import com.geeksville.mailgun.MailgunClient
import com.geeksville.scalatra.ScalatraTools
import com.github.aselab.activerecord.RecordInvalidException
import com.geeksville.dapi.Global
import com.geeksville.dapi.MailTools

class SessionsController(implicit val swagger: Swagger) extends DroneHubStack with CorsSupport with SwaggerSupport {

  override protected val applicationName = Some("api/v1/auth")
  protected lazy val applicationDescription = s"Session operations (login, logout, etc...)"

  protected def userJsonFormat = super.jsonFormats + new UserSerializer(Option(user), true)

  // Before every action runs, set the content type to be in JSON format.
  before() {
    contentType = formats("json")
  }

  before("/login") {
    logger.info("SessionsController: checking whether to run RememberMeStrategy: " + !isAuthenticated)

    // If not already authed, try to auth implicitly using their cookie
    if (!isAuthenticated) {
      scentry.authenticate("Remember")
    }
  }

  /*
    WE don't talk html
    
    get("/new") {
    if (isAuthenticated) redirect("/")
    contentType = "text/html"
    ssp("/sessions/new")
    }
    */

  private def doLogin() = {

    // Make sure this app is allowed to login users
    requireServiceAuth("user/login")

    val u = if (user != null && user.isAdmin) {
      val login = params("login")
      warn(s"Admin $user is impersonating $login")
      val newuser = User.findByLoginOrEmail(login).getOrElse(haltNotFound(s"Can't impersonate: $login not found"))
      user = newuser // Mark the session that this user is logged in
      rememberMe.setCookie(newuser)
      Some(newuser)
    } else {
      val u = scentry.authenticate("Password")

      // User just tried to login - if login has failed tell them they can't access the site
      if (!u.isDefined)
        haltForbidden("Invalid login")
      /*
    If we were using HTML this is what we would give
    
    if (isAuthenticated) {
      redirect("/")
    } else {
      redirect("/sessions/new")
    }
    * */
      u
    }

    Extraction.decompose(u)(userJsonFormat)
  }

  private lazy val loginOp = (
    apiOperation[User]("login") summary "POST your login parameters to this URL"
    parameters (
      formParam[String]("login").description("The loginName for the account"),
      formParam[String]("password").description("The password for the account"))
      responseMessages (
        StringResponseMessage(403, "login attempt failed")))

  post("/login", operation(loginOp)) {
    doLogin()
  }

  /**
   * NOTE: For most applications this GET method should not be used - but it does make for easier browser based testing
   * get("/login") {
   * doLogin()
   * }
   */

  post("/pwreset/:login") {
    val u = User.find(params("login")).getOrElse(haltNotFound("login not found"))
    if (!u.email.isDefined)
      haltBadRequest("No email address known for this account, sorry.")
    else {
      u.beginPasswordReset()
      MailTools.sendPasswordReset(u)
      "Password reset started"
    }
  }

  /**
   * This URL is used to confirm password reset email acknowledgement.  If someone starts a password
   * reset we will send the user an email that asks them to visit this link.
   */
  post("/pwreset/:login/:token") {
    try {
      val login = params("login")
      warn(s"Doing password reset confirm for $login")
      val u = User.find(login).getOrElse(haltNotFound("login not found"))
      val token = params("token")

      // The body is expected to contain the new user password - FIXME, perhaps I shouldn't have sent the string form the client with
      // quotes
      val newPassword = request.body.substring(1, request.body.length - 1)

      debug(s"Doing a password reset, new password $newPassword")
      u.confirmPasswordReset(token, newPassword)
      loginAndReturn(u)
    } catch {
      case ex: Exception =>
        haltBadRequest(ex.getMessage)
    }
  }

  /**
   * Frontend submits this to confirm email address
   */
  post("/emailconfirm/:login/:token") {
    val u = User.find(params("login")).getOrElse(haltNotFound("login not found"))
    val token = params("token")

    u.confirmVerificationCode(token)
  }

  private lazy val userOp = (apiOperation[User]("user")
    summary "Return the user object"
    responseMessages (
      StringResponseMessage(401, "if user is not logged in")))

  /**
   * Return the user-object if we are logged in, else 401
   */
  get("/user", operation(userOp)) {
    requireLogin()
    Extraction.decompose(user)(userJsonFormat)
  }

  private lazy val logoutOp = apiOperation[String]("logout") summary "Logout the current user"
  // Never do this in a real app. State changes should never happen as a result of a GET request. However, this does
  // make it easier to illustrate the logout code.
  post("/logout", operation(logoutOp)) {
    // Only allow logout if we can login
    requireServiceAuth("user/login")

    scentry.logout()

    // NOT USING HTML
    // redirect("/")
    JString("Logged out")
  }

  private lazy val createOp = apiOperation[User]("create") summary "Creates a new user record" parameter (bodyParam[UserJson])

  private def loginAndReturn(r: User) = {
    user = r // Mark the session that this user is logged in
    rememberMe.setCookie(user)
    Extraction.decompose(r)(userJsonFormat)
  }

  /// Subclasses can provide suitable behavior if they want to allow PUTs to /:id to result in creating new objects
  post("/create", operation(createOp)) {

    // Make sure this app is allowed to create users
    requireServiceAuth("user/create")

    val u = parsedBody.extract[UserJson]

    val id = u.login

    if (!u.email.isDefined)
      haltBadRequest("email required")

    val password = u.password.getOrElse {
      haltBadRequest("insuffient password")
    }

    val r = createUserAndWelcome(id, u.password.get, u.email, u.fullName)
    loginAndReturn(r)
  }

}