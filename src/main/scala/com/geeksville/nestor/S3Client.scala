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
package com.geeksville.nestor

import com.geeksville.aws.S3Bucket

/**
 * The Nestor glue for talking to S3
 */
object S3Client extends S3Bucket("s3-droneshare") {
  setRules(createExpireRule("upload-expire", "uploads/", 5))
}
