package com.geeksville.dapi.temp

import com.geeksville.nestor.TLogChunkDAO
import com.geeksville.dapi.model.User
import com.geeksville.dapi.model.Vehicle
import com.github.aselab.activerecord.dsl._
import akka.actor.Actor
import akka.actor.ActorLogging
import scala.concurrent.blocking
import com.geeksville.dapi.model.Mission
import com.geeksville.dapi.AccessCode
import java.util.UUID
import com.geeksville.nestor.TLogChunk
import java.io.ByteArrayInputStream

case class DoImport(numRecords: Int)

/**
 * Migrates old nestor records to the new dronehub db (including tlogs etc)
 */
class NestorImporter extends Actor with ActorLogging {

  def receive = {
    case DoImport(numRecords) =>
      migrate(numRecords)
  }

  def migrate(maxResults: Int) = blocking {
    TLogChunkDAO.tlogsRecent(maxResults).find { tlog =>

      val id = tlog.id
      val wantStop = if (Mission.findByTlogId(id).isDefined || tlog.startTime.getYear > 2020) {
        log.info(s"Skipping $id ${tlog.startTime}")
        false
      } else {
        val summary = tlog.summary
        var userid = summary.ownerId

        log.info(s"Migrating $id ${tlog.startTime}")

        // Create user record if necessary (with an invalid password)
        if (userid.isEmpty)
          userid = "anonymous"
        val user = User.find(userid).getOrElse {
          val u = User(userid).create
          u.save
          u
        }

        // Create vehicle record if nessary
        val vehicle = user.vehicles.headOption.getOrElse {
          val v = Vehicle().create
          v.name = "Imported from Droneshare"
          user.vehicles += v
          v.save
          user.save // FIXME - do I need to explicitly save?
          v
        }

        // Copy over tlog

        tlog.bytes.foreach { bytes =>
          vehicle.createMission(bytes, Some("Imported from Droneshare"), tlogId = tlog.id)
        }
        false
      }

      wantStop
    }
    log.info("Done with migration!")
  }
}