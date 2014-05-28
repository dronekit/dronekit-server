package com.geeksville.json

import org.json4s.CustomSerializer
import org.json4s.JsonAST._
import com.geeksville.util.DateTools
import net.sandrogrzicic.scalabuff.Enum
import com.google.protobuf.Internal.EnumLite
import com.geeksville.dapi.AccessCode

object EnumSerializer {

  def create[T <: Enum](typ: T)(implicit m: Manifest[typ.EnumVal]) = {
    class EnumSerializerImpl extends CustomSerializer[typ.EnumVal](format => (
      {
        case JString(s) =>
          println(s"Trying to decode $s into $typ")
          val found = typ.values.find(_.name == s)
          found.getOrElse(throw new Exception(s"Invalid $s not found in $typ"))
      },
      {
        case u: EnumLite =>
          JString(u.toString)
      }))

    new EnumSerializerImpl
  }
}
