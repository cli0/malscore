package controllers.manual

import play.api
import models.manual.Relationships
import play.api.mvc._
import com.datastax.spark.connector._
import controllers.manual.SparkConfig._
import play.api.libs.json._
import play.api.libs.json.Json
import javax.inject._



@Singleton
class Upload @Inject extends Controller {

  //uploading entries to the relationship table
  def insert = Action(BodyParsers.parse.json) { implicit request =>

    //can accept an array of json inputs : [{#data1},{#data2}]
    val entries = request.body.validate[Array[Relationships]]
    // in case of error, like non conforming to restrictions, error message that points to the error
    entries.fold(errors => {BadRequest(Json.obj("status" -> "Bad Request", "message" -> JsError.toJson(errors)))},
      elements => {
        sc.parallelize(elements).saveToCassandra(keyspace_cluster, table_cluster, SomeColumns("uuid","comment",
        "weight_manual","pluginid","primaryobject","tag","secondaryobject","timestamp","username"))
        Ok(Json.obj("status" -> "OK", "message" -> Json.toJson("Successfully Uploaded")))
      }
    )
  }

}