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
import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
// import com.github.siasia.PluginKeys._
import com.mojolly.scalate.ScalatePlugin._
import ScalateKeys._
import com.bowlingx.sbt.plugins.Wro4jPlugin._
import Wro4jKeys._
import sbtassembly.Plugin._
import AssemblyKeys._ // put this at the top of the file

object NestorBuild extends Build {
  val Organization = "com.geeksville"
  val Name = "nestor"
  val Version = "0.2.0-SNAPSHOT"
  val ScalaVersion = "2.10.0"
  val ScalatraVersion = "2.2.0"

  val assemblyCustomize = mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
    {
      // Pull all of the jansi classes from the offical dist jar, not jline
      case PathList("org", "fusesource", xs @ _*) => MergeStrategy.first
      case PathList("META-INF", "native", xs @ _*) => MergeStrategy.first
      //case "application.conf" => MergeStrategy.concat
      case ".project" => MergeStrategy.discard
      case ".classpath" => MergeStrategy.discard
      case "build.xml" => MergeStrategy.discard
      case "about.html" => MergeStrategy.discard
      case x => old(x)
    }
  }

  lazy val common = Project(id = "gcommon2",
                           base = file("arduleader/common"))

  lazy val nestorProject = Project(
    "nestor",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraWithJRebel ++ assemblySettings ++ scalateSettings ++ wro4jSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      assemblyCustomize,
      resolvers += "Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
      resolvers += "Maven snapshots" at "http://download.java.net/maven/2",

      // To include source for Takipi
      unmanagedResourceDirectories in Compile <+= baseDirectory( _ / "src" ),

      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-scalate" % ScalatraVersion,
        // "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.9" % "runtime",
        "com.novus" %% "salat" % "1.9.5-SNAPSHOT",
        "de.micromata.jak" % "JavaAPIforKml" % "2.2.0-SNAPSHOT",
        "com.amazonaws" % "aws-java-sdk" % "1.3.10",
        "com.google.code.findbugs" % "jsr305" % "2.0.1",
        "com.google.guava" % "guava" % "14.0-rc2",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "compile;container",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "compile;container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))),
        //"org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "container",
        //"org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))),

/* no longer works?/needed?
      warPostProcess in Compile <<= (target) map {
        (target) => { 
          () =>
          val webapp = target / "webapp"
	  val libs = webapp / "WEB-INF" / "lib"
          val notWar = Seq("javax.servlet-3.0.0.v201112011016.jar", "jetty-webapp-8.1.8.v20121106.jar", 
	    "jetty-server-8.1.8.v20121106.jar", "jetty-servlet-8.1.8.v20121106.jar")
          notWar.foreach { f =>
	  IO.delete(libs / f ) }
        }
      },
*/
      scalateTemplateConfig in Compile <<= (sourceDirectory in Compile) { base =>
        Seq(
          TemplateConfig(
            base / "webapp" / "WEB-INF" / "templates",
            Seq.empty, /* default imports should be added here */
            Seq.empty, /* add extra bindings here */
            Some("templates")))
      }
      // busted? needed? 
	// webappResources in Compile <+= (targetFolder in generateResources in Compile)
      )) dependsOn(common)
}
