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
import org.json4s._
import com.geeksville.util.Gravatar
import java.util.Date
import scala.util.Random
import com.geeksville.akka.MockAkka

case class User(@Required @Unique login: String,
  @Unique email: Option[String] = None, fullName: Option[String] = None) extends DapiRecord with Logging {
  /**
   * A user specified password
   * If null we assume invalid
   */
  @Transient
  @Length(min = 0, max = 40)
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
  var lastLoginDate: Date = new Date()

  @Length(max = 18) /// IP address of last client login
  var lastLoginAddr: String = "unknown"

  // For vehicles and missions
  var defaultViewPrivacy: Int = AccessCode.DEFAULT_VALUE
  var defaultControlPrivacy: Int = AccessCode.DEFAULT_VALUE

  // User has confirmed their email
  var emailVerified = false

  // A sysadmin/daemon has decided this user needs a new password
  var needNewPassword = false

  var wantEmails = true

  /// If set this token can be used tempoarily by confirmPasswordReset
  var passwordResetToken: Option[Long] = None

  /// If set this was the time the password reset started (used to ignore 'too old' reset tokens)
  var passwordResetDate: Option[Date] = None

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
      BCrypt.checkpw(test.trim, hashedPassword)
    }
  }

  /**
   * Start a password reset session by picking a reset token and sending an email to the user
   * that contains that token (later submitted to MDS via a webform, then MDS does a post that
   */
  def beginPasswordReset() {
    passwordResetToken = Some(User.random.nextLong)
    passwordResetDate = Some(new Date)
    save()

    // FIXME - send email
    throw new Exception("not yet implemented")
  }

  /**
   * Update the password if the token is correct.
   * @return false for failure
   */
  def confirmPasswordReset(token: String, newPassword: String) = {
    throw new Exception("not yet implemented")
    false
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

  def getVehicle(uuid: UUID) = {
    // Vehicle.find(uuid.toString)
    debug(s"Looking for $uuid inside of $this")
    vehicles.where(_.uuid === uuid).headOption
  }

  /**
   * find a vehicle object for a specified UUID, associating it with our user if needed
   */
  def getOrCreateVehicle(uuid: UUID) = getVehicle(uuid).getOrElse {
    warn(s"Vehicle $uuid not found in $this - creating")
    val v = Vehicle(uuid).create
    vehicles << v
    v.save
    save // FIXME - do I need to explicitly save?
    v
  }

  override def toString() = s"User:$login(group = $groupId)"
}

case class UserJson(login: String,
  password: Option[String] = None, email: Option[String] = None,
  fullName: Option[String] = None, wantEmails: Option[String] = None)

/// We provide an initionally restricted view of users
/// If we know a viewer we will customize for them
class UserSerializer(viewer: Option[User]) extends CustomSerializer[User](implicit format => (
  {
    // more elegant to just make a throw away case class object and use it for the decoding
    //case JObject(JField("login", JString(s)) :: JField("fullName", JString(e)) :: Nil) =>
    case x: JValue =>
      val r = x.extract[UserJson]
      val u = User(r.login, r.email, r.fullName)
      r.password.foreach(u.password = _)
      u
  },
  {
    case u: User =>
      var r = ("login" -> u.login) ~
        ("fullName" -> u.fullName) ~
        ("isAdmin" -> u.isAdmin) ~
        ("avatarImage" -> u.avatarImageURL) ~
        ("profileURL" -> u.profileURL) ~
        ("emailVerified" -> u.emailVerified) ~
        ("needNewPassword" -> u.needNewPassword) ~
        ("vehicles" -> u.vehicles.map(_.id).toSeq.sorted)

      val showEmail = viewer.map { v => v.isAdmin || v.login == u.login }.getOrElse(false)
      if (showEmail) {
        r = r ~ ("email" -> u.email) ~ ("wantEmails" -> u.wantEmails)
      }
      r
  }))

object User extends DapiRecordCompanion[User] with Logging {

  private val random = new Random(System.currentTimeMillis)

  def findByEmail(email: String): Option[User] =
    this.where(_.email === email.toLowerCase).headOption

  /**
   * Find a user (creating the root acct if necessary)
   */
  override def find(id: String): Option[User] = {
    this.where(_.login === id.toLowerCase).headOption.orElse {
      if (id == "root") {
        debug(s"Seeding $id user")

        // If we don't find a root account - make a new one (must be a virgin/damaged DB)
        // If your run is failing on the following line, add a definition to ~/nestor.conf
        val psw = MockAkka.config.getString("dapi.defaultRootPsw")
        val u = create("root", psw, Some("kevin@3drobotics.com"), Some("Kevin Hester"), group = "admin")
        Some(u)
      } else {
        debug(s"User $id not found in DB")
        None
      }
    }
  }

  def create(login: String, password: String = null, email: Option[String] = None, fullName: Option[String] = None, group: String = "") = {
    val u = User(login.trim.toLowerCase, email.map(_.trim.toLowerCase), fullName.map(_.trim))
    u.password = password.trim
    u.groupId = group
    u.create
    u.save
    debug(s"Created new user $u")
    u
  }
}

