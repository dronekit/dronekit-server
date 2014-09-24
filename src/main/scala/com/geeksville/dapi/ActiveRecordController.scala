package com.geeksville.dapi

import com.github.aselab.activerecord.ActiveRecord
import org.scalatra.swagger.Swagger
import com.geeksville.dapi.model.CRUDOperations
import org.json4s.Formats
import com.geeksville.json.ActiveRecordSerializer
import com.github.aselab.activerecord.dsl._
import com.geeksville.dapi.model.DapiRecordCompanion
import com.github.aselab.activerecord.ActiveRecordException
import org.json4s.JsonAST.JValue
import com.geeksville.dapi.model.DapiRecord

/**
 * A controller that assumes the backing object comes from ActiveRecord (allows easy field finding)
 */
class ActiveRecordController[T <: DapiRecord: Manifest, JsonT <: Product: Manifest](aName: String, swagger: Swagger, protected val myCompanion: DapiRecordCompanion[T])
  extends ApiController[T, JsonT](aName, swagger, myCompanion) {

  /// Fields we never want to share with clients
  /// FIXME - add annotations for this?
  def blacklist = Set[String]()

  private val findParamOp =
    (apiOperation[JValue]("getParam") // FIXME - this is not correct - the type should depend on the type of the a param itself
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

  /// Subclasses can override if they want to make finding fields smarter
  protected def applyFilterExpressions(r: myCompanion.Relation, whereExp: Seq[LogicalBoolean]) = {
    val eqFilters = whereExp.map { l => (l.colName, l.cmpValue) }
    if (!eqFilters.isEmpty)
      r.findAllBy(eqFilters.head, eqFilters.tail: _*)
    else
      r
  }

  /**
   * Use activerecord methods to find our records
   *
   *  @param whereExp, tuples of the form (fieldname, opcode, value)
   */
  final override protected def getWithQuery(pageOffset: Option[Int] = None,
    pagesizeOpt: Option[Int] = None,
    orderBy: Option[String] = None,
    orderDir: Option[String] = None,
    whereExp: Iterable[LogicalBoolean] = Iterable.empty) = {
    try {
      var r = getFiltered

      // FIXME - use the where expression more correctly
      if (!whereExp.isEmpty) {
        debug("Applying filter expressions: " + whereExp.mkString(", "))
        r = applyFilterExpressions(r, whereExp.toSeq)
      }

      // Apply ordering
      for {
        orderOn <- orderBy
      } yield {
        val dir = orderDir.getOrElse("asc")

        r = r.orderBy(orderOn, dir)
      }

      // Apply paging restriction - to prevent casual scraping
      val maxPageSize = 100
      val pagesize = pagesizeOpt.getOrElse(maxPageSize)
      if (pagesize > maxPageSize)
        haltBadRequest("page_size is too large")

      val offset = pageOffset.getOrElse(0)
      r = r.page(offset, pagesize)

      r.toIterable
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

  /// Subclasses can provide suitable behavior if they want to allow DELs to /:id to result in deleting objects
  override protected def doDelete(o: T): Any = {
    val desc = o.toString
    o.delete()
    s"Deleted $desc"
  }
}