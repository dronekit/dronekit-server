package com.geeksville.dapi

import akka.actor.Actor
import akka.util.ByteString
import akka.actor.ActorLogging

case class MsgHandleMavlink(srcInterface: Int, message: ByteString)
case class MsgLogin(username: String, password: String)

/**
 * Any actor that acts as the sister of a GCS client.  One instance per connected GCS (i.e this actor state includes knowledge of which GCS it is talking to
 *
 * FIXME - use a state machine to track logged in state vs not
 */
class GCSActor extends Actor with ActorLogging {
  def receive = {
    case x: MsgHandleMavlink =>
    case x: MsgLogin =>
  }
}