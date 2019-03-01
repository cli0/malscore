package controllers

import models.Relationships
import controllers.SparkConfig._
import javax.inject._
import javax.inject.Inject
import play.api._
import play.api.mvc._
import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.libs.ws._
import play.api.http.HttpEntity
import play.api.libs.json._
import scala.concurrent.ExecutionContext
import play.api.mvc.{Controller, _}
import com.datastax.spark.connector._
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.duration.Duration.Inf

class Malscore @Inject() (ws: WSClient) extends Controller {

  def service = Action.async(BodyParsers.parse.json) { implicit request =>

    val id = request.body

    val queryURL = "http://localhost:9000/query"
    val mlURL = "http://localhost:9000/ml"
    val scoreURL = "http://localhost:9000/scoring"

    val nested_service_calls = for {

      query <- ws.url(queryURL).withRequestTimeout(86400000.millis).withHeaders("Content-Type"->"application/json")
        .withBody(id).get()
      ml <- ws.url(mlURL).withRequestTimeout(86400000.millis).withHeaders("Content-Type"->"application/json")
        .withBody(query.body).get()
      score <- ws.url(scoreURL).withRequestTimeout(86400000.millis).withHeaders("Content-Type"->"application/json")
        .withBody(ml.body).get()
    } yield score.body

    nested_service_calls.map(data => Ok(data))
  }

}
