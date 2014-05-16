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
import org.mavlink.messages.ardupilotmega.msg_heartbeat
import scala.concurrent.duration._
import com.github.aselab.activerecord.dsl._
import com.geeksville.mavlink.FlushNowMessage

/// Sent when a vehicle connects to the server
case class VehicleConnected()

case class VehicleDisconnected()

// We would like the live vehicle to reply with an Option[Array[Byte]] of tlog bytes
case object GetTLogMessage

/**
 * An actor that represents a connection to a live vehicle.  GCSAdapters use this object to store mavlink from vehicle and publishes from this object
 * can cause GCSAdapters to send messages to the vehicle (from the web).
 *
 * Supported message types:
 * TimestampedMessage - used to add to the running log/new data received from the vehicle
 * VehicleConnected - sent by the GCSActor when the vehicle first connects
 * VehicleDisconnected - sent by the GCSActor when the vehicle disconnects
 */
class LiveVehicleActor(val vehicle: Vehicle, canAcceptCommands: Boolean)
  extends VehicleModel(maxUpdatePeriod = 5000) with ActorLogging {

  import LiveVehicleActor._
  import context._

  /// Our LogBinaryMavlink actor
  private var tloggerOpt: Option[ActorRef] = None

  /// We reserve a tlog ID at mission start - but don't use it until mission end
  private var tlogId: Option[UUID] = None
  private var myTlogFile: Option[File] = None

  /// The mission we are creating
  private var missionOpt: Option[Mission] = None

  private var gcsActor: Option[ActorRef] = None

  private case object SendUpdateTickMsg

  // We periodically send mission updates to any interested subscriber (mainly so SpaceSupervisor can
  // stay up to date)
  val updateTickInterval = 60 seconds
  val updateTickSender = system.scheduler.schedule(updateTickInterval,
    updateTickInterval,
    self,
    SendUpdateTickMsg)

  // Since we are on a server, we don't want to inadvertently spam the vehicle
  this.listenOnly = !canAcceptCommands
  autoWaypointDownload = false
  autoParameterDownload = false

  override def toString = s"LiveVehicle: $vehicle"

  /**
   * We always claim to be a ground controller (FIXME, find a better way to pick a number)
   * 255 is mission planner
   * 253 is andropilot
   */
  override def systemId = 252

  override def postStop() {
    updateTickSender.cancel()
    super.postStop()
  }

  override def onReceive = mReceive.orElse(super.onReceive)

  private def mReceive: InstrumentedActor.Receiver = {
    case VehicleConnected() =>
      log.debug(s"Vehicle connected (GCS=$sender)")

      // It is possible for a GCS to drop a connection and then callback into a 'live' 
      // vehicle instance.  In that case, we just mark that gcs as our new owner

      gcsActor.foreach { old =>
        log.warning(s"Vehicle reconnection, hanging up on old GCS $old")
        stopMission()
        old ! VehicleDisconnected()
      }
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

    case SendUpdateTickMsg =>
      sendMissionUpdate()

    case GetTLogMessage =>
      sender ! getTlogBytes()

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

    assert(msg != null)

    // Some messages we never want to send to the client
    val isBlacklist = msg match {
      case x: msg_heartbeat => // We assume the local GCS is sending this - no need to waste bandwidth
        true
      case _ =>
        false
    }
    if (!isBlacklist) {
      if (listenOnly)
        throw new Exception(s"$vehicle can not accept $msg")
      else
        gcsActor.foreach(_ ! SendMavlinkToVehicle(msg))
    }
  }

  /**
   * We modify the actor to copy to S3 after the file is closed
   */
  private class TlogToS3Actor(filename: File, mission: Mission) extends LogBinaryMavlink(filename, deleteIfBoring = false, wantImprovedFilename = false) {
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

  def getTlogBytes(): Option[Array[Byte]] = {
    try {
      myTlogFile.map { finalfile =>
        // FIXME - super skanky - we use knowledge on where the temp file is stored
        val file = new File(finalfile.getCanonicalPath() + ".tmp")
        log.info(s"Reading working tlog from $file")

        // Tell our tlogger to write to disk
        // FIXME - wait for a reply
        tloggerOpt.foreach { _ ! FlushNowMessage }

        log.info(s"Returning tlog bytes to live actor client")
        com.geeksville.util.Using.using(new FileInputStream(file)) { source =>
          val byteArray = new Array[Byte](file.length.toInt)
          source.read(byteArray)
          byteArray
        }
      }
    } catch {
      case ex: Exception =>
        log.error(s"Failed getting tlog due to $ex", ex)
        None
    }
  }

  def summary = MissionSummary(startTime.map(TimestampedMessage.usecsToDate), currentTime.map(TimestampedMessage.usecsToDate),
    maxAltitude, maxGroundSpeed, maxAirSpeed, -1, flightDuration, endPosition.map(_.lat), endPosition.map(_.lon), softwareVersion = buildVersion, softwareGit = buildGit)

  private def sendMissionUpdate() {

    // we write updates to DB and are careful to reuse old summary ids
    // if this becomes expensive we could remove db writes

    // We only send updates when we have an active mission
    missionOpt.foreach { m =>
      updateDBSummary()

      vehicle.updateFromMission(this)
      //log.debug(s"Generating mission update (starttime=${m.summary.startTime}, curtime=$currentTime, loc=$endPosition): $m")
      publishEvent(MissionUpdate(m))
    }
  }

  /// Update our summary record in the DB
  private def updateDBSummary() {
    missionOpt.foreach { m =>
      val ns = summary
      val s: MissionSummary = m.summary

      // Super yucky copies of summary updates
      s.startTime = ns.startTime
      s.endTime = ns.endTime
      s.maxAlt = ns.maxAlt
      s.maxGroundSpeed = ns.maxGroundSpeed
      s.maxAirSpeed = ns.maxAirSpeed
      s.maxG = ns.maxG
      s.flightDuration = ns.flightDuration
      s.latitude = ns.latitude
      s.longitude = ns.longitude
      s.softwareVersion = ns.softwareVersion
      s.softwareGit = ns.softwareGit
      // Don't copy text - it will be genned as needed 
      // s.text = ns.text

      s.regenText()
      s.save()
    }
  }

  private def startMission(msg: StartMissionMsg) = blocking {
    // The following can fail if the client sends multiple start msgs
    assert(!tlogId.isDefined)

    tlogId = Some(UUID.randomUUID())
    log.debug(s"Starting tlog $tlogId")

    val f = LogBinaryMavlink.getFilename() // FIXME - create in temp directory instead
    myTlogFile = Some(f)

    val m = Mission.create(vehicle)
    missionOpt = Some(m)
    startTime = None
    m.notes = msg.notes

    tloggerOpt = Some(context.actorOf(Props(new TlogToS3Actor(f, m)), "tlogger"))

    // Pull privacy from vehicle if not specified
    var viewPriv = msg.viewPrivacy.getOrElse(AccessCode.DEFAULT).id
    if (viewPriv == AccessCode.DEFAULT_VALUE)
      viewPriv = vehicle.viewPrivacy

    m.viewPrivacy = viewPriv
    m.keep = msg.keep
    m.isLive = true
    m.save()

    // Add the initial summary record
    val s = summary
    s.create
    s.mission := m
    s.save()

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
    myTlogFile = None

    missionOpt.foreach { m =>
      blocking {
        m.isLive = false
        m.tlogId = tlogId.map(_.toString)

        updateDBSummary()

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
   * Find the supervisor responsible for a particular vehicle
   */
  def findOrCreate(vehicle: Vehicle, canAcceptCommands: Boolean) = actors.getOrCreate(vehicle.uuid.toString, Props(new LiveVehicleActor(vehicle, canAcceptCommands)))

  def find(vehicle: Vehicle) = actors.get(vehicle.uuid.toString)

}
