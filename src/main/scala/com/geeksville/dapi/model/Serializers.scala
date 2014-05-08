package com.geeksville.dapi.model
import org.json4s.CustomSerializer
import com.google.protobuf.Internal.EnumLite
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JValue

/**
 * Many enum types are based on this (like AccessCode)
 */
private object EnumSerializer extends CustomSerializer[EnumLite](implicit format => (
  {
    case JString(v) =>
      throw new Exception("not yet implemented")
  },
  {
    case u: EnumLite =>
      JString(u.toString)
  }))

object `package` {

  /// We have custom JSON converters for key objects
  val DroneModelFormats = Seq(new UserSerializer(None), MissionSerializer, VehicleSerializer, EnumSerializer)
}