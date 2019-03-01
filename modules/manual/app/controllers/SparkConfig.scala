package controllers.manual

import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import com.datastax.spark.connector.cql._



object SparkConfig {

  val keyspace_cluster = "spaperks"

  val table_cluster = "staging_table"

  val cassandra_cluster = "10.0.4.80,10.0.4.81,10.0.4.82"

  val spark_master = "local"

  val appname = "malscore"

  val conf = new SparkConf(true)
      .setSparkHome("/home/relationship/spark-1.6.1-wh")
      .set("spark.cassandra.connection.host", cassandra_cluster)
      .set("spark.cassandra.auth.username", "spaper")
      .set("spark.cassandra.auth.password", "workthatpaper")

  val sc = new SparkContext(spark_master, appname, conf)

}

