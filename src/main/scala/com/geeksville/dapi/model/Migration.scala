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
import org.squeryl.Session
import org.squeryl.internals.StatementWriter
import java.sql.SQLException

case class Migration(var currentVersion: Int) extends DapiRecord with Logging

object Migration extends ActiveRecordCompanion[Migration] with Logging {

  val dbWipeVersion = 6

  val migrations = Seq(
    Migrator(7,
      "ALTER TABLE users ADD need_new_password BOOLEAN NOT NULL DEFAULT false"),
    Migrator(8,
      "ALTER TABLE users ADD want_emails BOOLEAN NOT NULL DEFAULT true"),
    Migrator(9,
      "ALTER TABLE mission_summaries ADD text VARCHAR(80)"),
    Migrator(10, "ALTER TABLE users ADD number_of_logins INTEGER NOT NULL DEFAULT 0"),
    Migrator(11,
      "ALTER TABLE mission_summaries ADD num_parameters INTEGER NOT NULL DEFAULT -1"))

  case class Migrator(newVerNum: Int, sql: String*) {
    def run() {
      inTransaction {
        val schema = Tables.users.schema
        info(s"Migrating to: $this")
        // schema.printDdl { s => info(s) }
        // val adapter = Session.currentSession.databaseAdapter
        // val sw = new StatementWriter(adapter)
        //Tables.users.schema.createColumnGroupConstraintsAndIndexes
        sql.foreach(executeDdl)
      }

      // Success - we are now at this new version #
      setVersion(newVerNum)
    }
  }

  def requiredVersion = migrations.map(_.newVerNum).reduce(math.max)

  /// Run raw SQL
  private def executeDdl(statement: String) = {

    val cs = Session.currentSession
    cs.log(statement)

    val s = cs.connection.createStatement
    try {
      s.execute(statement)
    } finally {
      s.close
    }
  }

  private def setVersion(v: Int) {
    val dbVer = find()
    dbVer.currentVersion = v
    dbVer.save
  }

  def allowAutoWipe = false

  def update() {
    val curver = try {
      find().currentVersion
    } catch {
      case ex: Exception =>
        error("Can't find migration table!")
        0
    }

    if (curver < dbWipeVersion) {
      if (!allowAutoWipe) {
        error(s"DB schema ver($curver) < wipever($dbWipeVersion), but autowipe is false - please fix DB")
        throw new Exception("DB invalid")
      } else {
        error("WIPING TABLES DUE TO MIGRATION!")
        Tables.reset

        // A wipe implies no need to run migrations
        setVersion(math.max(dbWipeVersion, requiredVersion))
      }
    } else
      migrations.filter(curver < _.newVerNum).foreach(_.run())
  }

  private def find(): Migration = {
    this.headOption.getOrElse {
      val m = Migration(0).create
      m
    }
  }

}

