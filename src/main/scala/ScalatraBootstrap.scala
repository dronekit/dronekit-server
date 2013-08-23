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
import com.geeksville.nestor._
import org.scalatra._
import javax.servlet.ServletContext
import java.io.File

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {

    val configOverride = new File(System.getProperty("user.home") + "/nestor.conf")
    if (configOverride.exists)
      System.setProperty("config.file", configOverride.toString)
    else
      println(s"No config override file found.  You should probably create $configOverride")

    // Doesn't work yet - for now allow all origins
    //context.initParameters("org.scalatra.cors.allowedOrigins") = "http://www.droneshare.com http://localhost:8080 http://dmn0kpsvjtmio.cloudfront.net nestor-production.herokuapp.com"

    context.mount(new DeviceServlet, "/api/*")
    context.mount(new MainServlet, "/*")
  }
}
