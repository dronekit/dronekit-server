package com.geeksville.dapi

import com.github.aselab.activerecord.ActiveRecord
import org.scalatra.swagger.Swagger
import com.geeksville.dapi.model.CRUDOperations
import org.json4s.Formats
import com.geeksville.json.ActiveRecordSerializer
import com.github.aselab.activerecord.dsl._
import com.geeksville.dapi.model.DapiRecordCompanion
import com.github.aselab.activerecord.ActiveRecordException

/**
 * A controller that assumes the backing object comes from ActiveRecord (allows easy field finding)
 */
class ActiveRecordController[T <: ActiveRecord: Manifest](aName: String, swagger: Swagger, protected val myCompanion: DapiRecordCompanion[T])
  extends ApiController[T](aName, swagger, myCompanion) {

  /// Fields we never want to share with clients
  /// FIXME - add annotations for this?
  def blacklist = Set[String]()

  private val findParamOp =
    (apiOperation[T]("getParam")
      summary "Get a parameter from an object"
      parameters (
        pathParam[String]("id").description(s"Id of $aName that needs to be fetched"),
        pathParam[String]("param").description(s"The parameter to read from the object")))

  get("/:id/:param", operation(findParamOp)) {
    // use for comprehension to stack up all the possibly missing values
    (for {
      param <- params.get("param")
      pval <- if (blacklist.contains(param)) None else findById.toMap.get(param)
    } yield {
      pval
    }).getOrElse(haltNotFound("object or parameter not found"))
  }

  /**
   * Using the current query parameters, return all matching records (paging and ordering is supported as well
   */
  final override protected def getAll(): List[T] = {
    try {
      var r = getFiltered

      // Apply ordering
      for {
        orderOn <- params.get("order_by")
      } yield {
        val dir = params.get("order_dir").getOrElse("asc")

        r = r.orderBy(orderOn, dir)
      }

      // Apply paging restriction
      for {
        pStr <- params.get("page_size")
      } yield {
        val offset = params.get("page_offset").getOrElse("0").toInt
        r = r.page(offset, pStr.toInt)
      }

      r.toList
    } catch {
      case ex: ActiveRecordException =>
        haltBadRequest(ex.getMessage)
    }
  }

  /**
   * Subclasses should override to 'chain' any parameter based filters.
   * This baseclass provides support for paging and ordering
   */
  protected def getFiltered() = {
    myCompanion.collection
  }
}