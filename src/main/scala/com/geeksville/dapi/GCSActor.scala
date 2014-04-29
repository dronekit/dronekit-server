package com.geeksville.dapi

import akka.actor.Actor
import akka.actor.ActorLogging
import com.geeksville.akka.NamedActorClient
import akka.actor.ActorRef
import akka.actor.Props
import scala.collection.mutable.HashMap
import com.google.protobuf.ByteString
import org.mavlink.MAVLinkCRC
import org.mavlink.IMAVLinkCRC
import org.mavlink.messages._
import com.geeksville.mavlink.TimestampedMessage
import com.geeksville.dapi.model.Vehicle
import com.geeksville.dapi.model.Tables
import com.github.aselab.activerecord.dsl._
import com.geeksville.dapi.model.User
import java.util.UUID
import com.geeksville.util.Throttled
import com.geeksville.akka.DebuggableActor

/// All messages after connection are identified by this tuple
case class VehicleBinding(interface: Int, sysId: Int)

/// Indicates malformed mavlink payload
class MavlinkException(msg: String) extends Exception(msg)

/// Sent to GCSActor when we want to send a message to the vehicle
case class SendMavlinkToVehicle(msg: MAVLinkMessage)

/**
 * Any actor that acts as the sister of a GCS client.  One instance per connected GCS (i.e this actor state includes knowledge of which GCS it is talking to
 *
 * FIXME - use a state machine to track logged in state vs not
 */
abstract class GCSActor extends DebuggableActor with ActorLogging {
  import GCSActor._

  private val msgLogThrottle = new Throttled(5000)

  private var myVehicle: Option[ActorRef] = None
  private val vehicles = HashMap[VehicleBinding, ActorRef]()

  private var startTime: Option[Long] = None

  private var userOpt: Option[User] = None

  // vehicle IDs might come _after_ start mission - so keep it around so we can resend to new vehicle actors as needed
  private var currentMission: Option[StartMissionMsg] = None

  private def user = userOpt.get

  override def toString = s"GCSActor with ${vehicles.size} vehicles"

  private def getVehicle(uuid: UUID) = {
    // Vehicle.find(uuid.toString)
    log.debug(s"Looking for $uuid inside of $user")
    user.vehicles.where(_.uuid === uuid).headOption
  }

  /**
   * find a vehicle object for a specified UUID, associating it with our user if needed
   */
  private def getOrCreateVehicle(uuid: UUID) = getVehicle(uuid).getOrElse {
    log.warning(s"Vehicle $uuid not found in $user - creating")
    val v = Vehicle(uuid).create
    user.vehicles << v
    v.save
    user.save // FIXME - do I need to explicitly save?
    v
  }

  protected def sendToVehicle(e: Envelope)

  private def checkLoggedIn() {
    if (!userOpt.isDefined) {
      log.error("HACK ATTEMPT: client not logged in")
      throw new Exception("Not logged-in")
    }
  }

  /// Helper function for making user visible messages
  private def createMessage(s: String) =
    Some(ShowMsg(text = Some(s), priority = ShowMsg.Priority.MEDIUM))

