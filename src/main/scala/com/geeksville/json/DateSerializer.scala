package com.geeksville.json

import org.json4s.CustomSerializer
import org.json4s.JsonAST._
import java.util.Date
import com.geeksville.util.DateTools

object DateSerializer extends CustomSerializer[Date](format => (
  {
    case JString(s) =>
      DateTools.fromISO8601(s)
  },
  {
    case x: Date =>
      JString(DateTools.toISO8601(x))
  }))