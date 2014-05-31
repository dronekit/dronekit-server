package com.geeksville.dapi

import com.geeksville.dapi.model.User
import com.geeksville.util.Using._
import com.geeksville.mailgun.MailgunClient
import com.geeksville.scalatra.ScalatraTools
import grizzled.slf4j.Logging
import scala.xml._

object MailTools extends Logging {

  def sendWelcomeEmail(u: User) {
    import Global._

    using(new MailgunClient()) { client =>
      val fullname = u.fullName.getOrElse(u.login)
      val confirmDest = s"$rootUrl/#/confirm/${u.login}/${u.verificationCode}"

      // FIXME - make HTML email
      val body =
        <html><body>
          Dear { fullname },<p/>

          Your new account on <a href={ rootUrl }>Droneshare</a> is now mostly ready.<p/>

          The only remaining step is to confirm your email address.  To confirm your email please visit the following URL:<p/>

          <a href={ confirmDest }>{ confirmDest }</a><p/>

          Feedback on this beta-test is appreciated.  Please email <a href={ "mailto:" + senderEmail }>{ senderEmail }</a>
          with questions or comments. Droneshare is 
          <a href="https://github.com/diydrones/droneshare/blob/master/WELCOME.md">open-source</a>, 
          please contribute code or fork it into something new.<p/>
        </body></html>

      val r = client.sendHtml(senderEmail, u.email.get, s"Welcome to $appName",
        body, testing = ScalatraTools.isTesting)
      debug("Mailgun reply: " + r)
    }
  }

  def sendPasswordReset(u: User) {
    import Global._

    val email = u.email.getOrElse(throw new Exception("No email address available"))

    using(new MailgunClient()) { client =>
      val fullname = u.fullName.getOrElse(u.login)
      val code = u.beginPasswordReset()
      val confirmDest = s"http://$hostname/#/reset/${u.login}/$code"

      // FIXME - make HTML email and also use a md5 or somesuch to hash username+emailaddr
      val bodyText =
        <html><body>
        Dear { fullname },<p/>
        
        Someone has requested a password reset procedure on your account.  If <b>you</b> did this, then please 
        visit the following URL to select your new password.<p/>
        
        If you did not request a new password, you can ignore this email.<p/>
        
        <a href={ confirmDest }>{ confirmDest }</a><p/>
        
        Thank you for using Droneshare.  Please email <a href={ "mailto:" + senderEmail }>{ senderEmail }</a>
        with questions or comments.<p/>
        </body></html>

      val r = client.sendHtml(senderEmail, email, s"$appName password reset",
        bodyText, testing = ScalatraTools.isTesting)
      debug("Mailgun reply: " + r)
    }
  }
}