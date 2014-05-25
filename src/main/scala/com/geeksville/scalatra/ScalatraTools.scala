package com.geeksville.scalatra

object ScalatraTools {
  lazy val isTesting = System.getProperty("run.mode", "unset") == "test"
}