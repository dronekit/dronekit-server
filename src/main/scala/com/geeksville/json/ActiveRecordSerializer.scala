package com.geeksville.json

import org.json4s._
import org.json4s.reflect.Reflector
import com.geeksville.dapi.model.{ User, Vehicle }
import com.github.aselab.activerecord._
import com.github.aselab.activerecord.dsl._
import com.github.aselab.activerecord.aliases._
import squeryl.Implicits._
import java.lang.reflect.InvocationTargetException

class ActiveRecordSerializer(val blacklist: Set[String] = Set.empty) extends Serializer[ActiveRecord] {

  val globalBlacklist = Set("errors")

  private def isBad(name: String) = name.startsWith("_") || name.contains('$') || globalBlacklist.contains(name) || blacklist.contains(name)

  def deserialize(implicit format: Formats) = PartialFunction.empty
  // throw new Exception("Can't deserialize yet")

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
            case ex: InvocationTargetException =>
              throw ex.getCause // Probably better to rethrow the actual failure
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