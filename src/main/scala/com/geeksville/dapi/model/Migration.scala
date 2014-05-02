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
import com.github.aselab.activerecord.ActiveRecordCompanion

case class Migration(var currentVersion: Int) extends DapiRecord with Logging {

}

object Migration extends ActiveRecordCompanion[Migration] with Logging {

  val requiredVersion = 1
  val dbWipeVersion = 6

  def update() {
    val curver = try {
      find().currentVersion
    } catch {
      case ex: Exception =>
        error("Can't find migration table!")
        0
    }

    if (curver < dbWipeVersion) {
      error("WIPING TABLES DUE TO MIGRATION!")
      Tables.reset
    }

    val dbVer = find()
    dbVer.currentVersion = math.max(requiredVersion, dbWipeVersion)
    dbVer.save
  }

  private def find(): Migration = {
    this.headOption.getOrElse {
      val m = Migration(0).create
      m
    }
  }

}

