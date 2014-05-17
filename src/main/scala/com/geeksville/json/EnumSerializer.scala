package com.geeksville.json

import org.json4s.CustomSerializer
import org.json4s.JsonAST._
import com.geeksville.util.DateTools
import net.sandrogrzicic.scalabuff.Enum

/* FIXME - not yet written
class EnumSerializer(typ: Enum) extends CustomSerializer[Enum](format => (
  {
    case JString(s) =>
      Enum.valueOf
  },
  {
    case x: Enum =>
      JString(x.)
  })) */ 