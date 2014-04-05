package com.geeksville.dapi

import com.github.aselab.activerecord.ActiveRecord
import org.scalatra.swagger.Swagger
import com.geeksville.dapi.model.CRUDOperations
import org.json4s.Formats
import com.geeksville.json.ActiveRecordSerializer2

/**
 * A controller that assumes the backing object comes from ActiveRecord (allows easy field finding)
 */
class ActiveRecordController[T <: ActiveRecord: Manifest](aName: String, swagger: Swagger, companion: CRUDOperations[T])
  extends ApiController[T](aName, swagger, companion) {

  /// Fields we never want to share with clients
  /// FIXME - add annotations for this?
  def blacklist = Set[String]()

  override implicit protected lazy val jsonFormats: Formats = super.jsonFormats + new ActiveRecordSerializer2(blacklist)

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

}