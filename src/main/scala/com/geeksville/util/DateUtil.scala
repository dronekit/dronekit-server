package com.geeksville.util

import java.util.TimeZone
import java.text.SimpleDateFormat

object DateUtil {
  /// FIXME - move this some place better as a singleton
  val isoDateFormat = {
    val tz = TimeZone.getTimeZone("UTC")
    val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'")
    df.setTimeZone(tz)
    df
  }
}