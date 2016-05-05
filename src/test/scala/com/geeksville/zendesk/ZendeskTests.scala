package com.geeksville.zendesk

import org.scalatra.test.scalatest._
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.json4s.Formats
import org.json4s.DefaultFormats
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import java.io.File
import org.scalatra.test.BytesPart
import grizzled.slf4j.Logging
import org.scalatest.GivenWhenThen
import scala.util.Random
import com.geeksville.flight.Location
import com.geeksville.util.Using._

class ZendeskTests extends FunSuite with Logging with GivenWhenThen {

  ignore("zendesk") {
    val content =
      """
        |This is a semi-automated ticket opened by a user (http://www.droneshare.com/user/kevinh) of Droneshare.
        |
        |They described their issue as "Crashed into a tree" while running this mission. (http://www.droneshare.com/mission/x)
        |
        |The email address from this requester is based on their droneshare account and that user has received an automated Zendesk email telling them that 3DR support will be responding.
      """.stripMargin
    val r = ZendeskClient.createTicket("Kevin TesterName", "kevinh@geeksville.com", "Testing please ignore", content)
    println(r)
    println("Ticket id: " + r.getId)
  }

}
