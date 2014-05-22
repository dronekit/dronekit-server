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

    val user = scentry.authenticate("Password")

    // User just tried to login - if login has failed tell them they can't access the site
    if (!user.isDefined)
      haltForbidden("Invalid login")
    /*
    If we were using HTML this is what we would give
    
    if (isAuthenticated) {
      redirect("/")
    } else {
      redirect("/sessions/new")
    }
    * */

    Extraction.decompose(user)(userJsonFormat)
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

  post("/pwreset") {
    requireLogin().beginPasswordReset()
    "Password reset started"
  }

  /**
   * This URL is used to confirm password reset email acknowledgement.  If someone starts a password
   * reset we will send the user an email that asks them to visit this link.
   */
  post("pwreset/:login/:token") {
    val u = User.find(params("login")).getOrElse(haltNotFound())
    val token = params("token")

    // The body is expected to contain the new user password
    val newPassword = parsedBody.extract[JString].s

    u.confirmPasswordReset(token, newPassword)
  }

  /**
   * Frontend submits this to confirm email address
   */
  post("emailconfirm/:login/:token") {
    val u = User.find(params("login")).getOrElse(haltNotFound())
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

  val hostname = "alpha.droneshare.com"
  val senderEmail = "platform-support@3drobotics.com"
  val appName = "Droneshare"

  def sendWelcomeEmail(u: User) {
    using(new MailgunClient()) { client =>
      val fullname = u.fullName.getOrElse(u.login)
      val confirmDest = "http://$hostname/confirm/${u.login}/${u.verificationCode}"

      // FIXME - make HTML email and also use a md5 or somesuch to hash username+emailaddr
      val bodyText =
        s"""
        Dear $fullname,
        
        Your new account on Droneshare is now mostly ready.  The only step that remains is to confirm
        your email address.  To confirm your email please visit the following URL:
        
        $confirmDest 
        
        Thank you for joining our beta-test.  Any feedback is always appreciated.  Please email
        $senderEmail.
        """

      val r = client.sendTo(senderEmail, u.email.get, s"Welcome to $appName",
        bodyText, testing = ScalatraTools.isTesting)
      debug("Mailgun reply: " + compact(render(r)))
    }
  }

  def sendPassowrdReset(u: User) {
    using(new MailgunClient()) { client =>
      val fullname = u.fullName.getOrElse(u.login)
      val code = u.beginPasswordReset()
      val confirmDest = "http://$hostname/reset/${u.login}/$code"

      // FIXME - make HTML email and also use a md5 or somesuch to hash username+emailaddr
      val bodyText =
        s"""
        Dear $fullname,
        
        Someone has begun a password reset procedure on your account.  If _you_ started this
        password request, then please visit the following URL to select your new password.  If
        you did not request a new password, you can ignore this email.
        
        $confirmDest 
        
        Thank you for using Droneshare.  Any feedback is always appreciated.  Please email
        $senderEmail.
        
        """

      val r = client.sendTo(senderEmail, u.email.get, s"$appName password reset",
        bodyText, testing = ScalatraTools.isTesting)
      debug("Mailgun reply: " + compact(render(r)))
    }
  }

  /// Subclasses can provide suitable behavior if they want to allow PUTs to /:id to result in creating new objects
  post("/create", operation(createOp)) {

    // Make sure this app is allowed to create users
    requireServiceAuth("user/create")

    val u = parsedBody.extract[UserJson]

    val id = u.login

    val found = User.find(id)
    if (found.isDefined)
      haltConflict("login already exists")

    if (!u.email.isDefined)
      haltBadRequest("email required")

    if (!u.password.isDefined)
      haltBadRequest("insuffient password")

    val r = try {
      User.create(id, u.password.get, u.email, u.fullName)
    } catch {
      // Failed validation
      case ex: RecordInvalidException =>
        haltBadRequest(ex.getMessage)
    }
    try {
      sendWelcomeEmail(r)
      user = r // Mark the session that this user is logged in
      rememberMe.setCookie(user)
      r
    } catch {
      case ex: Exception =>
        // If we failed sending the email delete the new user record
        r.delete()

        throw ex
    }
  }

}