package com.geeksville.dapi

import java.net.Socket
import com.geeksville.util.ThreadTools
import com.geeksville.util.Using._
import akka.actor.PoisonPill

/**
 * An actor that manages a TCP connection from a GCS
 */
class TCPGCSActor(private val socket: Socket) extends GCSActor {

  log.info(s"TCPGCSActor handling incoming $socket")

  // FIXME - change to use the fancy akka TCP API or zeromq so we don't need to burn a thread for each client)
  private val listenerThread = ThreadTools.createDaemon("TCPGCS")(readerFunct)
  listenerThread.start()

  override def postStop() {
    socket.close()

    super.postStop()
  }

  private def readerFunct() {
    try {
      // Any Envelopes that come over TCP, extract the message and handle just like any other actor msg
      using(socket.getInputStream) { is =>
        // Real until we see an invalid envelope - FIXME, don't hang up in this case?
        Stream.continually(Envelope.parseDelimitedFrom(is)).takeWhile(_.isDefined).foreach { mopt =>
          mopt.foreach { env =>
            log.debug(s"Got packet $env")

            // FIXME - use the enum to more quickly find the payload we care about
            Seq(env.mavlink, env.login, env.setVehicle).flatten.foreach { m =>
              log.debug(s"Dispatching $m")
              self ! m
            }
          }
        }
      }
    } catch {
      case ex: Throwable =>
        log.error(s"Exiting TCPGCS due to: $ex")
    } finally {
      // If our reader exits, kill our actor
      self ! PoisonPill
    }
  }
}