package com.geeksville.hull

import io.hull.HullClient
import io.hull.util.HullUtils
import scala.collection.JavaConverters._

object Hull {
    val appId = "***REMOVED***"
    val appSecret = "***REMOVED***"
    val appOrg = "https://***REMOVED***.hullapp.io"

    val client = new HullClient(appId, appSecret, appOrg)

    def generateUserHash(userid: String, email: String) = {

        val map = Map[String, Object]("id" -> userid, "email" -> email)

        val r = HullUtils.generateUserHash(map.asJava, appSecret)
        r
    }
}
