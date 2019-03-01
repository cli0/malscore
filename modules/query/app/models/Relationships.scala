package models.query

import java.util.UUID

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import org.joda.time.DateTime


case class Relationships(uuid: UUID, primaryobject: String, secondaryobject: String, username: Option[String],
                         pluginid: Option[String],timestamp: DateTime, rt_tag: String, comment: Option[String],
                         rt_manual: Double)

object Relationships{

  implicit val relationshipReads : Reads[Relationships] = (
    (JsPath \ "uuid").read[UUID] and
      (JsPath \ "primaryobject").read[String] and
      (JsPath \ "secondaryobject").read[String] and
      (JsPath \ "username").readNullable[String] and
      (JsPath \ "pluginid").readNullable[String] and
      (JsPath \ "timestamp").read[DateTime] and  //format necessary "yyyy-mm-dd"
      (JsPath \ "rt_tag").read[String] and //the tag that contains the relationship type, in this case it is MANUAL
      (JsPath \ "comment").readNullable[String] and
      (JsPath \ "rt_manual").read[Double](Reads.min(0.0) andKeep Reads.max(1.0)) //custom score from user for this relationship confidence
    )(Relationships.apply _)

  implicit val relationshipWrites = Json.writes[Relationships]

}
