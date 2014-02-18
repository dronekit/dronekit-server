package com.geeksville.akka

import akka.actor.Actor
import com.geeksville.util.TCPListener
import akka.actor.Props
import scala.reflect.ClassTag
import akka.actor.ActorLogging

private case object UnexpectedMessage

/**
 * An actor that listens on a port # and spawns actors to handle any incoming connections.
 * The actor constructor is assumed to take a Socket as the only parameter.
 */
class TCPListenerActor[T <: Actor: ClassTag](portNum: Int) extends Actor with ActorLogging {

  private val connectionListener = new TCPListener(portNum, { s =>
    log.info(s"TCP connection received on $s")

    // FIXME - don't hardwire a node ID
    // FIXME - the parent of the camera should actually be a mesh supervisor
    context.actorOf(Props(implicitly[ClassTag[T]].runtimeClass, s))
  })

  override def postStop() {
    connectionListener.close()

    super.postStop()
  }

  override def receive = {
    // We need to receive something
    case UnexpectedMessage => log.error("This should never happen")
  }
}