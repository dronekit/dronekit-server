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
import com.geeksville.akka.NamedActorClient
import akka.actor.ActorRefFactory
import com.geeksville.akka.MockAkka

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

  import LiveVehicleActor._

  /// Our LogBinaryMavlink actor
  private var tloggerOpt: Option[ActorRef] = None

  /// We reserve a tlog ID at mission start - but don't use it until mission end
  private var tlogId: Option[UUID] = None

  /// The mission we are creating
  private var missionOpt: Option[Mission] = None

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
      log.debug(s"Handling $msg")
      startMission(msg)

    case msg: StopMissionMsg =>
      log.debug(s"Handling $msg")

      // Update user preferences on keeping this tlog at all
      missionOpt.foreach(_.keep = msg.keep)

      stopMission(msg.notes)

    case msg: TimestampedMessage =>

      // Log to the file
      tloggerOpt.foreach { _ ! msg }

      // Let our vehicle model update current time
      super.onReceive(msg)

      // Update our live model (be careful here to not requeue the message, but rather handle it in this same callback
      // to preserve order
      if (receive.isDefinedAt(msg.msg))
        receive(msg.msg)
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
  private class TlogToS3Actor(mission: Mission) extends LogBinaryMavlink(LogBinaryMavlink.getFilename(), deleteIfBoring = false, wantImprovedFilename = false) {
    override protected def onFileClose() {
      if (mission.keep) {
        log.debug(s"Copying to s3: $tlogId")
        // Copy to S3
        val src = new BufferedInputStream(new FileInputStream(file), 8192)
        Mission.putBytes(tlogId.get.toString, src, file.length())
      } else
        log.warning("Mission marked as no-keep - not copying to S3")

      tlogId = None
      file.delete()
    }
  }

  def summary = MissionSummary(startTime.map(TimestampedMessage.usecsToDate), currentTime.map(TimestampedMessage.usecsToDate),
    maxAltitude, maxGroundSpeed, maxAirSpeed, -1, flightDuration, softwareVersion = buildVersion, softwareGit = buildGit)

  private def startMission(msg: StartMissionMsg) = blocking {
    assert(!tlogId.isDefined)
    tlogId = Some(UUID.randomUUID())
    log.debug(s"Starting tlog $tlogId")

    val f = LogBinaryMavlink.getFilename() // FIXME - create in temp directory instead

    val m = Mission.create(vehicle)
    missionOpt = Some(m)
    startTime = None
    m.notes = msg.notes

    tloggerOpt = Some(context.actorOf(Props(new TlogToS3Actor(m)), "tlogger"))

    // Pull privacy from vehicle if not specified
    var viewPriv = msg.viewPrivacy.getOrElse(AccessCode.DEFAULT).id
    if (viewPriv == AccessCode.DEFAULT_VALUE)
      viewPriv = vehicle.viewPrivacy

    m.viewPrivacy = viewPriv
    m.keep = msg.keep
    m.isLive = true
    m.save()

    // Find the space controller for our location
    val space = SpaceSupervisor.find()
    eventStream.subscribe(space, (x: Any) => true) // HUGE FIXME - we should subscribe only to the messages we care about
    publishEvent(MissionStart(m))
    log.debug(s"wrote Mission: $m")
  }

  private def stopMission(notes: Option[String] = None) {
    log.debug("Stopping mission")

    // Close the tlog and upload to s3
    tloggerOpt.foreach { a =>
      a ! PoisonPill
      tloggerOpt = None
    }

    missionOpt.foreach { m =>
      blocking {
        m.isLive = false
        m.tlogId = tlogId.map(_.toString)
        val s = summary
        s.create
        s.mission := m
        s.save()

        vehicle.updateFromMission(this)
        publishEvent(MissionStop(m))

        if (m.keep) {
          log.debug("Saving mission")
          m.save()
        } else {
          log.warning("No-keep mission, deleting")
          m.delete()
        }
      }
      missionOpt = None
    }
  }
}

object LiveVehicleActor {
  private implicit val context: ActorRefFactory = MockAkka.system
  private val actors = new NamedActorClient("live")

  private val msgLogThrottle = new Throttled(5000)

  /**
   * Find the supervisor responsible for a region of space
   *
   * FIXME - add grid identifer param
   */
  def find(vehicle: Vehicle, canAcceptCommands: Boolean) = actors.getOrCreate(vehicle.uuid.toString, Props(new LiveVehicleActor(vehicle, canAcceptCommands)))

}
