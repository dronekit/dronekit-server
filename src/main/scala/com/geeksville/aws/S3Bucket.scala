/**
 * *****************************************************************************
 * Copyright 2013 Kevin Hester
 *
 * See LICENSE.txt for license details.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.geeksville.aws

import com.amazonaws.services.s3._
import com.amazonaws.services.s3.model._
import com.amazonaws._
import java.io.InputStream
import com.amazonaws.auth.PropertiesCredentials
import sun.misc.BASE64Encoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.collection.JavaConverters._
import java.util.{Date, TimeZone}
import java.text.SimpleDateFormat
import com.amazonaws.auth.AWSCredentials
import grizzled.slf4j.Logging
import com.geeksville.util.Using._

class S3Bucket(val bucketName: String, val isReadable: Boolean, val client: AmazonS3Client) extends Logging {

  // At startup make sure our bucket exists
  createBucket()

  override def toString = s"S3Bucket:$bucketName"

  /// Return a URL that can be use for reading a file (FIXME, make secure)
  def getReadURL(objKey: String) =
    s"http://$bucketName.s3.amazonaws.com/$objKey"

  def createBucket() {
    // FIXME, support creating buckets in different regions
    info(s"Creating AWS bucket : $bucketName (readable=$isReadable)")
    client.createBucket(bucketName)
    if (isReadable)
      makeReadable()
  }

  def createExpireRule(id: String, prefix: String, days: Int) = new BucketLifecycleConfiguration.Rule()
    .withId(id)
    .withPrefix(prefix)
    .withExpirationInDays(days)
    .withStatus(BucketLifecycleConfiguration.ENABLED.toString())

  def setRules(rules: BucketLifecycleConfiguration.Rule*) {
    val configuration = new BucketLifecycleConfiguration().withRules(rules.asJava)

    // Save configuration.
    client.setBucketLifecycleConfiguration(bucketName, configuration);
  }

  def makeReadable() {
    // Make bucket readable by default
    // FIXME the following is very insecure - need to instead use cloudfront private keys
    // http://docs.amazonwebservices.com/AmazonS3/latest/API/RESTObjectPUT.html
    val policy = """{
      "Version":"2008-10-17",
      "Statement":[{
        "Sid":"AllowPublicRead",
            "Effect":"Allow",
          "Principal": {
                "AWS": "*"
             },
          "Action":["s3:GetObject"],
          "Resource":["arn:aws:s3:::%s/*"
          ]
        }
      ]
    }""".format(bucketName)
    client.setBucketPolicy(bucketName, policy)
  }

  def plusOneHour = {
    val expiration = new java.util.Date
    // Add 1 hour.
    expiration.setTime(expiration.getTime + 1000 * 60 * 60)
    expiration
  }

  /// Generate a URL which is good for limited time upload permissions
  /// FIXME - better to use cloudfront for reading
  /// http://docs.amazonwebservices.com/AmazonCloudFront/latest/DeveloperGuide/HowToPrivateContent.html
  def createPresignedURLRequest(objKey: String, mimeType: String) = {
    val expiration = plusOneHour

    val urlReq = new GeneratePresignedUrlRequest(bucketName, objKey)
      .withMethod(HttpMethod.PUT)
      .withBucketName(bucketName)
      .withKey(objKey)
      .withExpiration(expiration)

    // urlReq.addRequestParameter("Content-Type", mimeType)
    urlReq.addRequestParameter("x-amz-storage-class", "REDUCED_REDUNDANCY")

    // Does not seem to work
    // urlReq.addRequestParameter("x-amz-acl", "public-read")

    urlReq
  }

  /**
   * Upload a file to S3
   * It is import that callers NOT close the input stream.  AWS will handle that.
   */
  def uploadStream(key: String, stream: InputStream, mimeType: String, length: Long, highValue: Boolean = true) = {
    debug(s"Uploading to S3 (readable=$isReadable).  Read URL is: " + getReadURL(key))
    val metadata = new ObjectMetadata()
    metadata.setContentType(mimeType)
    metadata.setContentLength(length)

    var req = new PutObjectRequest(bucketName, key, stream, metadata)

    if (!highValue)
      req = req.withStorageClass("REDUCED_REDUNDANCY")

    client.putObject(req)
  }

  /**
   * Read info about an S3 file including metadata and the ability to read payload as an input stream
   * @param range an optional range of bytes to restrict to reading
   */
  def getObject(key: String, range: Option[Pair[Long, Long]] = None) = {
    val req = new GetObjectRequest(bucketName, key)

    range.foreach { case (start, end) => req.setRange(start, end)}

    client.getObject(req)
  }

  /**
   * Read from a S3 file
   * You MUST close the returned InputStream, otherwise connections will leak.
   * @param range an optional range of bytes to restrict to reading
   */
  def downloadStream(key: String, range: Option[Pair[Long, Long]] = None) =
    getObject(key, range).getObjectContent()

  private def listByMarker(keyPrefix: String, marker: String) = {
    debug("listByMarker " + Option(marker))

    val req = new ListObjectsRequest()
    if(marker != null)
      req.setMarker(marker)
    req.setBucketName(bucketName)
    req.setMaxKeys(Integer.MAX_VALUE)

    if (!keyPrefix.isEmpty)
      req.setPrefix(keyPrefix)

    val obj = client.listObjects(req)
    obj
  }

  /**
   * List objects in this bucket (FIXME, add support for delimeters to allow hierarchical results)
   * @param keyPrefix
   */
  def listObjects(keyPrefix: String = "") = {
    var done: Boolean = false
    var marker: String = null

    // Lazily do a series of reads
    val  subReads = Stream.continually {
      if(!done) {
        val obj = listByMarker(keyPrefix, marker)
        marker = obj.getNextMarker
        done = obj.getNextMarker == null
        obj.getObjectSummaries.asScala
      }
      else
        Seq.empty
    }.takeWhile { recs =>
      !recs.isEmpty
    }.flatten

    subReads.view
  }

  /**
   * Copy a file in S3 from one bucket to another (entirely inside of the AWS infrastructure - no download)
   *
   */
  def copyTo(destBucket: S3Bucket, srcKey: String, destKey: String) = {
    val req = new CopyObjectRequest(bucketName, srcKey, destBucket.bucketName, destKey)
    val obj = client.copyObject(req)
    obj
  }

  /**
   * Copy all files in this bucket to some other bucket
   * @param destBucket
   */
  def backupTo(destBucket: S3Bucket, newerThan: Option[Date] = None) {
    info(s"Starting backup from $this to $destBucket")
    val files = listObjects()

    val toBackup = files.filter { f =>
      newerThan match {
        case Some(n) =>
          !f.getLastModified.before(n)
        case None =>
          true
      }
    }
    val numToBackup = toBackup.size
    info(s"Found ${files.size} files, but we only need to backup $numToBackup")

    toBackup.zipWithIndex.foreach { case (f, i) =>
      val key = f.getKey
      info(s"Backing up $i/$numToBackup: $key")

      val copyAsFile = false
      if (copyAsFile) {
        val srcObj = getObject(key)
        val metadata = srcObj.getObjectMetadata

        val srcFile = srcObj.getObjectContent()

        // The srcFile will be closed by uploadStream
        destBucket.uploadStream(key, srcFile, metadata.getContentType, metadata.getContentLength)
      }
      else {
        copyTo(destBucket, key, key)
      }
    }

    info("Done with backup")
  }

  /// Generate a URL which is good for limited time upload permissions
  /// FIXME - better to use cloudfront for reading
  /// http://docs.amazonwebservices.com/AmazonCloudFront/latest/DeveloperGuide/HowToPrivateContent.html
  def generatePresignedUpload(objKey: String, mimeType: String) = {
    println("Requesting S3 presign %s/%s/%s".format(bucketName, objKey, mimeType))
    val urlReq = createPresignedURLRequest(objKey, mimeType)
    println("S3 presigned request: " + urlReq)
    val u = client.generatePresignedUrl(urlReq)
    println("Generated presigned upload: " + u)
    u
  }

  def copyObject(srcKey: String, destKey: String) = client.copyObject(bucketName, srcKey, bucketName, destKey)

  /**
   * Generate policy -> signature in a form acceptable for HTTP browser upload to S3
   */
  def s3Policy(credentials: AWSCredentials) = {

    // |{"success_action_redirect": "http://localhost/"},
    // |{"expiration": "%s", - not needed because we have an expire rule that covers the entire uploads folder
    val policyJson = """
                       |{"expiration": "2015-01-01T00:00:00Z",
                       	 |"conditions": [
                       	 |{"bucket": "%s"},
                       	    |["starts-with", "$key", "uploads/"],
                       	    |{"acl": "private"},
                       	    |["starts-with", "$Content-Type", ""],
                       	    |["content-length-range", 0, 50048576]
                       	    |]
                       	 |}""".stripMargin.format(bucketName)

    val policy = (new BASE64Encoder()).encode(policyJson.getBytes("UTF-8")).replaceAll("\n", "").replaceAll("\r", "")

    val awsSecretKey = credentials.getAWSSecretKey

    val hmac = Mac.getInstance("HmacSHA1")
    hmac.init(new SecretKeySpec(
      awsSecretKey.getBytes("UTF-8"), "HmacSHA1"));
    val signature = (new BASE64Encoder()).encode(
      hmac.doFinal(policy.getBytes("UTF-8")))
      .replaceAll("\n", "");

    policy -> signature
  }
}
