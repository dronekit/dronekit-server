package com.geeksville.scalatra

object ScalatraTools {
  def isTesting = System.getProperty("run.mode", "unset") == "test"
}