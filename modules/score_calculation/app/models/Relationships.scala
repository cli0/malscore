package models.score_calculation

import java.util.UUID

import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.json.Writes._

/*Definition of the relationship model for the staging_table.*/

case class Relationships(uuid: UUID, primaryobject: String, secondaryobject: String, username: Option[String],
                         pluginid: Option[Set[String]],timestamp: DateTime, relationshiptype: Set[String], comment: Option[String],
                         confidence: Double)

object Relationships{

  /*
  *Necessary restrictions made when manually uploading relationships so that new entries are
  * not incomplete and make sense: ex. lacking a secondaryobject hash or nonsensical confidence value.
  * If the JSON input does not conform to restrictions, an error will be raised.
  *
  * --- read ->   does not accept the absence of values
  * --- readNullable ->   value can also be absent and it won't raise an error
  * --- Reads.min(x) andKeep Reads.max() ->   min-max values allowed
  */

  implicit val relationshipReads : Reads[Relationships] = (
    (JsPath \ "uuid").read[UUID] and
      (JsPath \ "primaryobject").read[String] and
      (JsPath \ "secondaryobject").read[String] and
      (JsPath \ "username").readNullable[String] and
      (JsPath \ "pluginid").readNullable[Set[String]] and
      (JsPath \ "timestamp").read[DateTime] and  //format necessary "yyyy-mm-dd"
      (JsPath \ "relationshiptype").read[Set[String]] and //the tag that contains the relationship type, in this case it is MANUAL
      (JsPath \ "comment").readNullable[String] and
      (JsPath \ "confidence").read[Double](Reads.min(0.0) andKeep Reads.max(1.0)) //custom score from user for this relationship confidence
    )(Relationships.apply _)

  implicit val relationshipWrites = Json.writes[Relationships]

}
