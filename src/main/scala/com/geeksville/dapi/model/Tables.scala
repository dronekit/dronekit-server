package com.geeksville.dapi.model

import com.github.aselab.activerecord._
import com.github.aselab.activerecord.dsl._
import com.github.aselab.activerecord.scalatra._
import com.jolbox.bonecp.BoneCPConfig
import com.jolbox.bonecp.BoneCP

object Tables extends ActiveRecordTables with ScalatraSupport {
  val migration = table[Migration]
  val vehicles = table[Vehicle]
  val missions = table[Mission]
  val missionSummaries = table[MissionSummary]
  val users = table[User]
  val tokens = table[DBToken]

  override def loadConfig(config: Map[String, Any]) = new DefaultConfig(overrideSettings = config) {
    override def cleanup = {
      super.cleanup

      // Bug in scala active record - they are forgetting to shutdown the connection pool
      pool.shutdown()
    }

    /// We nastily override the pool function so we can customize the bone CP configuration - see kevinh below
    override lazy val pool = {
      try {
        Class.forName(driverClass)
      } catch {
        case e: ClassNotFoundException => throw ActiveRecordException.missingDriver(driverClass)
      }

      val conf = new BoneCPConfig
      conf.setConnectionTimeoutInMs(5 * 60 * 1000L) // The default of 1sec is too short -kevinh
      conf.setJdbcUrl(jdbcurl)
      username.foreach(conf.setUsername)
      password.foreach(conf.setPassword)
      partitionCount.foreach(conf.setPartitionCount)
      maxConnectionsPerPartition.foreach(conf.setMaxConnectionsPerPartition)
      minConnectionsPerPartition.foreach(conf.setMinConnectionsPerPartition)
      new BoneCP(conf)
    }
  }

  override def initialize(config: Map[String, Any]) {
    super.initialize(config)
  }
}