package com.geeksville.dapi.auth

import org.scalatra.{ Cookie, CookieOptions, ScalatraBase }
import org.scalatra.auth.ScentryStrategy
import javax.servlet.http.{ HttpServletResponse, HttpServletRequest }
import org.slf4j.LoggerFactory
import com.geeksville.dapi.model.User
import grizzled.slf4j.Logging
import org.apache.commons.codec.binary.Base64
import java.security.MessageDigest

class RememberMeStrategy(protected val app: ScalatraBase)(implicit request: HttpServletRequest, response: HttpServletResponse)
  extends ScentryStrategy[User] with Logging {

  override def name: String = "RememberMe"

  private val oneWeek = 7 * 24 * 3600

  def shouldUseCookies = {
    // Does the user want to use cookies.
    // If using a checkbox do this
    // checkbox2boolean(app.params.get("rememberMe").getOrElse("").toString)

    // But for now always try to use cookies
    true
  }

  /**
   * *
   * Grab the value of the rememberMe cookie token.
   */
  private def tokenVal = {
    Option(app.cookies).flatMap(_.get(cookieKey)) match {
      case Some(token) => token
      case None => ""
    }
  }

  /**
   * We use the referer name to construct our cookie - to ensure that any third party API
   * client gets their own cookie.  (Prevent CORSish attacks)
   */
  private def cookieKey(implicit request: HttpServletRequest) = {
    // Find referer for api checking - we prefer 'Origin' as the new preferred but will fall back to Referer
    val originOpt = Option(request.getHeader("Origin"))
    val referer = originOpt.orElse(Option(request.getHeader("Referer"))).map { url =>
      url.filter { c => // remove http:// from front - only allow alpha,digits and dots
        c.isLetter | c.isDigit | c == '.'
      }
    }

    "api-" + referer.getOrElse("unknown")
  }

  /**
   * *
   * Determine whether the strategy should be run for the current request.
   */
  override def isValid(implicit request: HttpServletRequest): Boolean = {
    // Note: I was using the less expensive getUserIdFromToken here - but that isn't good enough.  If the user has changed
    // their password we want remember me to say the token should be ignored
    val probablyGood = getUserFromToken(tokenVal).isDefined
    logger.debug(s"RememberMeStrategy: determining isValid: $probablyGood")
    probablyGood
  }

  /**
   * Change this token to invalidate all cookies.  FIXME - much better to generate randomly once and store
   * in nestor.conf (not in source control).
   */
  private val tokenKey = 432879274L

  private val encoder = new Base64

  private def md5hex(s: String) =
    {
      val md5digest = MessageDigest.getInstance("MD5")
      md5digest.digest(s.getBytes).map { "%02x".format(_) }.mkString
    }

  /**
   * This function is used both at token generation and later to check the generated token
   */
  private def makeMd5String(u: User, expireMsec: Long) =
    md5hex(u.login + ":" + expireMsec + ":" + u.hashedPassword + ":" + tokenKey)

  /**
   * Per
   * http://docs.spring.io/spring-security/site/docs/3.0.x/reference/remember-me.html
   *     base64(username + ":" + expirationTime + ":" +
   * md5Hex(username + ":" + expirationTime + ":" password + ":" + key))
   *
   * username:          As identifiable to the UserDetailsService
   * password:          That matches the one in the retrieved UserDetails
   * expirationTime:    The date and time when the remember-me token expires,
   * expressed in milliseconds
   * key:               A private key to prevent modification of the remember-me token
   */
  private def makeToken(u: User) = {
    val expireMsec = System.currentTimeMillis + oneWeek * 1000L

    val md5 = makeMd5String(u, expireMsec)
    val s = u.login + ":" + expireMsec + ":" + md5

    logger.debug(s"Generated token $s")
    encoder.encodeToString(s.getBytes)
  }

  private val TokenRegex = "(.*):(.*):(.*)".r
  private val MD5Regex = "(.*):(.*):(.*):(.*)".r

  /**
   *  This does most of the 'cheap' validation on the token, returning the userId if the md5 sum mostly checked out.
   *  @return (userid, expire, md5)
   */
  private def getUserIdFromToken(t: String): Option[(String, Long, String)] = {
    val sbytes = encoder.decode(t)
    val s = sbytes.map(_.toChar).mkString
    logger.debug(s"Checking base64 $t -> token $s")
    s match {
      case TokenRegex(login, expire, md5) =>

        val e = expire.toLong
        if (e < System.currentTimeMillis) {
          logger.warn(s"Token too old, deny.")
          None
        } else
          Some(login, e, md5)

      case "" =>
        logger.trace(s"No token found...")
        None

      case _ =>
        logger.error(s"HACK ATTEMPT: malformed token: $t")
        None
    }
  }

  /**
   * Complete validation of a token (including checking user password).  Returns user object on success
   */
  private def getUserFromToken(t: String): Option[User] = {
    getUserIdFromToken(t).flatMap {
      case (login, expire, md5) =>
        User.find(login).flatMap { u =>

          val expectedMd5 = makeMd5String(u, expire)
          if (expectedMd5 != md5) {
            logger.error(s"Bad token for $u - deny! (hack attempt or user changed psw)")
            None
          } else {
            logger.debug(s"$login has good token")
            Some(u)
          }
        }.orElse {
          logger.warn(s"User $login not found")
          None
        }
    }
  }

  /**
   * *
   * In a real application, we'd check the cookie's token value against a known hash, probably saved in a
   * datastore, to see if we should accept the cookie's token. Here, we'll just see if it's the one we set
   * earlier ("foobar") and accept it if so.
   */
  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse) = {
    logger.debug("RememberMeStrategy: attempting authentication")
    getUserFromToken(tokenVal)
  }

  /**
   * What should happen if the user is currently not authenticated?
   */
  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    //app.redirect("/sessions/new")
  }

  /// Set the cookie indicating that this user is logged in
  def setCookie(user: User) {
    if (shouldUseCookies) {
      logger.trace("rememberMe: setting cookie")
      val token = makeToken(user)
      Option(app.cookies) match {
        case Some(c) => c.set(cookieKey, token)(CookieOptions(maxAge = oneWeek, path = "/"))
        case None => logger.error(s"Can't set cookie on $request") // It seems like the atmosphere paths are broken for cookies
      }
    }
  }

  /**
   * *
   * After successfully authenticating with either the RememberMeStrategy, or the UserPasswordStrategy with the
   * "remember me" tickbox checked, we set a rememberMe cookie for later use.
   *
   * NB make sure you set a cookie path, or you risk getting weird problems because you've accidentally set
   * more than 1 cookie.
   */
  override def afterAuthenticate(winningStrategy: String, user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) = {
    if (winningStrategy == "RememberMe" || shouldUseCookies) {
      setCookie(user)
    }
  }

  /**
   * Run this code before logout, to clean up any leftover database state and delete the rememberMe token cookie.
   */
  override def beforeLogout(user: User)(implicit request: HttpServletRequest, response: HttpServletResponse) = {
    logger.info("rememberMe: beforeLogout")
    if (user != null) {
      // FIXME - do we need to do anything here?
      // user.forgetMe
    }
    app.cookies.delete(cookieKey)(CookieOptions(path = "/"))
  }

  /**
   * Used to easily match a checkbox value
   */
  private def checkbox2boolean(s: String): Boolean = {
    s match {
      case "yes" => true
      case "y" => true
      case "1" => true
      case "true" => true
      case _ => false
    }
  }
}