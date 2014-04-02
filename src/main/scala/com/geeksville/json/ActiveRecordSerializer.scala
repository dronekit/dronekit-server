package com.geeksville.json

import org.json4s.FieldSerializer

/**
 * A serializer that is smart enough to ignore various bits of private ActiveRecord state...
 */
object ActiveRecordSerializer {

  /// Construct our serializer
  def apply[T: Manifest](blacklist: Set[String] = Set.empty) = FieldSerializer[T](serializer(blacklist), Map())

  private def serializer(blacklist: Set[String]): PartialFunction[(String, Any), Option[(String, Any)]] = {
    case (name, _) if name.startsWith("_") || name.contains('$') || blacklist.contains(name) => None
  }

  /* no need for a deserializer yet 
  case class FieldSerializer[A](
    serializer: PartialFunction[(String, Any), Option[(String, Any)]] = Map(),
    deserializer: PartialFunction[JField, JField] = Map())(implicit val mf: Manifest[A])
  */
}