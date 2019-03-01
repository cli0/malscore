package workflow.mlearning

//import controllers.mlearning.SparkConfig._
import workflow.mlearning.Algorithms._
import org.joda.time._
import java.util.UUID

import org.apache.spark.mllib.linalg.Vector
import com.datastax.spark.connector._
import org.apache.spark.{SparkConf, SparkContext}

object Yara {

  def run_bkm_yara(id: String): String = {

    /**************Setup SparkContext****************/
    val keyspace_cluster = "spaperks"

    val table_cluster = "staging_table"

    val cassandra_cluster = "10.0.4.80,10.0.4.81,10.0.4.82"

    val spark_master = "local"

    val appname = "malscore"

    val conf = new SparkConf(true)
      .set("spark.cassandra.connection.host", cassandra_cluster)
      .set("spark.cassandra.auth.username", "spaper")
      .set("spark.cassandra.auth.password", "workthatpaper")

    val sc = new SparkContext(spark_master, appname, conf)

    /**************************************************/

    /*obtain all DB contents for the YARA service*/
    val yaraRDD = sc.cassandraTable("holmes_testing","results").select("sha256","results").where("service_name='yara'")
                    .map(x=> (x.get[String]("sha256"), x.get[String]("results").replaceAll("[^a-zA-Z,_:]","")
                    .replaceAll("yara:","").replaceAll("rule:",""))).mapValues(_.split(",").toSeq)

    /*get the yara results of the query sample and prepare(clean) the data in the appropriate format*/
    val query = sc.cassandraTable("holmes_testing","results").where("service_name=?","yara").where("sha256=?",id)
    if (query.isEmpty) {
      return "Abort: Query does not have YARA results."
    }
    val queryData = query.first.get[String]("results")
    val clean_queryData = queryData.replaceAll("[^a-zA-Z,_:]","")
                                    .replaceAll("yara:","")
                                    .replaceAll("rule:","").split(",")

    /*create a vector for every YARA result in the database and for the query data,
    * use TF_IDF algorithm for that*/

    val (hashTF_features, hashTF_vec) = hashTF(15000, yaraRDD.values, clean_queryData)
    val (hashIDF_features, hashIDF_vec) = hashIDF(2, hashTF_features, hashTF_vec)

    /*cluster the dataset and receive an rdd for the appropriate cluster numbers for each yara vector,
    * and the cluster number for the query vector*/
    val (cluster_rdd, cluster_query) = cluster_bkm(30, 2, hashIDF_features, hashIDF_vec)

    /*create a new RDD[String,Vector,Int] that contains sha256, each vector and the appropriate clusters*/
    val yaraResults = yaraRDD.keys.zip[Vector](hashIDF_features).map(x=> (x._1, x._2))
                              .zip[Int](cluster_rdd).map(y=> (y._1._1, y._1._2, y._2))
    /*filter and get only the identifiers + vectors that are in the same cluster as our query*/
    val cluster = yaraResults.filter(_._3 == cluster_query).map(x=> (x._1,x._2))

    /*create new RDD for ID and similarity score between each vector in the cluster and the query vector*/
    val cosine_rdd = cluster.map(x=> (x._1,cosine_similarity(x._2,hashIDF_vec)))

    /*save the new relationships in the staging table*/
    cosine_rdd
      .collect
      .foreach(x=> sc.parallelize(Seq((UUID.randomUUID(),id,x._1,new DateTime(), "yara_sim", "ML",round(x._2))))
        .saveToCassandra(keyspace_cluster,table_cluster,
          SomeColumns("uuid","primaryobject","secondaryobject","timestamp","tag","pluginid","weight_yara_sim")))

    sc.stop()

    return "Successful completion."
  }

}
