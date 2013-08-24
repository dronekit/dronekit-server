package com.geeksville.util

import java.io._

/// Write CSV values
/// @param out becomes owned by this class, we will close it when close is called on the writer
class CSVWriter(out: StringBuilder, headers: Seq[String]) {
  val numcols = headers.length

  out ++= headers.mkString(",")
  out += '\n'

  /// Spit out a new row of CSV data
  def emit(data: Seq[Any]) {
    assert(data.length == numcols)

    out ++= data.mkString(",")
    out += '\n'
  }
}
