package controllers

import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.sql.hive.HiveContext


object SparkConfig {

  val keyspace_cluster = "spaperks"

  val input_cluster = "staging_table"
  val output_cluster = "relationships"

  val cassandra_cluster = "10.0.4.80"

  val spark_master = "local"

  val appname = "malscore"

  val conf = new SparkConf(true)
    //.setSparkHome("/home/relationship/spark-1.6.1-wh")
    .set("spark.cassandra.connection.host", cassandra_cluster)
    .set("spark.cassandra.auth.username", "spaper")
    .set("spark.cassandra.auth.password", "workthatpaper")

  val sc = new SparkContext(spark_master, appname, conf)

  val hiveContext = new HiveContext(sc)

}
