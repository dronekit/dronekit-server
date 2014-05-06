package com.geeksville.scalatra

import org.scalatra.atmosphere._
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.OutputStreamAppender
import java.io.ByteArrayOutputStream
import org.json4s.JsonAST.JString
import com.geeksville.akka.MockAkka
import scala.concurrent.ExecutionContext

class AtmosphereLogAppender extends OutputStreamAppender[ILoggingEvent] {
  lazy val isTesting = ScalatraTools.isTesting // atmo no work in test world

  private val stream = new ByteArrayOutputStream() {
    override def write(b: Array[Byte], off: Int, len: Int) = {
      if (!isTesting) {
        val r = super.write(b, off, len)

        val str = toString()
        reset()

        // println(s"Sending to atmo $str")

        val route = "/api/v1/admin/log"

        // Yuck FIXME - must be a nicer way to find execution context
        // Also a nicer way to see if we've created our akka context
        if (MockAkka.configOverride.isDefined) {
          implicit val context: ExecutionContext = MockAkka.system.dispatcher
          AtmosphereTools.broadcast(route, "log", JString(str))
          r
        }
      }
    }
  }

  override def start() {
    setOutputStream(stream)
    super.start()
  }

}