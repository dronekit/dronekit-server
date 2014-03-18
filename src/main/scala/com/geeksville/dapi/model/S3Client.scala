package com.geeksville.dapi.model

import com.geeksville.aws.S3Bucket

/**
 * The Nestor glue for talking to S3
 */
object S3Client extends S3Bucket("s3-dapi") {
  // Not needed - dapi doesn't allow direct user uploads
  // setRules(createExpireRule("upload-expire", "uploads/", 5))
  
    val tlogPrefix = "tlogs/"
}
