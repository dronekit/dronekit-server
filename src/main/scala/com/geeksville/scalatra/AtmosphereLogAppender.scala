package com.geeksville.scalatra

import org.scalatra.atmosphere._
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.OutputStreamAppender
import java.io.ByteArrayOutputStream
import org.json4s.JsonAST.JString

class AtmosphereLogAppender extends OutputStreamAppender[ILoggingEvent] {
  private val stream = new ByteArrayOutputStream() {
    override def write(b: Array[Byte], off: Int, len: Int) = {
      val r = super.write(b, off, len)

      val str = toString()
      reset()

      // println(s"Sending to atmo $str")

      val route = "/api/v1/admin/log"

      import scala.concurrent.ExecutionContext.Implicits.global
      AtmosphereTools.broadcast(route, "log", JString(str))
      r
    }
  }

  override def start() {
    setOutputStream(stream)
    super.start()
  }

}