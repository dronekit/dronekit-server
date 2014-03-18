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
  /// Our LogBinaryMavlink actor
  private var tloggerOpt: Option[ActorRef] = None
  private var tlogFileOpt: Option[File] = None

  private var missionOpt: Option[Mission] = None

  private var gcsActorOpt: Option[ActorRef] = None

  // Since we are on a server, we don't want to inadvertently spam the vehicle
  this.listenOnly = !canAcceptCommands
  autoWaypointDownload = false
  autoParameterDownload = false

  private def gcsActor = gcsActorOpt.getOrElse(throw new Exception("No GCS connected"))

  override def onReceive = mReceive.orElse(super.onReceive)

  private def mReceive: InstrumentedActor.Receiver = {
    case VehicleConnected() =>
      log.debug("Vehicle connected")

      assert(!gcsActorOpt.isDefined)
      gcsActorOpt = Some(sender)

    case VehicleDisconnected() =>
      log.debug("Vehicle disconnected")

      // Vehicle should only be connected through one gcs actor at a time
      assert(sender == gcsActor)
      gcsActorOpt = None

      stopMission() // In case client forgot
    // FIXME - store tlog to S3
    // FIXME - should I kill myself? - FIXME - need to use supervisors to do reference counting

    case msg: StartMissionMsg =>
      startMission(msg)
    case msg: StopMissionMsg =>
      stopMission(msg.notes)

    case msg: TimestampedMessage =>
      log.debug(s"Forwarding $msg")

      // Log to the file
      tloggerOpt.foreach { _ ! msg }

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
      gcsActor ! SendMavlinkToVehicle(msg)
  }

  private def startMission(msg: StartMissionMsg) {
    log.debug("Starting tlog")

    val f = LogBinaryMavlink.getFilename() // FIXME - create in temp directory instead
    tlogFileOpt = Some(f)
    tloggerOpt = Some(context.actorOf(Props(LogBinaryMavlink.create(false, f, wantImprovedFilename = false)), "tlogger"))

    val m = Mission.create(vehicle)
    missionOpt = Some(m)
    m.notes = msg.notes
    m.controlPrivacy = msg.controlPrivacy.getOrElse(AccessCode.DEFAULT).id
    m.viewPrivacy = msg.viewPrivacy.getOrElse(AccessCode.DEFAULT).id
    m.keep = msg.keep
    m.isLive = true
    m.save()
    log.debug("wrote db")
  }

  private def stopMission(notes: Option[String] = None) {

    // FIXME - move the tlog to s3 and update the record

    missionOpt.foreach { m =>
      m.isLive = false
      m.save()
    }
    tloggerOpt.foreach { _ ! PoisonPill }
  }
}
