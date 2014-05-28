package com.geeksville.dapi.model

import com.geeksville.aws.S3Bucket
import com.geeksville.aws.ConfigCredentials
import com.amazonaws.ClientConfiguration
import com.amazonaws.services.s3.AmazonS3Client

/**
 * The droneshare glue for talking to S3
 */
object S3Client {
  // Not needed - dapi doesn't allow direct user uploads
  // setRules(createExpireRule("upload-expire", "uploads/", 5))
  val credentials = new ConfigCredentials("dapi")

  val config = new ClientConfiguration()
  config.setSocketTimeout(30 * 1000)
  val client = new AmazonS3Client(credentials, config)

  val tlogBucket = new S3Bucket("s3-droneapi", false, client)

  // Prerendered map tiles
  val mapsBucket = new S3Bucket("maps-droneapi", true, client)

  val tlogPrefix = "tlogs/"
}
