package com.geeksville.dapi.model

import com.github.aselab.activerecord.Datestamps
import com.github.aselab.activerecord.annotations._
import org.squeryl.annotations.Transient
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import com.github.aselab.activerecord.dsl._

//case class Location(lat: Double, lon: Double, alt: Double)
//case class Attitude(pitch: Double, yaw: Double, roll: Double)

// FIXME - unify with real model
//case class Mavlink(time: Long, id: Int, payload: List[Byte])