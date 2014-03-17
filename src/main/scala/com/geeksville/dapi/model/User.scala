package com.geeksville.dapi.model

import com.github.aselab.activerecord.Datestamps
import com.github.aselab.activerecord.annotations._
import org.squeryl.annotations.Transient
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import com.github.aselab.activerecord.dsl._

case class User(@Required @Unique login: String, email: Option[String], fullName: Option[String]) extends DapiRecord {
  @Transient
  @Length(min = 8, max = 30)
  var password: String = _

  var hashedPassword: String = _

  /**
   * All the vehicles this user owns
   */
  lazy val vehicles = hasMany[Vehicle]

  def isPasswordGood(test: String) = BCrypt.checkpw(test, hashedPassword)

  override def beforeSave() {
    hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
  }
}

object User extends DapiRecordCompanion[User] {
  def find(id: String): Option[User] = this.where(_.login === id).headOption
}

