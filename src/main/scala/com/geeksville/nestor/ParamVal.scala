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

/// A mavlink value, scaled for plotting in javascropt
case class ParamVal(name: String, v: Double)

object ParamVal {

  private def accept(d: Double) = Some(d)
  private def deny(d: Double) = None

  val converters = Map[String, Double => Option[Double]](
    "GLOBAL_POSITION_INT.alt" -> { d => Some(d / 1000) },
    "VFR_HUD.groundspeed" -> accept,
    "VFR_HUD.airspeed" -> accept,
    "VFR_HUD.throttle" -> accept /*
    "GLOBAL_POSITION_INT.hdg" -> { d => Some(d / 100) },
    "GLOBAL_POSITION_INT.lat" -> { d => Some(d / 1e7) },
    "GLOBAL_POSITION_INT.lon" -> { d => Some(d / 1e7) }
    */ )

  def perhapsCreate(n: String, v: String) = {
    try {
      val d = v.toDouble

      val f = converters.getOrElse(n, deny _) // If not listed, for now we let it through
      f(d).map(ParamVal(n, _))
    } catch {
      case ex: NumberFormatException =>
        None // Must have been a string - we don't do those yet
    }
  }
}