  def receive = {
    case SendMavlinkToVehicle(msg) =>
      log.debug(s"Sending mavlink to vehicle $msg")
      sendToVehicle(Envelope(mavlink = Some(MavlinkMsg(1, List(ByteString.copyFrom(msg.encode))))))

    case msg: SetVehicleMsg =>
      checkLoggedIn()

      log.info(s"Binding vehicle $msg")
      //log.info(s"Binding vehicle $msg, user has " + user.vehicles.toList.mkString(","))
      if (msg.vehicleUUID == "GCS")
        log.warning("ignoring GCS ID")
      else {
        val uuid = UUID.fromString(msg.vehicleUUID)
        val vehicle = getOrCreateVehicle(uuid)

        val actor = LiveVehicleActor.find(vehicle, msg.canAcceptCommands)
        vehicles += VehicleBinding(msg.gcsInterface, msg.sysId) -> actor
        actor ! VehicleConnected()

        // The actor might need to immediately start a mission
        currentMission.foreach { actor ! _ }
      }

    case msg: MavlinkMsg =>
      //log.debug(s"Mavlink from vehicle: $msg")
      checkLoggedIn()

      // If the user provided a time then use it, otherwise use our local time
      val timestamp = msg.deltaT.map(_ + startTime.get).getOrElse(System.currentTimeMillis * 1000)

      msg.packet.foreach { pRaw =>
        val p = decodeMavlink(pRaw)

        // Have our vehicle handle the message
        val timestamped = TimestampedMessage(timestamp, p)
        val vehicle = vehicles.get(VehicleBinding(msg.srcInterface, p.sysId))
        val probablyGCS = p.sysId > 200 // Don't spam the log about GCS msgs - just send them to every vehicle
        if (!vehicle.isDefined && !probablyGCS) {
          msgLogThrottle.withIgnoreCount { numIgnored: Int =>
            log.warning(s"Unknown sysId=${p.sysId} send to all: $p (and $numIgnored others)")
          }

          vehicles.values.foreach { _ ! timestamped }
        } else
          vehicle.foreach { _ ! timestamped }
      }

    // For now we let each vehicle handle these msgs
    case msg: StartMissionMsg =>
      checkLoggedIn()
      vehicles.values.foreach { _ forward msg }
      currentMission = Some(msg)

    case msg: StopMissionMsg =>
      checkLoggedIn()
      vehicles.values.foreach { _ forward msg }
      currentMission = None

    case msg: LoginMsg =>
      val response = try {
        startTime = msg.startTime
        val found = User.find(msg.username)
        msg.code match {
          case LoginRequestCode.LOGIN =>

            if (!found.isDefined) {
              log.error(s"Bad username " + msg.username)
              LoginResponseMsg(LoginResponseMsg.ResponseCode.BAD_PASSWORD, createMessage("Bad username or password"))
            } else if (!found.get.isPasswordGood(msg.password.get)) {
              log.error(s"Bad password for " + msg.username)
              LoginResponseMsg(LoginResponseMsg.ResponseCode.BAD_PASSWORD, createMessage("Bad username or password"))
            } else {
              // We are now logged in
              userOpt = found
              log.info(s"Logged in " + msg.username)
              LoginResponseMsg(LoginResponseMsg.ResponseCode.OK)
            }

          case LoginRequestCode.CHECK_USERNAME =>
            log.info(s"Checking username for " + msg.username + " available=" + !found.isDefined)
            LoginResponseMsg(if (found.isDefined)
              LoginResponseMsg.ResponseCode.NAME_UNAVAILABLE
            else
              LoginResponseMsg.ResponseCode.OK)

          case LoginRequestCode.CREATE =>
            if (found.isDefined) {
              log.error(s"Username unavailable: " + msg.username)
              LoginResponseMsg(LoginResponseMsg.ResponseCode.NAME_UNAVAILABLE)
            } else {
              // Create new user and login

              val u = User.create(msg.username, msg.password.get, msg.email, None)
              userOpt = Some(u)

              log.info(s"Created user " + msg.username)
              LoginResponseMsg(LoginResponseMsg.ResponseCode.OK)
            }

          case _ =>
            throw new Exception("Unexpected login request: " + msg.code)
        }
      } catch {
        case ex: Exception =>
          // Always send a response - even if we had an exception
          log.error(ex, s"Server bug: $ex")
          LoginResponseMsg(LoginResponseMsg.ResponseCode.SERVER_FAULT, createMessage(ex.getMessage))
      }
      log.debug(s"Sending login response: $response")
      sendToVehicle(Envelope(loginResponse = Some(response)))
  }

  // Tell our vehicles we've lost the link
  override def postStop() {
    log.info("Shutting down GCSActor")
    vehicles.values.foreach(_ ! VehicleDisconnected())
    super.postStop()
  }
}

object GCSActor {
  /**
   * convert raw bytes to a mavlink packet object
   * FIXME: merge this with the (not yet written) replacement for the java glue
   */
  def decodeMavlink(bytes: ByteString) = {
    val start = bytes.byteAt(0).toInt & 0xff
    val len = bytes.byteAt(1).toInt & 0xff
    def seq = bytes.byteAt(2).toInt & 0xff
    def sysId = bytes.byteAt(3).toInt & 0xff
    def compId = bytes.byteAt(4).toInt & 0xff
    def msgId = bytes.byteAt(5).toInt & 0xff
    def payload = bytes.substring(6, 6 + len)
    def crcLow = bytes.byteAt(6 + len)
    def crcHigh = bytes.byteAt(6 + len + 1)

    if (start != 0xfe)
      throw new MavlinkException("Invalid start")

    if (len + 8 != bytes.size())
      throw new MavlinkException(s"len was ${len + 8}, but expected ${bytes.size()}")

    // FIXME, change the mavlink lib to work better with bytestrings
    val bytesArray = bytes.toByteArray
    var crc = MAVLinkCRC.crc_calculate_decode(bytesArray, len)
    if (IMAVLinkCRC.MAVLINK_EXTRA_CRC) {
      // CRC-EXTRA for Mavlink 1.0
      crc = MAVLinkCRC.crc_accumulate(IMAVLinkCRC.MAVLINK_MESSAGE_CRCS(msgId).toByte, crc)
    }

    val crcl = (crc & 0x00FF).toByte
    val crch = ((crc >> 8) & 0x00FF).toByte
    if ((crcl != crcLow) || (crch != crcHigh))
      throw new MavlinkException("Bad message CRC")

    val msg = MAVLinkMessageFactory.getMessage(msgId, sysId, compId, payload.toByteArray)
    if (msg == null)
      throw new MavlinkException(s"Error decoding msgId $msgId")

    msg.sequence = seq
    msg
  }
}