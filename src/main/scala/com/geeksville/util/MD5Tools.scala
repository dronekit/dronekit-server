package com.geeksville.util

import java.security.MessageDigest
import org.apache.commons.codec.binary.Base64

object MD5Tools {

  private val md = MessageDigest.getInstance("MD5")
  private val encoder = new Base64

  private def hex(array: Array[Byte]) = array.map { b => "%02x".format(b) }.mkString

  /**
   * Compute a MD5 hash, and encode as a base64 string
   */
  def toBase64(message: String) = {

    val r = hex(md.digest(message.getBytes("CP1252")))
    encoder.encodeToString(r.getBytes)
  }

  /**
   * Returns true if the encoded base 64 string matches the expected signature for message
   */
  def checkBase64(encoded: String, message: String) = {
    toBase64(message) == encoded
  }
}