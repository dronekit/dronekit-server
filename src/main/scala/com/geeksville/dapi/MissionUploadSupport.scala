package com.geeksville.dapi

import com.geeksville.dapi.model.Vehicle
import org.scalatra.servlet.FileUploadSupport
import com.geeksville.scalatra.ControllerExtras
import com.geeksville.dapi.model.Mission
import com.geeksville.util.FileTools
import com.geeksville.apiproxy.APIConstants

/**
 * This is a mixin that is shared by both the vehicle and mission endpoints (because they both allow slightly
 * different variants of tlog upload)
 */
trait MissionUploadSupport extends FileUploadSupport { self: ControllerExtras =>

  /**
   * @return response to client
   */
  def handleMissionUpload(v: Vehicle, privacy: AccessCode.EnumVal = AccessCode.DEFAULT) = {
    var errMsg: Option[String] = None

    // dumpRequest()

    def setErr(msg: String) {
      error(msg)
      errMsg = Some(msg)
    }

    val requestType = request.contentType.getOrElse("")
    val tlogs = if (APIConstants.isValidMimeType(requestType)) {
      // Just pull out one file
      val bytes = FileTools.toByteArray(request.getInputStream)
      Seq((None, requestType, bytes))
    } else {
      val files = fileMultiParams.values.flatMap { s => s }

      files.flatMap { payload =>
        warn(s"Considering ${payload.name} ${payload.fieldName} ${payload.contentType}")
        val requestedType = payload.contentType.flatMap { c =>
          if (c == "application/octet-stream") // If it is this the client doesn't really know
            None
          else
            Some(c)
        }
        val ctype = APIConstants.extensionToMimeType(payload.name).orElse(requestedType).getOrElse(haltBadRequest("content-type not set"))
        debug(s"Upload mime type is $ctype for ${payload.name}")
        if (!APIConstants.isValidMimeType(ctype)) {
          setErr(s"${payload.name} is not a log file")
          None
        } else {
          info(s"Processing upload for vehicle $v, numBytes=${payload.get.size}, notes=${payload.name}")

          Some((Some(payload.name), ctype, payload.get))
        }
      }
    }

    // Create missions for each tlog
    val created = tlogs.flatMap {
      case (name, mimeType, payload) =>

        if (payload.isEmpty) {
          setErr(s"$name is empty")
          None
        } else {
          val m = v.createMission(payload, name, mimeType = mimeType)
          m.viewPrivacy = privacy
          m.save()

          if (!m.deleteIfUninteresting()) {
            // Make this new mission show up on the recent flights list
            val space = SpaceSupervisor.find()
            SpaceSupervisor.tellMission(space, m)
            Some(m)
          } else {
            setErr("Invalid location data was found in that log, ignoring")
            None
          }
        }
    }.toList

    errMsg.foreach { msg =>
      // If the user submitted at least one valid tlog, but we didn't find it interesting, show a less severe
      // error message
      if (!tlogs.isEmpty && created.isEmpty)
        haltNotAcceptable(msg)

      // If we had exactly one bad file, tell the client there was a problem via an error code.
      // Otherwise, claim success (this allows users to drag and drop whole directories and we'll cope with
      // just the tlogs).
      if (tlogs.size <= 1)
        haltBadRequest(msg)
    }

    warn(s"Considered ${tlogs.mkString(", ")} - returning ${created.mkString(", ")}")

    // Return the missions that were created
    created
  }
}