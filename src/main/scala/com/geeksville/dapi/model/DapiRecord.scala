package com.geeksville.dapi.model

import com.github.aselab.activerecord.ActiveRecord
import com.github.aselab.activerecord.ActiveRecordCompanion
import com.github.aselab.activerecord.Datestamps
import com.github.aselab.activerecord.annotations._
import org.squeryl.annotations.Transient
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import com.github.aselab.activerecord.dsl._

/**
 * Behavior common to all Dapi records
 */
abstract class DapiRecord extends ActiveRecord with Datestamps

/**
 * Standard CRUD operations to support ApiController (normally through ActiveRecord but could be implemented with a different provider)
 */
trait CRUDOperations[T] {
  /// Assume that the key is a long SQL primary id, subclasses can override if they want different behavior
  def find(id: String): Option[T]
}

trait DapiRecordCompanion[T <: ActiveRecord] extends ActiveRecordCompanion[T] with CRUDOperations[T] {
  /// Assume that the key is a long SQL primary id, subclasses can override if they want different behavior
  def find(id: String): Option[T] = collection.where(_.id === id.toLong).headOption

  /**
   * Return a top level view of this collection (subclasses might change this method to make it prefetch associated tables (see Mission)
   */
  def collection: ActiveRecord.Relation1[T, T] = this.companionToRelation(this)
}
