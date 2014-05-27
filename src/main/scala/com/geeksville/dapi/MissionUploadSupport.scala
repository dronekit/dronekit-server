package com.geeksville.dapi

import com.geeksville.dapi.model.Vehicle
import org.scalatra.servlet.FileUploadSupport
import com.geeksville.scalatra.ControllerExtras
import com.geeksville.dapi.model.Mission
import com.geeksville.util.FileTools

/**
 * This is a mixin that is shared by both the vehicle and mission endpoints (because they both allow slightly
 * different variants of tlog upload)
 */
trait MissionUploadSupport extends FileUploadSupport { self: ControllerExtras =>

  /**
   * @return response to client
   */
  def handleMissionUpload(v: Vehicle) = {
    var errMsg: Option[String] = None

    dumpRequest()

    val tlogs = if (request.contentType == Some(Mission.mimeType)) {
      // Just pull out one file
      val bytes = FileTools.toByteArray(request.getInputStream)
      Seq(None -> bytes)
    } else {
      val files = fileMultiParams.values.flatMap { s => s }

      files.flatMap { payload =>
        warn(s"Considering ${payload.name} ${payload.fieldName} ${payload.contentType}")
        val ctype = {
          if (payload.name.endsWith(".tlog")) // In case the client isn't smart enough to set mime types
            Mission.mimeType
          else
            payload.contentType.getOrElse(haltBadRequest("content-type not set"))
        }
        if (Mission.mimeType != ctype) {
          val msg = (s"${payload.name} did not seem to be a TLOG")
          errMsg = Some(msg)
          None
        } else {
          info(s"Processing tlog upload for vehicle $v, numBytes=${payload.get.size}, notes=${payload.name}")

          Some(Some(payload.name) -> payload.get)
        }
      }
    }

    // Create missions for each tlog
    val created = tlogs.map {
      case (name, payload) =>

        val m = v.createMission(payload, name)

        // Make this new mission show up on the recent flights list
        val space = SpaceSupervisor.find()
        SpaceSupervisor.tellMission(space, m)
        m
    }.toList

    // If we had exactly one bad file, tell the client there was a problem via an error code.
    // Otherwise, claim success (this allows users to drag and drop whole directories and we'll cope with
    // just the tlogs).
    errMsg.foreach { msg =>
      if (created.size == 1)
        haltBadRequest(msg)
    }

    warn(s"Returning ${created.mkString(", ")}")

    // Return the missions that were created
    created
  }
}