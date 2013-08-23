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
package com.geeksville.util

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.cache.Cache

object CacheUtil {
  implicit def functionToCacheLoader[F, T](f: F => T) = {
    new CacheLoader[F, T] {
      def load(key: F) = f(key)
    }
  }

  implicit def pimpCache[F, T](cache: Cache[F, T]) = {
    new PimpedCache(cache)
  }

  class PimpedCache[F, T](cache: Cache[F, T]) {
    def getOption(key: F) = {
      val value = cache.getIfPresent(key)
      if (value == null) None else Some(value)
    }
  }
}
