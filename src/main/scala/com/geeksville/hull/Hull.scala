package com.geeksville.hull

import com.auth0.jwt.JWTSigner
import io.hull.HullClient
import io.hull.util.HullUtils
import scala.collection.JavaConverters._

object Hull {
    val appId = "***REMOVED***"
    val appSecret = "***REMOVED***"
    val appOrg = "https://***REMOVED***.hullapp.io"

    val client = new HullClient(appId, appSecret, appOrg)

  /**
   * This generates the _deprecated_ hull hash code
   * @param userid
   * @param email
   * @return
   */
    def generateUserHash(userid: String, email: String) = {

        val map = Map[String, Object]("id" -> userid, "email" -> email)

        val r = HullUtils.generateUserHash(map.asJava, appSecret)
        r
    }

  def generateAccessToken(userid: String) = {

    val claims = Map[String, Object]("iat" -> (System.currentTimeMillis / 1000L).asInstanceOf[Object], "iss" -> appId, "sub" -> userid)

    val signer = new JWTSigner(appSecret)
    val r = signer.sign(claims.asJava)

    r
  }

}
