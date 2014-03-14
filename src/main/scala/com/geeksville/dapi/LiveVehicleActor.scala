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

      val f = LogBinaryMavlink.getFilename() // FIXME - create in temp directory instead
      tlogFileOpt = Some(f)
      tloggerOpt = Some(context.actorOf(Props(LogBinaryMavlink.create(false, f, wantImprovedFilename = false)), "tlogger"))

    case VehicleDisconnected() =>
      log.debug("Vehicle disconnected")

      // Vehicle should only be connected through one gcs actor at a time
      assert(sender == gcsActor)
      gcsActorOpt = None

      tloggerOpt.foreach { _ ! PoisonPill }
    // FIXME - store tlog to S3
    // FIXME - should I kill myself?

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
}
