package com.geeksville.dapi.model

import com.geeksville.aws.S3Bucket
import com.geeksville.aws.ConfigCredentials

/**
 * The Nestor glue for talking to S3
 */
object S3Client {
  // Not needed - dapi doesn't allow direct user uploads
  // setRules(createExpireRule("upload-expire", "uploads/", 5))
  val credentials = new ConfigCredentials("dapi")
  val tlogBucket = new S3Bucket("s3-droneapi", false, credentials)

  // Prerendered map tiles
  val mapsBucket = new S3Bucket("maps-droneapi", true, credentials)

  val tlogPrefix = "tlogs/"
}
