package com.geeksville.dapi

import akka.actor.SupervisorStrategy
import java.net.SocketException
import java.io.BufferedOutputStream
import akka.actor._
import com.geeksville.akka.ZMQGateway
import akka.util.ByteString

/**
 * An actor that manages a TCP connection from a GCS
 */
class ZMQGCSActor extends GCSActor {
  import ZMQGateway._

  private val defaultEnvelope = Envelope.defaultInstance

  override def receive = myReceive.orElse(super.receive)

  private def myReceive: Receive = {
    case FromZMQ(msg) =>
      val env = defaultEnvelope.mergeFrom(msg.toArray)
      log.debug(s"Got packet $env")

      fromEnvelope(env).foreach { m =>
        log.debug(s"Dispatching $m")
        receive(m)
      }
  }

  override protected def sendToVehicle(e: Envelope) {
    log.debug(s"Sending to vehicle $e")
    context.parent ! ToZMQ(ByteString(e.toByteArray))
  }

}