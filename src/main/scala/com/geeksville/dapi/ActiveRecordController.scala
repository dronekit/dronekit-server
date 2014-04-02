package com.geeksville.dapi

import com.github.aselab.activerecord.ActiveRecord
import org.scalatra.swagger.Swagger
import com.geeksville.dapi.model.CRUDOperations
import org.json4s.Formats
import com.geeksville.json.ActiveRecordSerializer

/**
 * A controller that assumes the backing object comes from ActiveRecord (allows easy field finding)
 */
class ActiveRecordController[T <: ActiveRecord: Manifest](aName: String, swagger: Swagger, companion: CRUDOperations[T])
  extends ApiController[T](aName, swagger, companion) {

  /// Fields we never want to share with clients
  val blacklist = Set("hashedPassword")

  override protected val jsonFormats: Formats = super.jsonFormats + ActiveRecordSerializer[T](blacklist)

  private val findParamOp =
    (apiOperation[T]("getParam")
      summary "Get a parameter from an object"
      parameters (
        pathParam[String]("id").description(s"Id of $aName that needs to be fetched"),
        pathParam[String]("param").description(s"The parameter to read from the object")))

  get("/:id/:param", operation(findParamOp)) {
    findById(params("id")).map { obj =>
      val param = params("param")
      obj.toMap(params("param"))
    }.getOrElse(halt(404))
  }

}