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
import akka.actor.Cancellable
import scala.concurrent.duration._
import akka.actor.PoisonPill

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
class ZMQGateway(val workerActorFactory: Props, val zmqSocket: String = "tcp://*:5556") extends DebuggableActor with ActorLogging {
  import ZMQGateway._

  private val socket = ZeroMQExtension(context.system).newSocket(
    SocketType.Router,
    Listener(self),
    Bind(zmqSocket),
    HighWatermark(200),
    // Wait 200ms to give any last msgs a hope of getting out
    Linger(200))

  private case class ActorInfo(val actor: ActorRef) {
    var disconnectTimer: Option[Cancellable] = None

    // Start the initial deathwatch timeout
    resetTimeout()

    def cancelTimeout() {
      disconnectTimer.foreach(_.cancel())
    }

    // If we don't receive a packet often enough from the client we will declare loss of connection
    def resetTimeout() {
      import context._

      cancelTimeout()
      disconnectTimer = Some(context.system.scheduler.scheduleOnce(30 seconds, actor, ZMQConnectionLost))
    }
  }

  private val clientIdToActor = new HashMap[String, ActorInfo]()
  private val actorToClientId = new HashMap[ActorRef, ByteString]()

  override def postStop() {
    clientIdToActor.values.foreach(_.cancelTimeout())
    super.postStop()
  }

  def receive = {
    // Incoming msg from ZMQ client DEALERS
    // the first frame is the client id, third is the message
    case m: ZMQMessage =>
      val clientId = m.frame(0)
      val clientIdStr = clientId.utf8String
      // val delimeter = m.frame(1)
      val payload = m.frame(2)

      // FIXME - validate that the client id looks like a UUID (so we don't generate invalid actor names)

      log.debug(s"Received ZMQ from $clientIdStr")

      // Get or create actor as needed
      val ainfo = clientIdToActor.getOrElseUpdate(clientIdStr, {
        val r = context.actorOf(workerActorFactory, clientIdStr)
        // Also keep a map in the other direction
        actorToClientId(r) = clientId
        context.watch(r)
        ActorInfo(r)
      })

      ainfo.resetTimeout() // We just received a packet - stave off death a bit longer
      ainfo.actor ! FromZMQ(payload)

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
    def zmqMessage(clientId: ByteString) = ZMQMessage(clientId, msg)
  }

  /// Sent to the actor if we detect loss of link to the client.  Actor is expected to kill itself
  case object ZMQConnectionLost extends PoisonPill
}