package com.geeksville.dapi

import java.net.Socket
import com.geeksville.util.ThreadTools
import com.geeksville.util.Using._
import akka.actor.PoisonPill
import akka.actor.SupervisorStrategy
import java.net.SocketException
import java.io.BufferedOutputStream

/**
 * An actor that manages a TCP connection from a GCS
 */
class TCPGCSActor(private val socket: Socket) extends GCSActor {

  private lazy val toVehicle = new BufferedOutputStream(socket.getOutputStream, 8192)

  log.info(s"TCPGCSActor handling incoming $socket")

  // FIXME - change to use the fancy akka TCP API or zeromq so we don't need to burn a thread for each client)
  private val listenerThread = ThreadTools.createDaemon("TCPGCS")(readerFunct)
  listenerThread.start()

  override def postStop() {
    socket.close()

    super.postStop()
  }

  override protected def sendToVehicle(e: Envelope) {
    try {
      e.writeDelimitedTo(toVehicle)
      toVehicle.flush()
    } catch {
      case ex: SocketException =>
        log.warning(s"Socket already closed? ignoring $ex")
    }
  }

  private def readerFunct() {
    try {
      val default = Envelope.defaultInstance

      // Any Envelopes that come over TCP, extract the message and handle just like any other actor msg
      using(socket.getInputStream) { is =>
        // Real until we see an invalid envelope - FIXME, don't hang up in this case?
        Stream.continually(default.mergeDelimitedFromStream(is)).takeWhile { o =>
          // log.warning(s"Considering $o")
          o.isDefined
        }.foreach { mopt =>
          mopt.foreach { env =>
            //log.debug(s"Got packet $env")

            fromEnvelope(env).foreach { m =>
              //log.debug(s"Dispatching $m")
              self ! m
            }
          }
        }
      }
    } catch {
      case ex: SocketException =>
        log.error(s"Exiting TCPGCS due to: $ex")
    } finally {
      // If our reader exits, kill our actor
      self ! PoisonPill
    }
  }
}