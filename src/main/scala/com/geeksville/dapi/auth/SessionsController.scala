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

class SessionsController(implicit val swagger: Swagger) extends DroneHubStack with SwaggerSupport {

  override protected val applicationName = Some("api/v1/auth")
  protected lazy val applicationDescription = s"Session operations (login, logout, etc...)"

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
    Extraction.decompose(user)
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

  // NOTE: For most applications this GET method should not be used - but it does make for easier browser based testing
  get("/login") {
    doLogin()
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
    user
  }

  private lazy val logoutOp = apiOperation[String]("logout") summary "Logout the current user"
  // Never do this in a real app. State changes should never happen as a result of a GET request. However, this does
  // make it easier to illustrate the logout code.
  post("/logout", operation(logoutOp)) {
    scentry.logout()

    // NOT USING HTML
    // redirect("/")
    JString("Logged out")
  }

}