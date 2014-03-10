package com.geeksville.dapi

import akka.actor.Actor
import akka.actor.ActorLogging
import com.geeksville.akka.NamedActorClient
import akka.actor.ActorRef
import akka.actor.Props
import scala.collection.mutable
import com.google.protobuf.ByteString
import org.mavlink.MAVLinkCRC
import org.mavlink.IMAVLinkCRC
import org.mavlink.messages.MAVLinkMessageFactory
import com.geeksville.mavlink.TimestampedMessage
import com.geeksville.dapi.model.Vehicle
import com.geeksville.dapi.model.Tables
import com.github.aselab.activerecord.dsl._
import com.geeksville.dapi.model.User
import java.util.UUID

/// All messages after connection are identified by this tuple
case class VehicleBinding(interface: Int, sysId: Int)

/// Indicates malformed mavlink payload
class MavlinkException(msg: String) extends Exception(msg)

/**
 * Any actor that acts as the sister of a GCS client.  One instance per connected GCS (i.e this actor state includes knowledge of which GCS it is talking to
 *
 * FIXME - use a state machine to track logged in state vs not
 */
class GCSActor extends Actor with ActorLogging {
  import GCSActor._

  private var myVehicle: Option[ActorRef] = None
  private val vehicles = new mutable.HashMap[VehicleBinding, ActorRef]()

  private var startTime: Option[Long] = None

  private var userOpt: Option[User] = None

  private def user = userOpt.get

  def receive = {
    case msg: SetVehicleMsg =>
      log.info(s"Binding vehicle $msg")
      val uuid = UUID.fromString(msg.vehicleUUID)
      val vehicle = Vehicle.find(uuid).getOrElse {
        val v = Vehicle(uuid).create
        user.vehicles += v
        v.save
        user.save // FIXME - do I need to explicitly save?
        v
      }

      val actor = vehicleActors.getOrCreate(uuid.toString, Props(new LiveVehicleActor(vehicle)))
      vehicles += VehicleBinding(msg.gcsInterface, msg.sysId) -> actor
      actor ! VehicleConnected()

    case msg: MavlinkMsg =>
      log.info(s"Got $msg")

      // If the user provided a time then use it, otherwise use our local time
      val timestamp = msg.deltaT.map(_ + startTime.get).getOrElse(System.currentTimeMillis * 1000)

      msg.packet.foreach { pRaw =>
        val p = decodeMavlink(pRaw)

        // Have our vehicle handle the message
        val vehicle = vehicles(VehicleBinding(msg.srcInterface, p.sysId))
        vehicle ! TimestampedMessage(timestamp, p)
      }

    case msg: LoginMsg =>
      log.info(s"FIXME ignoring login $msg")
      startTime = msg.startTime
      userOpt = Tables.users.where(_.login === msg.username).headOption

    case x @ _ =>
      log.warning(s"Ignoring $x" + x.getClass())
  }

  // Tell our vehicles we've lost the link
  override def postStop() {
    vehicles.values.foreach(_ ! VehicleDisconnected())
    super.postStop()
  }
}

object GCSActor {
  // Actors are named based on their globally unique vehicle ID
  private val vehicleActors = new NamedActorClient("vehicles")

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