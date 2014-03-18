package com.geeksville.dapi.model

import com.github.aselab.activerecord._
import com.github.aselab.activerecord.dsl._
import com.github.aselab.activerecord.scalatra._

object Tables extends ActiveRecordTables with ScalatraSupport {
  val vehicles = table[Vehicle]
  val missions = table[Mission]
  val missionSummaries = table[MissionSummary]
  val users = table[User]

  override def initialize(config: Map[String, Any]) {
    super.initialize(config)

    // FIXME - don't always reseed
    //transaction {
    /*
    val id = "test-bob@3drobotics.com"
    if (!User.find(id).isDefined) {
      val u = User("Tester Bob", id).create
      u.password = "sekrit"
      u.save()
    }
    */
    //}
  }
}