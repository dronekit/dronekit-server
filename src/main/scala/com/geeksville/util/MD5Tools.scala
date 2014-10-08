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
    encoded == message
  }
}

/**
 * Generates/extracts an encoded version of a string, verifying that the secret magic # is correct.
 *
 * Eventually we could make this factory even smarter.
 */
class EnvelopeFactory(private val magic: String) {
  def encode(message: String) = MD5Tools.toBase64(magic + message)

  // Was the hashed value correct for the specified expected message
  def isValid(expectedMessage: String, receivedMessage: String) = MD5Tools.checkBase64(receivedMessage, magic + expectedMessage)
}