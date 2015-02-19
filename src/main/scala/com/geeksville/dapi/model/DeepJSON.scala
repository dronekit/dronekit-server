package com.geeksville.dapi.model

import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s._

object DeepJSON {

  object Flavor extends Enumeration {
    type Type = Value
    val Deep, ShallowObj, ShallowId = Value
  }

  def asJSONArray(records: Seq[DapiRecord], flavor: Flavor.Type)(implicit format: Formats): Seq[JValue] = {
    records.map { v =>
      flavor match {
        case Flavor.Deep =>
          Extraction.decompose(v)
        case Flavor.ShallowObj =>
          ("id" -> v.id): JObject
        case Flavor.ShallowId =>
          new JInt(v.id)
      }
    }
  }
}
