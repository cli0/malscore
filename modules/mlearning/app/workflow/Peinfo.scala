package workflow.mlearning


//import controllers.mlearning.SparkConfig._
import workflow.mlearning.Algorithms._
import org.joda.time._
import java.util.UUID

import org.apache.spark.mllib.linalg.Vector
import play.api.libs.json._
import com.datastax.spark.connector._
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable.ArrayBuffer


object Peinfo {

  /*********************************helper methods***************************************/

  def get_pehash(data : String) : Seq[String] = {
    val pehash = Json.parse(data) \ "pehash"
    pehash match {
      case pehash : JsUndefined => return Seq()
      case _ => return Seq(pehash.toString.replaceAll("""["]""",""))
    }
  }

  def get_imphash(data : String) : Seq[String] = {
    val imphash = Json.parse(data) \ "imphash"
    imphash match {
      case imphash : JsUndefined => return Seq()
      case _ => return Seq(imphash.toString)
    }
  }

  def get_timestamp(data : String) : Seq[String] = {
    val timestamp = Json.parse(data) \ "timestamp" \ "timestamp"
    timestamp match {
      case timestamp : JsUndefined => return Seq()
      case _ => return Seq(timestamp.toString)
    }
  }

  def get_nr_of_sections(data: String) : Seq[String] = {
    val sections = Json.parse(data) \ "pe_sections" \\ "section_name"
    sections match {
      case sections : Seq[JsValue] => return Seq(sections.size.toString)
      case _ => return Seq()
    }
  }

  def get_imports(data: String): Seq[String] = {
    val func = (Json.parse(data) \ "imports" \\ "function").map(_.toString).toSeq
    val dll = (Json.parse(data) \ "imports" \\ "dll").map(_.toString).toSeq
    return dll++func
  }

  def get_vinfo(data: String) : Seq[String] = {

    val values= (Json.parse(data) \ "version_info" \\ "value").map(_.toString)
    val keys = (Json.parse(data) \ "version_info" \\ "key").map(_.toString)
    val vinfo = keys.zip(values)

    if (vinfo.isEmpty) {
      return Seq()
    }

    val internal_name = vinfo.filter(x=> x._1.contains("InternalName")).map(_._2)
    val product_name = vinfo.filter(x=> x._1.contains("ProductName")).map(_._2)
    val original_name = vinfo.filter(x=> x._1.contains("OriginalFileName")).map(_._2)
    val file_description = vinfo.filter(x=> x._1.contains("FileDescription")).map(_._2)
    val company_name = vinfo.filter(x=> x._1.contains("CompanyName")).map(_._2)


    val intname = if (!internal_name.isEmpty) internal_name(0) else ""
    val prdname = if (!product_name.isEmpty) product_name(0) else ""
    val origname = if (!original_name.isEmpty) original_name(0) else ""
    val filedescr = if (!file_description.isEmpty) file_description(0) else ""
    val cname = if (!company_name.isEmpty) company_name(0) else ""

    return Seq(intname,prdname,origname,filedescr,cname).filter(_.size>1)
  }

  /***********************************************************************************************/

  def run_bkm_peinfo(id: String): String = {

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

    /*obtain all DB contents for the PEID service*/
    val peinfoRDD = sc.cassandraTable("holmes_testing","results").select("sha256","results")
                      .where("service_name='peinfo'")
                      .map(x=> (x.get[String]("sha256"), x.get[String]("results")))
                      .map(x=> (x._1, get_imports(x._2)++get_nr_of_sections(x._2)++get_vinfo(x._2)))

    /*get the PEINFO results of the query sample and prepare(clean) the data in the appropriate format*/
    val query = sc.cassandraTable("holmes_testing","results").where("service_name=?","peinfo").where("sha256=?",id)
    if (query.isEmpty) {
      return "Abort: Query does not have PEINFO results."
    }
    val queryData = query.first.get[String]("results")

    val clean_queryData = get_imports(queryData)++get_nr_of_sections(queryData)++get_vinfo(queryData)

    /*create a vector for every PEINFO result in the database and for the query data,
    * use TF algorithm for that*/
    val (hashTF_features, hashTF_vec) = hashTF(15000, peinfoRDD.values, clean_queryData)
    val (hashIDF_features, hashIDF_vec) = hashIDF(2, hashTF_features, hashTF_vec)

    /*cluster the dataset and receive an rdd for the appropriate cluster numbers for each peid vector,
    * and the cluster number for the query vector*/
    val (cluster_rdd, cluster_query) = cluster_bkm(30, 2, hashTF_features, hashTF_vec)

    /*create a new RDD[String,Vector,Int] that contains sha256, each vector and the appropriate clusters*/
    val peidResults = peinfoRDD.keys.zip[Vector](hashTF_features).map(x=> (x._1, x._2))
                                    .zip[Int](cluster_rdd).map(y=> (y._1._1, y._1._2, y._2))
    /*filter and get only the identifiers + vectors that are in the same cluster as our query*/
    val cluster = peidResults.filter(_._3 == cluster_query).map(x=> (x._1,x._2))

    /*create new RDD for ID and similarity score between each vector in the cluster and the query vector*/
    val cosine_rdd = cluster.map(x=> (x._1,cosine_similarity(x._2,hashTF_vec)))

    /*save the new relationships in the staging table*/
    cosine_rdd
      .collect
      .foreach(x=> sc.parallelize(Seq((UUID.randomUUID(),id,x._1,new DateTime(), "peinfo_sim", "ML",round(x._2))))
        .saveToCassandra(keyspace_cluster,table_cluster, SomeColumns("uuid","primaryobject","secondaryobject",
          "timestamp","tag","pluginid","weight_peinfo_sim")))

    sc.stop()

    return "Successful completion."
  }
















}
