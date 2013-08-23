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

import org.scalatra.CorsSupport
import org.scalatra.ScalatraBase

/**
 * Configure our cross site scripting support
 * FIXME - does not work.   fails on www.droneshare.com/recent
 */
trait CorsSettings extends CorsSupport { self: ScalatraBase ⇒

  options("/*") {
    Option(request.getHeader("Access-Control-Request-Headers")).foreach(response.setHeader("Access-Control-Allow-Headers", _))
  }

  // FIXME - restrict what sites can use our JS
  // context.initParameters("org.scalatra.cors.allowedOrigins") = "http://example.com:8080 http://foo.example.com"
}
