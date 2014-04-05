package com.geeksville.dapi.model

import com.github.aselab.activerecord.Datestamps
import com.github.aselab.activerecord.annotations._
import org.squeryl.annotations.Transient
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import com.github.aselab.activerecord.dsl._

case class User(@Required @Unique login: String, email: Option[String] = None, fullName: Option[String] = None) extends DapiRecord {
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
   * All the vehicles this user owns
   */
  lazy val vehicles = hasMany[Vehicle]

  def isPasswordGood(test: String) = BCrypt.checkpw(test, hashedPassword)

  override def beforeSave() {
    hashedPassword = if (password == null)
      "invalid"
    else
      BCrypt.hashpw(password, BCrypt.gensalt())
  }
}

object User extends DapiRecordCompanion[User] {
  override def find(id: String): Option[User] = this.where(_.login === id).headOption

  def create(login: String, password: String, email: Option[String] = None, fullName: Option[String] = None) = {
    val u = User(login, email, fullName).create
    u.password = password
    u.save() // FIXME - do I need to save?
    u
  }
}

