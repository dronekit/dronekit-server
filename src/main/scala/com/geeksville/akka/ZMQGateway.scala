package com.geeksville.akka

import akka.zeromq._
import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorLogging
import akka.serialization.SerializationExtension
import java.lang.management.ManagementFactory
import scala.collection.mutable.HashMap
import akka.actor.ActorRef
import akka.util.ByteString
import akka.util.CompactByteString
import akka.actor.Terminated

/**
 * An actor that manages incoming packets from GCSes speaking ZeroMQ
 *
 * Clients are DEALERS who call in to this single ROUTER.  The router tracks the client ID for each request
 * and forwards the request to the ZeroMQGCSActor which is registered for that client ID.  Any messages back from
 * the ZeroMQGCSActor are mapped by ActorRef back to the appropriate identity and sent to the correct client.
 *
 * This is the following pattern:
 * http://zguide.zeromq.org/page:all#The-Asynchronous-Client-Server-Pattern
 *
 * Possibly use JeroMQ on the android client...
 *
 * FIXME - kill worker actors if we haven't heard from their client in a while
 */
class ZMQGateway(val workerActorFactory: Props, val zmqSocket: String = "tcp://127.0.0.1:5556") extends DebuggableActor with ActorLogging {
  import ZMQGateway._

  private val socket = ZeroMQExtension(context.system).newSocket(
    SocketType.Router,
    Listener(self),
    Bind(zmqSocket),
    HighWatermark(200),
    Linger(0))

  private val clientIdToActor = new HashMap[String, ActorRef]()
  private val actorToClientId = new HashMap[ActorRef, ByteString]()

  def receive = {
    // Incoming msg from ZMQ client DEALERS
    // the first frame is the client id, third is the message
    case m: ZMQMessage =>
      log.debug(s"Received ZMQ from client $m")
      val clientId = m.frame(0)
      val clientIdStr = clientId.utf8String
      val delimeter = m.frame(1)
      val payload = m.frame(2)

      // Get or create actor as needed
      val actor = clientIdToActor.getOrElseUpdate(clientIdStr, {
        val r = context.actorOf(workerActorFactory, clientIdStr)
        // Also keep a map in the other direction
        actorToClientId(r) = clientId
        context.watch(r)
        r
      })

      actor ! FromZMQ(payload)

    case m: ToZMQ =>
      log.debug(s"Sending from $sender to ZMQ: $m")
      val clientId = actorToClientId(sender)
      val zmsg = m.zmqMessage(clientId)
      socket ! zmsg

    case Terminated(child) =>
      log.debug(s"Removing ZMQ worker $child")
      actorToClientId.remove(child).foreach { clientId =>
        clientIdToActor.remove(clientId.utf8String)
      }
  }
}

object ZMQGateway {

  /// An inbound message from the world of ZMQ - sent to the worker actor for this client ID
  case class FromZMQ(msg: ByteString)

  /// An outbound message to a particular ZMQ client side DEALER (client ID will be found based on the sender
  /// ActorRef)
  case class ToZMQ(msg: ByteString) {
    def zmqMessage(clientId: ByteString) = ZMQMessage(clientId, ByteString.empty, msg)
  }
}