package com.geeksville.dapi.model

import com.github.aselab.activerecord._
import com.github.aselab.activerecord.dsl._
import com.github.aselab.activerecord.scalatra._

object Tables extends ActiveRecordTables with ScalatraSupport {
  val vehicles = table[Vehicle]
  val missions = table[Mission]
}