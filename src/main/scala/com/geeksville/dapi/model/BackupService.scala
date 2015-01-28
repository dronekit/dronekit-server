package com.geeksville.dapi.model

import com.geeksville.dapi.Global

/**
 * Created by kevinh on 1/28/15.
 */
object BackupService {

  /**
   * Allow running backups from the commandline with
   * sbt runMain com.geeksville.dapi.model.BackupService
   *
   * @param args
   */
  def main(args: Array[String]): Unit = {
    val lastBackup = None

    Global.setConfig()
    S3Client.doBackup(lastBackup)
  }
}
