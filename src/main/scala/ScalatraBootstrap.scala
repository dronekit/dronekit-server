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
import com.geeksville.nestor._
import akka.actor.Props
import javax.servlet.ServletContext
import java.io.File
import com.geeksville.dapi.UserController
import com.geeksville.dapi.ResourcesApp
import com.geeksville.dapi.ApiSwagger
import com.geeksville.dapi.MissionController
import com.geeksville.dapi.VehicleController
import com.geeksville.akka.MockAkka
import com.geeksville.akka.TCPListenerActor
import com.geeksville.dapi.TCPGCSActor
import org.scalatra.LifeCycle
import com.geeksville.apiproxy.APIConstants
import com.github.aselab.activerecord.scalatra.ActiveRecordLifeCycle
import com.geeksville.dapi.temp._
import com.geeksville.dapi.Global
import com.geeksville.dapi.auth.SessionsController
import com.geeksville.dapi.AdminController
import com.geeksville.akka.EventStreamDebugger
import com.geeksville.threescale.ThreeActor

class ScalatraBootstrap extends ActiveRecordLifeCycle {
  implicit val swagger = new ApiSwagger

  def system = MockAkka.system

  override def init(context: ServletContext) {

    Global.setConfig()

    super.init(context)

    // start a console so we can browse the H2 database
    // FIXME - do this someplace else, and only in developer mode
    //org.h2.tools.Server.createWebServer("-webPort", "10500").start()

    // Doesn't work yet - for now allow all origins
    //context.initParameters("org.scalatra.cors.allowedOrigins") = "http://www.droneshare.com http://localhost:8080 http://dmn0kpsvjtmio.cloudfront.net nestor-production.herokuapp.com"

    // Don't start old nestor stuff for now
    //context.mount(new DeviceServlet, "/api/*")
    //context.mount(new MainServlet, "/*")

    // Auth controller
    context.mount(new SessionsController, "/api/v1/auth/*")

    // API controllers
    context.mount(new UserController, "/api/v1/user/*")
    context.mount(new VehicleController, "/api/v1/vehicle/*")
    context.mount(new MissionController, "/api/v1/mission/*")

    // Admin operations
    context.mount(new AdminController, "/admin/*")

    // Swagger autodocs
    context.mount(new ResourcesApp, "/api-docs/*")

    // Start up our tcp listener
    val tcpGCSActor = system.actorOf(Props(new TCPListenerActor[TCPGCSActor](APIConstants.DEFAULT_TCP_PORT)), "tcpListener")

    system.actorOf(Props(new EventStreamDebugger), "eventDebug")

    Thread.sleep(2000) // Nasty hack to let TCP actor have time to start running
  }

  /// Make sure you shut down Akka
  override def destroy(context: ServletContext) {
    system.shutdown()
    super.destroy(context)
  }
}

