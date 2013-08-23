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

import org.scalatra.test.specs2._

// For more on Specs2, see http://etorreborre.github.com/specs2/guide/org.specs2.guide.QuickStart.html
class MainServletSpec extends ScalatraSpec { def is =
  "GET / on MainServlet"                     ^
    "should return status 200"                  ! root200^
                                                end

  addServlet(classOf[MainServlet], "/*")

  def root200 = get("/") {
    status must_== 200
  }
}
