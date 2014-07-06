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

import com.amazonaws.auth.AWSCredentials
import com.typesafe.config.ConfigFactory
import grizzled.slf4j.Logging

/**
 * Provides AWS credentials from a Typesafe style config db
 *
 * @param baseKey - we look for AWS keys underneed this root entry
 */
class ConfigCredentials(baseKey: String) extends AWSCredentials with Logging {
  private lazy val conf = ConfigFactory.load()
  private def prefix = if (baseKey.isEmpty) baseKey else baseKey + "."

  def getAWSAccessKeyId() = {
    val r = conf.getString(prefix + "aws.accessKey")
    //debug(s"Using AWS access key $r")
    r
  }
  def getAWSSecretKey() = {
    val r = conf.getString(prefix + "aws.secretKey")
    //debug(s"Using AWS secret key $r")
    r
  }
}
