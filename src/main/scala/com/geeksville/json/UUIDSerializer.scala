package com.geeksville.json

import org.json4s.CustomSerializer
import java.util.UUID
import org.json4s.JsonAST._

object UUIDSerializer extends CustomSerializer[UUID](format => (
  {
    case JString(s) =>
      UUID.fromString(s)
  },
  {
    case x: UUID =>
      JString(x.toString)
  }))