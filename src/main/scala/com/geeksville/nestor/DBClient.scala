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

import com.mongodb.casbah.MongoConnection
import com.mongodb.casbah.MongoURI
import com.mongodb.casbah.WriteConcern
import org.scalatra.CoreDsl
import com.typesafe.config.ConfigFactory

/**
 * A mixin that opens a DB connection for each servlet
 */
object DBClient {

  private val conf = ConfigFactory.load()

  private val username = conf.getString("db.user")
  private val password = conf.getString("db.password")
  private val dbname = conf.getString("db.dbname")

  private val uri = MongoURI(conf.getString("db.uri"))

  lazy val db = {
    val db = MongoConnection(uri)(dbname)
    val success = db.authenticate(username, password)
    if (!success) {
      throw new Exception("DB auth failed: " + db.getLastError)
    }
    println("Did authenticate")
    db.setWriteConcern(WriteConcern.Safe) // Slow but cautious 

    db
  }

  // val coll = db("msgs")

}
