package controllers.score_calculation

import com.google.inject._
import org.apache.spark.sql.hive.HiveContext
import play.api.mvc._
import workflow.score_calculation.LR
import org.joda.time._
import java.util.UUID
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import com.datastax.spark.connector._
import play.api.libs.json._
import org.apache.spark.mllib.regression.LabeledPoint
import models.score_calculation.Relationships
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.functions._

@Singleton
class Score @Inject() extends Controller {

  def confidence = Action(BodyParsers.parse.json) {implicit request =>

    /**************Setup SparkContext****************/
    val keyspace_cluster = "spaperks"

    val input_cluster = "staging_table"
    val output_cluster = "relationships"

    val cassandra_cluster = "10.0.4.80"

    val spark_master = "local"

    val appname = "malscore"

    val conf = new SparkConf(true)
      .set("spark.cassandra.connection.host", cassandra_cluster)
      .set("spark.cassandra.auth.username", "spaper")
      .set("spark.cassandra.auth.password", "workthatpaper")

    val sc = new SparkContext(spark_master, appname, conf)

    val hiveContext = new HiveContext(sc)


    /**************************************************/


    val id = (request.body \ "id").get.as[String]

    /*load all the relationship stubs collected by the previous components*/
    val relationshipDF = hiveContext
      .read
      .format("org.apache.spark.sql.cassandra")
      .options(Map( "table" -> "staging_table", "keyspace" -> "spaperks"))
      .load()


    /*
     *format the data for the linear regression by making an
     *RDD[id: String,plugins: Seq[String],relationship_tags: Seq[String], username:Seq[String],
     * comments: Seq[String], LabeledPoint(double,Vector)]
     * */


    val data = relationshipDF.groupBy("secondaryobject")
                                  .agg(collect_set("pluginid"),collect_set("tag"),
                                    collect_set("username"), collect_set("comment"),
                                    sum("weight_manual"), sum("weight_pehash"),
                                    sum("weight_imphash"), sum("weight_timestamp"),
                                    sum("weight_cname"), sum("weight_digitalsig"),
                                    sum("weight_peid_sim"), sum("weight_peinfo_sim"),
                                    sum("weight_yara_sim"),sum("weight_vt_sim"))
                                    .map(x=> (x.getAs[String](0), x.getAs[Seq[String]](1), x.getAs[Seq[String]](2),
                                      x.getAs[Seq[String]](3), x.getAs[Seq[String]](4),
                                      LabeledPoint(x.getAs[Double](14), Vectors.dense(x.getAs[Double](5),
                                        x.getAs[Double](6), x.getAs[Double](7), x.getAs[Double](8), x.getAs[Double](9),
                                        x.getAs[Double](10), x.getAs[Double](11), x.getAs[Double](12), x.getAs[Double](12),
                                        x.getAs[Double](13))
                                      )
                                      ))
    val training = data.filter(x=> x._3.contains("vt_sim"))

    training.cache()
    val scores = LR.linreg(data.map(_._6), training.map(_._6)).cache()

    /*create the RDD with the data necessary for uploading.
    * this should contain RDD[secondaryObject, plugins, tags, username, comments, weight]*/


    val final_rdd = data.map(x=> (x._1,x._2,x._3, x._4, x._5)).zip[Double](scores)
                            .map(x=> (x._1._1,x._1._2,x._1._3,x._1._4,x._1._5,x._2))


    final_rdd
      .collect
      .foreach(x=> sc.parallelize(Seq((UUID.randomUUID(),id,x._1,new DateTime(),x._2,x._3,x._4,x._5,LR.round(x._6))))
        .saveToCassandra(keyspace_cluster,output_cluster,SomeColumns("uuid","primaryobject","secondaryobject",
        "timestamp","pluginid","relationshiptype","username","comment","confidence")))

    val output = sc.cassandraTable[Relationships](keyspace_cluster,output_cluster)

    Ok(Json.toJson(output))
  }



}
