package com.geeksville.util

import java.io._

/// Write CSV values
/// @param out becomes owned by this class, we will close it when close is called on the writer
class CSVWriter(out: StringBuilder, headers: Seq[String]) {
  private val numcols = headers.length
  private val headersToCol = Map(headers.zipWithIndex: _*)

  private def println(s: String) {
    out ++= s
    out ++= "\r\n"
  }

  println(headers.mkString(","))

  /// Spit out a new row of CSV data
  def emitCols(data: Seq[Any]) {
    assert(data.length == numcols)

    println(data.map { Option(_).getOrElse("") }.mkString(","))
  }

  def emit(colValPairs: (String, Any)*) {
    val byCol = new Array[String](numcols)
    colValPairs.foreach {
      case (k, v) =>
        byCol(headersToCol(k)) = v.toString
    }
    emitCols(byCol)
  }
}
