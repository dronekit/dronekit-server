package com.geeksville.dapi.model

import com.github.aselab.activerecord.ActiveRecord
import com.github.aselab.activerecord.ActiveRecordCompanion
import com.github.aselab.activerecord.annotations._
import org.squeryl.annotations.Transient
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import com.github.aselab.activerecord.dsl._
import grizzled.slf4j.Logging
import com.github.aselab.activerecord.Timestamps

/**
 * Behavior common to all Dapi records
 */
abstract class DapiRecord extends ActiveRecord with Timestamps {
  /// To support the perhapsReread method, we use a timestamp indicating when we were read/created
  @Transient
  val readAt = System.currentTimeMillis
}

/**
 * Standard CRUD operations to support ApiController (normally through ActiveRecord but could be implemented with a different provider)
 */
trait CRUDOperations[T] {
  /// Assume that the key is a long SQL primary id, subclasses can override if they want different behavior
  def find(id: String): Option[T]
}

trait DapiRecordCompanion[T <: DapiRecord] extends ActiveRecordCompanion[T] with CRUDOperations[T] with Logging {

  type Relation = ActiveRecord.Relation[T, T]

  /// Assume that the key is a long SQL primary id, subclasses can override if they want different behavior
  def find(id: String): Option[T] = try {
    collection.where(_.id === id.toLong).headOption
  } catch {
    case ex: NumberFormatException =>
      warn(s"Can't convert $id to an integer id")
      None
  }

  /// Allows us to reread from the master database occasionally (in case some other thread has updated this record)
  /// Note, returns an option because if some other thread has deleted the record we want to know that too
  /// @param recOpt is passed as an option because it allows clients to idomatically say Mission.perhapsReread(someOpt) - if
  /// the option is None this call is a no-op
  def perhapsReread(recOpt: Option[T], expireMsec: Long = 60 * 1000L): Option[T] = {
    recOpt.flatMap { rec =>
      if (rec.readAt + expireMsec < System.currentTimeMillis)
        // Expired
        find(rec.id)
      else
        Some(rec)
    }
  }

  /**
   * Return a top level view of this collection (subclasses might change this method to make it prefetch associated tables (see Mission)
   */
  def collection: Relation = this.companionToRelation(this)

}
