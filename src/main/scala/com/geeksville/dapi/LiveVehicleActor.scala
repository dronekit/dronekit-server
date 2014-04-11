package com.geeksville.dapi

import akka.actor.Actor
import akka.actor.ActorLogging
import com.geeksville.mavlink.TimestampedMessage
import com.geeksville.dapi.model.Vehicle
import akka.actor.Props
import com.geeksville.mavlink.LogBinaryMavlink
import akka.actor.ActorRef
import akka.actor.PoisonPill
import java.io.File
import com.geeksville.flight.VehicleModel
import com.geeksville.akka.InstrumentedActor
import com.geeksville.mavlink.SendYoungest
import org.mavlink.messages.MAVLinkMessage
import com.geeksville.dapi.model.Mission
import scala.concurrent.blocking
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.util.UUID
import com.geeksville.dapi.model.MissionSummary
import com.geeksville.mavlink.TimestampedMessage
import com.geeksville.util.Throttled

/// Sent when a vehicle connects to the server
case class VehicleConnected()

case class VehicleDisconnected()

/**
 * An actor that represents a connection to a live vehicle.  GCSAdapters use this object to store mavlink from vehicle and publishes from this object
 * can cause GCSAdapters to send messages to the vehicle (from the web).
 *
 * Supported message types:
 * TimestampedMessage - used to add to the running log/new data received from the vehicle
 * VehicleConnected - sent by the GCSActor when the vehicle first connects
 * VehicleDisconnected - sent by the GCSActor when the vehicle disconnects
 */
class LiveVehicleActor(val vehicle: Vehicle, canAcceptCommands: Boolean) extends VehicleModel with ActorLogging {
  private val msgLogThrottle = new Throttled(5000)

  /// Our LogBinaryMavlink actor
  private var tloggerOpt: Option[ActorRef] = None

  /// We reserve a tlog ID at mission start - but don't use it until mission end
  private var tlogId: Option[UUID] = None

  /// The mission we are creating
  private var missionOpt: Option[Mission] = None

  /// Start time for current mission in usecs
  private var startTime: Option[Long] = None

  /// Stop time for current mission in usecs
  private var stopTime: Option[Long] = None

  private var gcsActor: Option[ActorRef] = None

  // Since we are on a server, we don't want to inadvertently spam the vehicle
  this.listenOnly = !canAcceptCommands
  autoWaypointDownload = false
  autoParameterDownload = false

  override def onReceive = mReceive.orElse(super.onReceive)

  private def mReceive: InstrumentedActor.Receiver = {
    case VehicleConnected() =>
      log.debug("Vehicle connected")

      assert(!gcsActor.isDefined)
      gcsActor = Some(sender)

    case VehicleDisconnected() =>
      log.debug("Vehicle disconnected")

      // Vehicle should only be connected through one gcs actor at a time
      assert(sender == gcsActor.get)
      gcsActor = None

      stopMission() // In case client forgot
      // FIXME - store tlog to S3
      // FIXME - should I kill myself? - FIXME - need to use supervisors to do reference counting
      self ! PoisonPill

    case msg: StartMissionMsg =>
      startMission(msg)
    case msg: StopMissionMsg =>
      stopMission(msg.notes)

    case msg: TimestampedMessage =>
      msgLogThrottle.withIgnoreCount { numIgnored: Int =>
        log.debug(s"Receive ${msg.msg} (and $numIgnored others)")
      }

      // Log to the file
      tloggerOpt.foreach { _ ! msg }

      // Update start/stop times
      if (missionOpt.isDefined) {
        if (!startTime.isDefined)
          startTime = Some(msg.time)

        stopTime = Some(msg.time)
      }

      // Update our live model
      self ! msg.msg
  }

  /**
   * m must be a SendYoungest or a MAVLinkMessage
   */
  override protected def handlePacket(m: Any) {
    //log.debug(s"handlePacket: forwarding $m to $gcsActor")

    val msg = m match {
      case x: MAVLinkMessage => x
      case SendYoungest(x) => x
    }

    if (!canAcceptCommands)
      throw new Exception(s"$vehicle does not accept commands")
    else
      gcsActor.foreach(_ ! SendMavlinkToVehicle(msg))
  }

  /**
   * We modify the actor to copy to S3 after the file is closed
   */
  private class TlogToS3Actor extends LogBinaryMavlink(LogBinaryMavlink.getFilename(), deleteIfBoring = false, wantImprovedFilename = false) {
    override protected def onFileClose() {
      log.debug(s"Copying to s3: $tlogId")
      // Copy to S3
      val src = new BufferedInputStream(new FileInputStream(file), 8192)
      Mission.putBytes(tlogId.get.toString, src, file.length())
      tlogId = None
      // FIXME - delete the local copy once things seem to work
    }
  }

  def summary = MissionSummary(startTime.map(TimestampedMessage.usecsToDate), stopTime.map(TimestampedMessage.usecsToDate),
    maxAltitude, maxGroundSpeed, maxAirSpeed, -1, flightDuration)

  private def startMission(msg: StartMissionMsg) = blocking {
    assert(!tlogId.isDefined)
    tlogId = Some(UUID.randomUUID())
    log.debug(s"Starting tlog $tlogId")

    val f = LogBinaryMavlink.getFilename() // FIXME - create in temp directory instead
    tloggerOpt = Some(context.actorOf(Props(new TlogToS3Actor), "tlogger"))

    val m = Mission.create(vehicle)
    missionOpt = Some(m)
    startTime = None
    m.notes = msg.notes
    m.controlPrivacy = msg.controlPrivacy.getOrElse(AccessCode.DEFAULT).id
    m.viewPrivacy = msg.viewPrivacy.getOrElse(AccessCode.DEFAULT).id
    m.keep = msg.keep
    m.isLive = true
    m.save()
    log.debug(s"wrote Mission: $m")
  }

  private def stopMission(notes: Option[String] = None) {
    // Close the tlog and upload to s3
    tloggerOpt.foreach { a =>
      a ! PoisonPill
      tloggerOpt = None
    }

    missionOpt.foreach { m =>
      blocking {
        log.debug("Saving mission")
        m.isLive = false
        m.tlogId = tlogId
        val s = summary
        s.create
        s.mission := m
        s.save()
        m.save()
      }
      missionOpt = None
    }
  }
}
