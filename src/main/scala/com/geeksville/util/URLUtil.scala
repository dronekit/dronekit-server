package com.geeksville.util

object URLUtil {
  /// Camelcase convert a string, capitalizing first char
  def capitalize(s: String) = s.head.toUpper.toString + s.tail
}