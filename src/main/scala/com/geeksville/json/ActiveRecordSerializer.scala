package com.geeksville.json

import org.json4s._
import org.json4s.reflect.Reflector
import com.geeksville.dapi.model.{ User, Vehicle }
import com.github.aselab.activerecord._
import com.github.aselab.activerecord.dsl._
import com.github.aselab.activerecord.aliases._
import squeryl.Implicits._

/**
 * A serializer that is smart enough to ignore various bits of private ActiveRecord state...
 */
object ActiveRecordSerializer {

  val globalBlacklist = Set("errors")

  /// Construct our serializer
  def apply[T: Manifest](blacklist: Set[String] = Set.empty) = FieldSerializer[T](serializer(blacklist), Map())

  private def serializer(blacklist: Set[String]): PartialFunction[(String, Any), Option[(String, Any)]] = {
    case (name, x) => println(s"Being asked to serialize $name $x (${if (x != null) x.getClass else "null"})"); Some(name, x)
    // case (name, _) if name.startsWith("_") || name.contains('$') || globalBlacklist.contains(name) || blacklist.contains(name) => None
  }

  /* no need for a deserializer yet 
  case class FieldSerializer[A](
    serializer: PartialFunction[(String, Any), Option[(String, Any)]] = Map(),
    deserializer: PartialFunction[JField, JField] = Map())(implicit val mf: Manifest[A])
  */
}

// FIXME - this can't work - not enough state with only the association
class CollectionAssociationSerializer[O <: ActiveRecord, T <: ActiveRecord] extends Serializer[ActiveRecord.CollectionAssociation[O, T]] {
  def deserialize(implicit format: Formats) = throw new Exception("Can't deserialize yet")
  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: ActiveRecord.CollectionAssociation[O, T] =>
      println(s"Considering hasmany $x")
      JString("fish")
  }
}

class ActiveRecordSerializer2(val blacklist: Set[String] = Set.empty) extends Serializer[ActiveRecord] {

  val globalBlacklist = Set("errors")

  private def isBad(name: String) = name.startsWith("_") || name.contains('$') || globalBlacklist.contains(name) || blacklist.contains(name)

  def deserialize(implicit format: Formats) = throw new Exception("Can't deserialize yet")

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: ActiveRecord =>

      val jclass = x.getClass
      val klass = Reflector.scalaTypeOf(jclass)
      val descriptor = Reflector.describe(klass).asInstanceOf[reflect.ClassDescriptor]
      // val ctorParams = descriptor.mostComprehensive.map(_.name)
      val props = descriptor.properties.iterator

      val fields = props.filter { p => !isBad(p.name) }.map { p =>
        val name = p.name
        var v: Any = p.get(x)

        // This hack is necessary because json4s doesn't properly handle lazy vals - I've asked them if they want me to
        // put the correct fix in: https://github.com/json4s/json4s/issues/111
        if (v == null)
          try {
            val m = jclass.getMethod(name)
            // Call the getter to ensure it is valid
            v = m.invoke(x)
          } catch {
            case ex: NoSuchMethodException =>
            // No getter
          }

        //println(s"Encoding $name as $v ${p.returnType}")

        if (v != null)
          v match {
            // Nasty hack to prevent infinite recursion - we show collections as lists of ints
            case x: ActiveRecord.CollectionAssociation[_, ActiveRecord] =>
              v = x.toList.map(_.id)

            case x: ActiveRecord.BelongsToAssociation[_, ActiveRecord] =>
              v = x.headOption.map(_.id) // Also prevent recursion in the upward direction - just show the int id instead of the object

            case x: ActiveRecord.SingularAssociation[_, ActiveRecord] =>
              v = x.getOrElse(null)

            case _ =>
            // Don't change
          }

        JField(name, Extraction.decompose(v))
      }.toList
      JObject(fields)
  }
}