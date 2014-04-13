package com.geeksville.dapi.model

import com.github.aselab.activerecord.Datestamps
import com.github.aselab.activerecord.annotations._
import org.squeryl.annotations.Transient
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import com.github.aselab.activerecord.dsl._
import grizzled.slf4j.Logging
import com.geeksville.dapi.AccessCode

case class User(@Required @Unique login: String, email: Option[String] = None, fullName: Option[String] = None) extends DapiRecord with Logging {
  /**
   * A user specified password
   * If null we assume invalid
   */
  @Transient
  @Length(min = 8, max = 40)
  var password: String = _

  /**
   * A hashed password or "invalid" if we want this password to never match
   */
  var hashedPassword: String = _

  /**
   * The group Id for htis user (eventually we will support group tables, for now the only options are null, admin.
   */
  @Length(max = 40)
  var groupId: String = ""

  /**
   * All the vehicles this user owns
   */
  lazy val vehicles = hasMany[Vehicle]

  def isAdmin = groupId == "admin"

  def isDeveloper = groupId == "develop"

  def isResearcher = groupId == "research"

  def isPasswordGood(test: String) = {
    if (hashedPassword == "invalid") {
      logger.warn(s"Failing password test for $login, because stored password is invalid")
      false
    } else {
      // FIXME - never leave enabled in production code, because emitting psws to logs is bad juju
      // logger.warn(s"Checking password $test againsted hashed version $hashedPassword")
      BCrypt.checkpw(test, hashedPassword)
    }
  }

  override def beforeSave() {

    if (password != null) {
      // A new password has been requested
      logger.warn(s"Saving $this with new password")
      hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
    }

    if (hashedPassword == null) {
      logger.warn(s"Saving $this with invalid password")
      hashedPassword = "invalid"
    }

    super.beforeSave()
  }
}

object User extends DapiRecordCompanion[User] {
  /**
   * Find a user (creating the root acct if necessary)
   */
  override def find(id: String): Option[User] = {
    this.where(_.login === id).headOption.orElse {
      if (id == "root") {
        // If we don't find a root account - make a new one (must be a virgin/damaged DB)
        // FIXME - choose a random initial password and print it to the log
        val psw = "fish4403"
        Some(create("root", psw, Some("kevin@3drobotics.com"), Some("Kevin Hester")))
      } else
        None
    }
  }

  def create(login: String, password: String, email: Option[String] = None, fullName: Option[String] = None) = {
    val u = User(login, email, fullName).create
    u.password = password
    u.save() // FIXME - do I need to save?
    u
  }
}

