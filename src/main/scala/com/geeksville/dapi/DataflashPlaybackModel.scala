package com.geeksville.dapi

import com.geeksville.flight.Waypoint
import java.io.ByteArrayInputStream
import com.geeksville.dataflash.DFReader
import scala.io.Source
import com.geeksville.dataflash.DFMessage
import org.mavlink.messages.ardupilotmega._
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import com.geeksville.flight.Location

class DataflashPlaybackModel extends PlaybackModel {
  /// A MAV_TYPE vehicle code
  var vehicleType: Option[Int] = None
  var autopilotType: Option[Int] = None

  val modeChanges: ArrayBuffer[(Long, String)] = ArrayBuffer.empty

  val positions: ArrayBuffer[TimestampedLocation] = ArrayBuffer.empty

  val waypoints: ArrayBuffer[Waypoint] = ArrayBuffer.empty

  private val params = HashMap[String, ROParamValue]()

  def parameters = params.values

  /**
   * Load messages from a raw mavlink tlog file
   */
  private def loadBytes(bytes: Array[Byte]) {
    import DFMessage._

    val reader = new DFReader
    var nowMsec = 0L
    var gpsOffsetUsec = 0L
    def nowUsec = gpsOffsetUsec + nowMsec * 1000

    reader.parseText(Source.fromRawBytes(bytes)).foreach { m =>
      def dumpMessage() = debug(s"Considering $m")

      def updateTime() {
        m.timeMSopt.foreach(nowMsec = _)
        currentTime = Some(nowUsec)
      }

      m.typ match {
        case GPS =>
          //dumpMessage()

          // Cancel out the last known msec offset, using GPS time as the new zero
          gpsOffsetUsec = m.gpsTimeUsec - (nowMsec * 1000)
          if (!startTime.isDefined)
            startTime = Some(nowUsec)

          for {
            lat <- m.latOpt
            lon <- m.lngOpt
          } yield {
            if (lat != 0.0 && lon != 0.0) {
              val loc = Location(lat, lon, m.altOpt)
              m.altOpt.foreach { a =>
                maxAltitude = math.max(maxAltitude, a)
              }
              m.spdOpt.foreach { a =>
                maxGroundSpeed = math.max(maxGroundSpeed, a)
              }

              val tm = TimestampedLocation(nowUsec, loc)
              //debug(s"Adding location $tm")
              positions.append(tm)
              endPosition = Some(loc)
            }
          }

        case NTUN =>
          m.arspdOpt.foreach { a =>
            maxAirSpeed = math.max(maxAirSpeed, a)
          }

        case CMD =>
          dumpMessage()
          for {
            // TimeMS,CTot,CNum,CId,Prm1,Prm2,Prm3,Prm4,Lat,Lng,Alt
            // 11725, 5, 1, 16, 0.000000, 0.000000, 0.000000, 0.000000, 39.98196, -87.85937, 80.00000
            ctot <- m.ctotOpt
            cnum <- m.cnumOpt

            cid <- m.cidOpt

            prm1 <- m.prm1Opt
            prm2 <- m.prm2Opt
            prm3 <- m.prm3Opt
            prm4 <- m.prm4Opt
            lat <- m.latOpt
            lon <- m.lngOpt
            alt <- m.altOpt
          } yield {
            val msg = new msg_mission_item(0, 0)
            msg.param1 = prm1.toFloat
            msg.param2 = prm2.toFloat
            msg.param3 = prm3.toFloat
            msg.param4 = prm4.toFloat
            msg.seq = cnum
            msg.command = cid
            msg.x = lat.toFloat
            msg.y = lon.toFloat
            msg.z = alt.toFloat
            val w = new Waypoint(msg)
            debug(s"Adding $w")
            waypoints.append(w)
          }

        case IMU =>
          updateTime()

        case ATT =>
          updateTime()

        case MODE =>
          dumpMessage()
          modeChanges.append(nowUsec -> m.mode)

        case PARM =>
          // dumpMessage()

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
