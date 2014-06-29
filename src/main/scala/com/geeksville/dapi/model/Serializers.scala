package com.geeksville.dapi.model
import org.json4s.CustomSerializer
import com.google.protobuf.Internal.EnumLite
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JValue
import com.geeksville.json.EnumSerializer
import com.geeksville.dapi.AccessCode

/**
 * Many enum types are based on this (like AccessCode)
 */
private object SimpleEnumSerializer extends CustomSerializer[EnumLite](implicit format => (
  {
    case JString(v) =>
      println(s"Trying to convert $v")
      throw new Exception("not yet implemented")
  },
  {
    case u: EnumLite =>
      JString(u.toString)
  }))

object `package` {

  /// We have custom JSON converters for key objects
  // new UserSerializer(None), 
  // new VehicleSerializer()
  val DroneModelFormats = Seq(new MissionSerializer(false), EnumSerializer.create(AccessCode))
}