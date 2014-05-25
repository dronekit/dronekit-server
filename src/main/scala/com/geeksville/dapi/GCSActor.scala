package com.geeksville.dapi

import akka.actor.Actor
import akka.actor.SupervisorStrategy
import akka.actor.ActorLogging
import com.geeksville.akka.NamedActorClient
import akka.actor.ActorRef
import akka.actor.Props
import scala.collection.mutable.HashMap
import com.google.protobuf.ByteString
import org.mavlink.messages._
import com.geeksville.mavlink.TimestampedMessage
import com.geeksville.dapi.model.Vehicle
import com.geeksville.dapi.model.Tables
import com.github.aselab.activerecord.dsl._
import com.geeksville.dapi.model.User
import java.util.UUID
import com.geeksville.util.Throttled
import com.geeksville.akka.DebuggableActor
import org.mavlink._
import scala.collection.mutable.Queue
import java.io.DataInputStream
import com.geeksville.util.QueueInputStream
import scala.collection.JavaConverters._

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

  // We pump the mavlink bytes through this queue
  private val mavlinkQueue = new Queue[java.lang.Byte]()
  private val dataStream = new DataInputStream(new QueueInputStream(mavlinkQueue))
  private val reader = new MAVLinkReader(dataStream, IMAVLinkMessage.MAVPROT_PACKET_START_V10)

  private val msgLogThrottle = new Throttled(30 * 1000)

  private var myVehicle: Option[ActorRef] = None
  private val vehicles = HashMap[VehicleBinding, ActorRef]()

  private var startTime: Option[Long] = None

  private var userOpt: Option[User] = None

  // vehicle IDs might come _after_ start mission - so keep it around so we can resend to new vehicle actors as needed
  private var currentMission: Option[StartMissionMsg] = None

  private def user = userOpt.get

  override def toString = s"GCSActor with ${vehicles.size} vehicles"

  // If we encounter a problem we want to hang up on the client and have them reconnect...
  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy

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
    // It is possible for vehicle actors to tell us they are now talking to a different GCS.  In
    // that case we forget about them
    case VehicleDisconnected() =>
      log.warning(s"Vehicle $sender has abandoned our GCS")

      // Expensive way to find and remove the mapping
      val removeOpt = vehicles.find { case (k, v) => v == sender }.map(_._1)

      removeOpt match {
        case Some(k) =>
          vehicles.remove(k)
        case None =>
          log.error(s"Count not find vehicle $sender in our children")
      }

    case SendMavlinkToVehicle(msg) =>
      log.debug(s"Sending mavlink to vehicle $msg")
      sendToVehicle(Envelope(mavlink = Some(MavlinkMsg(1, List(ByteString.copyFrom(msg.encode))))))

    case msg: PingMsg => // We just reply to pings
      sendToVehicle(Envelope(pingResponse = Some(PingResponseMsg(msg.nonce))))

    case msg: PingResponseMsg =>
      log.warning("Ignoring unexpected ping response") // We currently aren't sending pings, so why did the client send this?

    case msg: SenderIdMsg =>
      checkLoggedIn()

      log.info(s"Binding vehicle $msg")
      //log.info(s"Binding vehicle $msg, user has " + user.vehicles.toList.mkString(","))
      if (msg.vehicleUUID == "GCS")
        log.warning("ignoring GCS ID")
      else {
        val uuid = UUID.fromString(msg.vehicleUUID)
        val vehicle = user.getOrCreateVehicle(uuid)

        val actor = LiveVehicleActor.findOrCreate(vehicle, msg.canAcceptCommands)
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

      // Put all payload into our input stream
      msg.packet.foreach { pRaw =>
        // Prevent client trying to crash the server by uploading an endless stream of non mavlink
        if (mavlinkQueue.size > 512) {
          log.error("Client sending too much crap - discarding")
          mavlinkQueue.clear()
        }

        //log.debug(s"Enuqueuing ${pRaw.size} bytes")
        mavlinkQueue.enqueue(pRaw.asScala.toSeq: _*)
      }

      var p: MAVLinkMessage = null
      do {
        p = reader.getNextMessageWithoutBlocking()
        if (p != null) {
          //log.debug(s"Processing $p")

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
      } while (p != null)

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
            log.info("Handling user create for " + msg.username)
            if (found.isDefined) {
              log.error(s"Username unavailable: " + msg.username)
              LoginResponseMsg(LoginResponseMsg.ResponseCode.NAME_UNAVAILABLE)
            } else {
              // Create new user and login

              val u = User.create(msg.username, msg.password.get, msg.email, None)
              try {

                userOpt = Some(u)

                log.info(s"Created user " + msg.username)
                if (msg.email.isDefined)
                  MailTools.sendWelcomeEmail(u)

                LoginResponseMsg(LoginResponseMsg.ResponseCode.OK)
              } catch {
                case ex: Exception =>
                  // If we fail while sending the conf email, return error to client and don't create the record
                  u.delete()
                  throw ex
              }
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

  /**
   * Given an envelope return the various populated members
   */
  protected def fromEnvelope(env: Envelope) = {
    // FIXME - use the enum to more quickly find the payload we care about
    Seq(env.mavlink, env.login, env.setSender, env.startMission, env.stopMission, env.note, env.ping, env.pingResponse).flatten
  }
}

object GCSActor {
  /**
   * convert raw bytes to a mavlink packet object
   * FIXME: merge this with the (not yet written) replacement for the java glue
   */
  /* No longer used
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
  } */
}