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

trait DapiRecordCompanion[T <: ActiveRecord] extends ActiveRecordCompanion[T] {
}
