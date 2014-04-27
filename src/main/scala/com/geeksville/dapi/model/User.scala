package com.geeksville.dapi.model

import com.github.aselab.activerecord.Datestamps
import com.github.aselab.activerecord.annotations._
import org.squeryl.annotations.Transient
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import com.github.aselab.activerecord.dsl._
import grizzled.slf4j.Logging
import com.geeksville.dapi.AccessCode
import org.json4s.CustomSerializer
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import com.geeksville.util.Gravatar
import java.util.Date

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

  /// Date of last login
  var lastLoginDate: Date = _

  @Length(max = 18) /// IP address of last client login
  var lastLoginAddr: String = _

  /**
   * A URL of a small jpg for this user
   */
  def avatarImageURL = email.map(Gravatar.avatarImageUrl)

  /**
   * A URL that can be shown to the user if they want to view more details on an avatar.
   * Currently goes to gravatar.  Once we have a user profile URL in MDS we can return that instead.
   */
  def profileURL = email.map(Gravatar.profileUrl)

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
      password = null
    }

    if (hashedPassword == null) {
      logger.warn(s"Saving $this with invalid password")
      hashedPassword = "invalid"
    }

    super.beforeSave()
  }

  override def toString() = s"User:$login(group = $groupId)"
}

/// We provide an initionally restricted view of users
object UserSerializer extends CustomSerializer[User](format => (
  {
    // FIXME - it would more elegant to just make a throw away case class object and use it for the decoding
    case JObject(JField("login", JString(s)) :: JField("fullName", JString(e)) :: Nil) =>
      User(s, fullName = Some(e))
  },
  {
    case u: User =>
      ("login" -> u.login) ~ ("fullName" -> u.fullName) ~ ("isAdmin" -> u.isAdmin) ~ ("avatarImage" -> u.avatarImageURL) ~ ("profileURL" -> u.profileURL)
  }))

object User extends DapiRecordCompanion[User] with Logging {
  /**
   * Find a user (creating the root acct if necessary)
   */
  override def find(id: String): Option[User] = {
    this.where(_.login === id).headOption.orElse {
      debug(s"Read user $id from DB")

      if (id == "root") {
        // If we don't find a root account - make a new one (must be a virgin/damaged DB)
        // FIXME - choose a random initial password and print it to the log
        val psw = "fish4403"
        val u = create("root", psw, Some("kevin@3drobotics.com"), Some("Kevin Hester"), group = "admin")
        Some(u)
      } else {
        debug(s"User $id not found in DB")
        None
      }
    }
  }

  def create(login: String, password: String, email: Option[String] = None, fullName: Option[String] = None, group: String = "") = {
    val u = User(login, email, fullName).create
    u.password = password
    u.groupId = group
    u.save() // FIXME - do I need to save?
    u
  }
}

