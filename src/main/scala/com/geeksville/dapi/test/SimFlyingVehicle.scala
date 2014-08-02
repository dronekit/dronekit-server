package com.geeksville.dapi.test

import akka.actor.Actor
import akka.actor.ActorLogging
import com.geeksville.apiproxy.GCSHooksImpl
import com.geeksville.util.Using._
import java.io.BufferedInputStream
import akka.actor.Props
import com.geeksville.mavlink.TlogStreamReceiver
import com.geeksville.mavlink.MavlinkEventBus
import com.geeksville.apiproxy.LiveUploader
import com.geeksville.apiproxy.GCSHooks
import com.geeksville.apiproxy.APIProxyActor
import java.util.UUID
import akka.actor.Terminated
import akka.actor.PoisonPill
import com.geeksville.apiproxy.StopMissionAndExitMsg
import com.geeksville.apiproxy.APIConstants
import com.geeksville.mavlink.MavlinkStreamReceiver
import scala.concurrent.duration._
import akka.pattern.ask
import scala.concurrent.Await
import akka.actor.Identify
import akka.util.Timeout
import com.geeksville.flight.VehicleSimulator
import org.mavlink.messages.MAVLinkMessage
import akka.actor.ActorContext
import grizzled.slf4j.Logging
import akka.actor.ActorSystem
import scala.util.Random
import com.geeksville.flight.Location
import com.geeksville.flight.HeartbeatSender
import org.mavlink.messages.MAV_TYPE
import org.mavlink.messages.MAV_MODE_FLAG
import org.mavlink.messages.MAV_AUTOPILOT
import java.net.NetworkInterface
import com.geeksville.akka.DebuggableActor
import com.geeksville.flight.SendMessage
import com.geeksville.akka.AkkaTools
import scala.util.Success
import scala.util.Failure
import com.geeksville.mavlink.MavlinkUtils

/// A vehicle that flies in circles sending real looking flight data
private class SimFlyingVehicle(systemId: Int, numSeconds: Int, val numPoints: Int, host: String, keep: Boolean) extends SimVehicle(systemId, host, keep) {
  import SimClient._
  import context._

  // Center our vehicles around various world points
  val centerLocations = Seq((21.2966980, -157.8480360), // HI
    // (37.517, -122.29), // SF Bay area
    (51.500, -0.1262), // London
    (35.68, 139.69) // Tokyo
    )
  val center = centerLocations(random.nextInt(centerLocations.size))

  val lineAngle = generation * random.nextDouble % (math.Pi * 2)
  val maxLen = 0.5 // in degrees
  val maxAlt = 100

  val ovalWidth = maxLen * random.nextDouble
  val ovalHeight = maxLen * random.nextDouble

  // 20-40sec per each path down the line
  val secondsPerLoop = 20.0 + random.nextDouble * 20
  val numLoops = numSeconds / secondsPerLoop

  var heading = random.nextInt(360)

  val interval = numSeconds.toDouble / numPoints

  /// A fake current position
  def curLoc = {
    // How far are we through our sim time 0 means all the time is left, 1 means done
    val pos = (numPoints.toDouble - numRemaining.toDouble) / numPoints

    val curLoopNum = numLoops * pos

    // Position on current loop (0 to 1)
    var len = curLoopNum - math.floor(curLoopNum)

    val alt = Some(maxAlt * math.sin(len))

    val onLine = false // We either do ovals or lines
    if (onLine) {
      // How far are we on our current loop (if on odd loops fly backwards in the other direction)
      val isOdd = (curLoopNum.toInt % 2) == 1

      if (isOdd)
        len = 1 - len

      Location(center._1 + len * math.cos(lineAngle),
        center._2 + len * math.sin(lineAngle),
        alt)
    } else {
      Location(center._1 + ovalWidth * math.cos(len * math.Pi * 2),
        center._2 + ovalHeight * math.sin(len * math.Pi * 2),
        alt)
    }
  }

  override def doNextStep() {
    import com.geeksville.util.MathTools._

    sendMavlink(makeVFRHud(random.nextFloat % 10, random.nextFloat % 10, random.nextInt(100), heading))
    sendMavlink(makeAttitude(toRad(heading).toFloat, toRad(heading).toFloat, toRad(heading).toFloat))
    sendMavlink(makePosition(curLoc))
    sendMavlink(makeGPSRaw(curLoc))
    if (random.nextInt(100) < 2)
      sendMavlink(makeStatusText("Random status text msg!"))

    // Fake up some mode changes
    if (random.nextInt(100) < 5) {
      //log.debug("Faking a mode change")
      heading = random.nextInt(360)
      gcsCustomMode = random.nextInt(5) + 1
      gcsBaseMode = (if (random.nextBoolean()) MAV_MODE_FLAG.MAV_MODE_FLAG_SAFETY_ARMED else 0) | MAV_MODE_FLAG.MAV_MODE_FLAG_AUTO_ENABLED
    }
  }
}

