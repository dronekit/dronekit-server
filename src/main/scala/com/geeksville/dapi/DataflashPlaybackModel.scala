package com.geeksville.dapi

import com.geeksville.flight.Waypoint
import java.io.ByteArrayInputStream
import com.geeksville.dataflash.DFReader
import scala.io.Source
import com.geeksville.dataflash.DFMessage
import org.mavlink.messages.ardupilotmega.msg_param_value
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import com.geeksville.flight.Location

class DataflashPlaybackModel extends PlaybackModel {
  /// A MAV_TYPE vehicle code
  var vehicleType: Option[Int] = None
  var autopilotType: Option[Int] = None

  def modeChanges: Seq[(Long, String)] = Seq.empty

  def positions: ArrayBuffer[TimestampedLocation] = ArrayBuffer.empty

  def waypoints: Seq[Waypoint] = Seq.empty

  private val params = HashMap[String, ROParamValue]()

  def parameters = params.values

  /**
   * Load messages from a raw mavlink tlog file
   */
  private def loadBytes(bytes: Array[Byte]) {
    import DFMessage._

    val reader = new DFReader
    var nowMsec = 0L
    def nowUsec = nowMsec * 1000

    reader.parseText(Source.fromRawBytes(bytes)).foreach { m =>
      debug(s"Considering $m")
      m.timeMSopt.foreach(nowMsec = _)

      m.typ match {
        case GPS =>
          for {
            lat <- m.latOpt
            lon <- m.lngOpt
          } yield {
            val loc = Location(lat, lon, m.altOpt)
            val tm = TimestampedLocation(nowUsec, loc)
            debug(s"Adding location $tm")
            positions.append(tm)
          }

        case MODE =>
        case PARM =>
          val msg = new msg_param_value(0, 0) // FIXME - params shouldn't assume mavlink msgs, but for now...
          val name = m.name
          msg.setParam_id(name)
          msg.param_value = m.value.toFloat
          params(name) = new ROParamValue(msg)
        case _ =>
        // Ignore
      }
    }
  }
}

object DataflashPlaybackModel {
  /**
   * Fully populate a model from bytes, or return None if bytes not available
   */
  def fromBytes(b: Array[Byte]) = {
    val model = new DataflashPlaybackModel
    model.loadBytes(b)
    model
  }

}
