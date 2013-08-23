/*******************************************************************************
 * Copyright 2013 Kevin Hester
 * 
 * See LICENSE.txt for license details.
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.geeksville.aws

import com.amazonaws.auth.AWSCredentials
import com.typesafe.config.ConfigFactory

/**
 * Provides AWS credentials from a Typesafe style config db
 */
class ConfigCredentials extends AWSCredentials {
  private lazy val conf = ConfigFactory.load()

  def getAWSAccessKeyId() = conf.getString("aws.accessKey")
  def getAWSSecretKey() = conf.getString("aws.secretKey")
}
