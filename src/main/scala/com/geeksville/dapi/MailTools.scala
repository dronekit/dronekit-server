package com.geeksville.dapi

import com.geeksville.dapi.model.User
import com.geeksville.util.Using._
import com.geeksville.mailgun.MailgunClient
import com.geeksville.scalatra.ScalatraTools
import grizzled.slf4j.Logging

object MailTools extends Logging {

  def sendWelcomeEmail(u: User) {
    import Global._

    using(new MailgunClient()) { client =>
      val fullname = u.fullName.getOrElse(u.login)
      val confirmDest = s"http://$hostname/confirm/${u.login}/${u.verificationCode}"

      // FIXME - make HTML email
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
      debug("Mailgun reply: " + r)
    }
  }

  def sendPasswordReset(u: User) {
    import Global._

    using(new MailgunClient()) { client =>
      val fullname = u.fullName.getOrElse(u.login)
      val code = u.beginPasswordReset()
      val confirmDest = s"http://$hostname/reset/${u.login}/$code"

      // FIXME - make HTML email and also use a md5 or somesuch to hash username+emailaddr
      val bodyText =
        s"""
        Dear $fullname,
        
        Someone has requested a password reset procedure on your account.  If _you_ did this, then please 
        visit the following URL to select your new password.  
        
        If you did not request a new password, you can ignore this email.
        
        $confirmDest 
        
        Thank you for using Droneshare.  Any feedback is always appreciated.  Please email
        $senderEmail.
        
        """

      val r = client.sendTo(senderEmail, u.email.get, s"$appName password reset",
        bodyText, testing = ScalatraTools.isTesting)
      debug("Mailgun reply: " + r)
    }
  }
}