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
import com.geeksville.dapi.Global
import com.geeksville.dapi.auth.SessionsController
import com.geeksville.dapi.AdminController
import com.geeksville.akka.EventStreamDebugger
import com.geeksville.threescale.ThreeActor
import org.scalatra.atmosphere.ActorSystemKey
import com.geeksville.dapi.model.Migration
import com.geeksville.akka.ZMQGateway
import com.geeksville.dapi.ZMQGCSActor
import com.geeksville.dapi.test.SimGCSClient
import com.geeksville.akka.AkkaTools
import scala.util.Success
import scala.util.Failure
import grizzled.slf4j.Logging
import com.geeksville.dapi.NewRelicAnalyticsAdapter
import akka.actor.ActorRef
import com.geeksville.dapi.RedirectController

class ScalatraBootstrap extends ActiveRecordLifeCycle {
  implicit val swagger = new ApiSwagger

  lazy val system = MockAkka.system

  override def init(context: ServletContext) {

    NewRelicAnalyticsAdapter.install()

    Global.setConfig()

    super.init(context)

    // Let atmosphere find akka here...
    context(ActorSystemKey) = system

    Migration.update()

    // start a console so we can browse the H2 database
    // FIXME - do this someplace else, and only in developer mode
    //org.h2.tools.Server.createWebServer("-webPort", "10500").start()

    // Doesn't work yet - for now allow all origins
    //context.initParameters("org.scalatra.cors.allowedOrigins") = "http://www.droneshare.com http://localhost:8080 http://dmn0kpsvjtmio.cloudfront.net nestor-production.herokuapp.com"
    context.initParameters("org.scalatra.cors.allowCredentials") = "true"

    // Don't start old nestor stuff for now
    //context.mount(new DeviceServlet, "/api/*")
    // No longer needed - we now use nginix for this hack
    // context.mount(new RedirectController, "/*")

    // Auth controller
    context.mount(new SessionsController, "/api/v1/auth/*")

    // API controllers
    context.mount(new UserController, "/api/v1/user/*")
    context.mount(new VehicleController, "/api/v1/vehicle/*")
    context.mount(new MissionController, "/api/v1/mission/*")

    // Admin operations
    context.mount(new AdminController, "/api/v1/admin/*")

    // Swagger autodocs
    context.mount(new ResourcesApp, "/api-docs/*")

    // Start up our tcp listener
    val tcpGCSActor = system.actorOf(Props(new TCPListenerActor[TCPGCSActor](APIConstants.DEFAULT_TCP_PORT)), "tcpListener")

    val zmqWorkerFactory = Props[ZMQGCSActor]
    val zmqGateway = system.actorOf(Props(new ZMQGateway(zmqWorkerFactory)), "zmqGateway")

    system.actorOf(Props(new EventStreamDebugger), "eventDebug")

    // startSimVehicles(tcpGCSActor)
  }

  private def startSimVehicles(tcpGCSActor: ActorRef) {
    import system._
    // Start up a few sim vehicles that are always running
    AkkaTools.waitAlive(tcpGCSActor) onComplete {
      case Failure(t) =>
        throw t

      case Success(_) =>
        Global.simGCSClient ! SimGCSClient.RunTest(1, 24 * 60 * 60 * 30)
    }
  }

  /// Make sure you shut down Akka
  override def destroy(context: ServletContext) {
    system.shutdown()
    super.destroy(context)
  }
}
