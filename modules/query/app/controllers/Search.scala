package controllers.query

import com.google.inject._
import play.api.mvc._
import com.datastax.spark.connector._
//import controllers.query.SparkConfig._
import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import scala.collection.mutable.ArrayBuffer
import play.api.libs.json._
import org.joda.time.DateTime
import java.util.UUID

import org.apache.spark.rdd.RDD

@Singleton
class Search @Inject extends Controller {

  /*This component automatically queries the results table to find identical matches for specific output field content.
  * The only input it requires is the sha256 of the query sample. All the queries are done automatically. */

  /**************************helper methods*********************************/

  // helping method used for querying the CompanyName feature in PEINFO
  def get_company_name(input: String) : String = {

    val values= (Json.parse(input) \ "version_info" \\ "value").map(_.toString)
    val keys = (Json.parse(input) \ "version_info" \\ "key").map(_.toString)
    val companyname = keys.zip(values).filter(x=>(x._1).contains("CompanyName")).map(_._2)

    if (companyname.isEmpty) {
      return "NONE"
    }
    return companyname.head.replaceAll("""["]""","")
  }


  // helping method used for querying the Signature feature in PEINFO
  def check_cname(input: String) : Boolean = {

    /*the following are three of the most common company names with 98k+ and 33k+ and 10k+ matches each,
    * if sig is any of these two then it will just be ignored because they would
    * provide too much noise and little to no information*/
    val noise_array = Array("Microsoft Corporation","","NONE")

    if (noise_array.contains(input)) {
      return false
    }
    return true
  }

  /*The CompanyName value can vary a lot. A malicious file may tweak it to look normal.
  * I pooled the most popular cnames that look benign and assigned a low weight for them. That array is subject
  * to expansion if necessary. If the cname is some random combination of characters it gets a slightly higher value
   * and if it contains a wide array of characters, numbers or other symbols, it gets an even higher weight.
   *
   * The weight distribution was designed to assign a high score to the more irregular looking names. The more irregular
   * the value is, the more likely it is the malwares with the same values are related, AND the fewer are the hits
   * in the DB. After some tests with the results table,
   * I saw that identical matches for highly irregular cnames are at most in the double digits. An IOC
   * (of sorts) is more promising if it finds few good matches and doesnt ring the alarm a large dataset that
   * will just provide lots of noise.*/
  def weight_of_cname(input:String): Double = {

    val known_signers = Array("Sun Microsystems, Inc.","Adobe Systems, Inc.","Adobe Systems Incorporated",
      "Macromedia Inc.")
    if (known_signers.contains(input)){
      return 0.5
    }
    //if the signer only has the following standard symbols assign 0.5
    else if (input.matches("""[a-zA-Z .,]""")){
      return 0.75
    }
    //when numbers of other unknown symbols are concerned, increase relevance
    return 1.0
  }


  /*methdd looks for the signature in PEINFO where available*/
  def get_digitalsig(result: String) : String = {

    val x= (Json.parse(result) \ "version_info" \\ "value").map(_.toString)
    val y = (Json.parse(result) \ "version_info" \\ "key").map(_.toString)
    val sig = y.zip(x).filter(x=>(x._1).contains("Signature")).map(_._2)

    if (sig.isEmpty) {
      return "NONE"
    }
    return sig.head.replaceAll("""["]""","")
  }


  /************************************************************************************************/



  def search = Action(BodyParsers.parse.json) { implicit request =>

    /***************Setup SparkContext*****************/

    val keyspace_cluster = "spaperks"

    val table_cluster = "staging_table"

    val cassandra_cluster = "10.0.4.80,10.0.4.81,10.0.4.82"

    val spark_master = "local"

    val appname = "malscore"

    val conf = new SparkConf(true)
      //.setSparkHome("/home/relationship/spark-1.6.1-wh")
      .set("spark.cassandra.connection.host", cassandra_cluster)
      .set("spark.cassandra.auth.username","spaper")
      .set("spark.cassandra.auth.password", "workthatpaper")
      .set("spark.executor.cores","4")
      .set("spark.worker.instances","6")
      .set("spark.worker.memory","2g")

    val sc = new SparkContext(spark_master, appname, conf)

    /****************************************************/


    val payload = request.body
    //the system input requires a JSON input with key "primarystring"
    val id = (request.body \ "id").get.as[String]

    var services = ArrayBuffer[String]()

    /*Create an array with the name of all the distinct services the query sample has been analyzed with.*/
    sc.cassandraTable("holmes_testing","results").select("service_name").where("sha256 = ?",id)
        .collect.foreach(x=> services += x.get[String]("service_name"))
    services.distinct

    /*Goes through the detected services to query for specific relationships.*/

    /*Right now the queries focus mostly on peinfo results.*/

    if (services.contains("peinfo")){
      /*PEHASH Query*/
      val sample_pehash = (Json.parse(
                              sc.cassandraTable("holmes_testing","results")
                                .select("sha256","results")
                                .where(s"service_name='peinfo' and sha256='$id'")
                                .first
                                .get[String]("results")) \ "pehash")
                                .asOpt[String].getOrElse("Undefined")

      if (!sample_pehash.equals("Undefined")){

        /*find matches with the same pehash in common as the sample and prevent self-reference*/

        val queryRDD = sc.cassandraTable("holmes_testing","results")
                        .select("sha256","results")
                        .where("service_name=?","peinfo")
                        .filter(x => (Json.parse(
                                          x.get[String]("results")) \ "pehash")
                                          .asOpt[String].getOrElse("Undefined")
                                          .contains(sample_pehash) && !x.get[String]("sha256").contains(id))
        /* save the identified matches in the temp_relationships table*/
        queryRDD.collect.foreach(x => sc.parallelize(Seq((UUID.randomUUID(), id, x.get[String]("sha256"), new DateTime(),
                    "Pehash","query",1.0))).saveToCassandra(keyspace_cluster, table_cluster, SomeColumns("uuid",
                    "primaryobject", "secondaryobject", "timestamp", "tag","pluginid", "weight_pehash")))
      }


      /*TIMESTAMP Query*/
      val sample_timestamp = (Json.parse(
                                sc.cassandraTable("holmes_testing","results")
                                  .select("sha256","results")
                                  .where(s"service_name='peinfo' and sha256 = '$id'").first
                                  .get[String]("results")) \ "timestamp" \ "timestamp")
                                  .asOpt[Long].getOrElse(-1:Long)

      if(!sample_timestamp.equals(-1:Long)){

        val queryRDD = sc.cassandraTable("holmes_testing","results")
                          .select("sha256","results")
                          .where("service_name=?","peinfo")
                          .filter(x => (Json.parse(x.get[String]("results")) \ "timestamp" \ "timestamp")
                          .asOpt[Long].getOrElse(-1:Long)
                            .equals(sample_timestamp) && !x.get[String]("sha256").contains(id))

        queryRDD.collect.foreach(x => sc.parallelize(Seq((UUID.randomUUID(), id, x.get[String]("sha256"), new DateTime(),
                      "Timestamp","query",1.0))).saveToCassandra(keyspace_cluster, table_cluster, SomeColumns("uuid",
                      "primaryobject", "secondaryobject", "timestamp", "tag","pluginid","weight_timestamp")))
      }

      /*IMPHASH Query*/
      val sample_imphash = (Json.parse(sc.cassandraTable("holmes_testing","results")
                          .select("sha256","results")
                          .where(s"service_name='peinfo' and sha256 = '$id'").first
                          .get[String]("results")) \ "imphash")
                          .asOpt[String].getOrElse("Undefined")

      if(!sample_imphash.equals("Undefined")){

        val queryRDD = sc.cassandraTable("holmes_testing","results")
                          .select("sha256","results")
                          .where("service_name=?","peinfo")
                          .filter(x => (Json.parse(x.get[String]("results")) \ "imphash")
                          .asOpt[String].getOrElse("Undefined")
                            .contains(sample_imphash) && !x.get[String]("sha256").contains(id))

        queryRDD.collect.foreach(x => sc.parallelize(Seq((UUID.randomUUID(), id, x.get[String]("sha256"), new DateTime(),
                      "Imphash","query",1.0))).saveToCassandra(keyspace_cluster, table_cluster, SomeColumns("uuid",
                      "primaryobject", "secondaryobject", "timestamp", "tag","pluginid","weight_imphash")))
      }



      /*VersionInfo  CompanyName Query*/
      val sample_cname = get_company_name(sc.cassandraTable("holmes_testing","results")
                                            .select("sha256","results")
                                            .where(s"service_name='peinfo' and sha256 = '$id'").first
                                            .get[String]("results"))

      //continue only if sample has a valid name
      if (check_cname(sample_cname)){
        //find all the company names that match the sample's
        val queryRDD = sc.cassandraTable("holmes_testing","results")
                          .select("sha256","results").where("service_name=?","peinfo")
                          .filter(x => get_company_name(x.get[String]("results")).equals(sample_cname) &&
                            !x.get[String]("sha256").contains(id))
                          .map(x=> (x.get[String]("sha256"), get_company_name(x.get[String]("results"))))

        queryRDD.collect.foreach(x=> sc.parallelize(Seq((UUID.randomUUID,id, x._1, new DateTime(),
          "CompanyName", "query", weight_of_cname(x._2)))).saveToCassandra(keyspace_cluster,table_cluster,
          SomeColumns("uuid","primaryobject","secondaryobject","timestamp","tag","pluginid","weight_cname")))
      }


      /*DigitalSignature Query*/

      val digitsig = get_digitalsig(sc.cassandraTable("holmes_testing","results")
                                    .select("sha256","results")
                                    .where(s"service_name='peinfo' and sha256 = '$id'").first
                                    .get[String]("results"))

      if(!digitsig.equals("NONE")){

        val queryRDD = sc.cassandraTable("holmes_testing","results")
                          .select("sha256","results").where("service_name=?","peinfo")
                            .filter(x=> get_digitalsig(x.get[String]("results")).equals(digitsig)
                            && !x.get[String]("sha256").contains(id))


        queryRDD.collect.foreach(x=> sc.parallelize(Seq((UUID.randomUUID,id, x.get[String]("sha256"), new DateTime(),
          "DigitalSignature", "query", 1.0))).saveToCassandra(keyspace_cluster,table_cluster,
          SomeColumns("uuid","primaryobject","secondaryobject","timestamp","tag","pluginid","weight_digitalsig")))
      }


    }

    sc.stop()

/*    if (services.contains("virustotal")){
      /*Symantec,Microsoft,Kaspersky_exact Query*/
      val microsoft_sig = (Json.parse(sc.cassandraTable("holmes_testing","results")
                        .select("sha256","results")
                        .where(s"service_name='virustotal' and sha256 = '$id'").first
                        .get[String]("results"))\"scans"\"Microsoft"\"result")
                        .asOpt[String].getOrElse("Undefined")
      val symantec_sig = (Json.parse(sc.cassandraTable("holmes_testing","results")
                        .select("sha256","results")
                        .where(s"service_name='virustotal' and sha256 = '$id'").first
                        .get[String]("results"))\"scans"\"Symantec"\"result")
                        .asOpt[String].getOrElse("Undefined")
      val kaspersky_sig = (Json.parse(sc.cassandraTable("holmes_testing","results")
                        .select("sha256","results")
                        .where(s"service_name='virustotal' and sha256 = '$id'").first
                        .get[String]("results"))\"scans"\"Kaspersky"\"result")
                        .asOpt[String].getOrElse("Undefined")
      /*find only the entries whose match for all three signatures is identical with the sample's*/
      val queryRDD = sc.cassandraTable("holmes_testing","results")
        .select("sha256","results").where("service_name=?","virustotal")
        .filter(x => (Json.parse(x.get[String]("results"))\ "scans" \ "Microsoft" \ "result").toString.contains(microsoft_sig)
          && (Json.parse(x.get[String]("results"))\ "scans" \ "Symantec" \ "result").toString.contains(symantec_sig)
          && (Json.parse(x.get[String]("results"))\"scans"\"Kaspersky"\"result").toString.contains(kaspersky_sig)
          && !x.get[String]("sha256").contains(id))

      queryRDD.collect.foreach(x => sc.parallelize(Seq((UUID.randomUUID(), id, x.get[String]("sha256"), new DateTime(),
        "SMK_exact","query",1.0))).saveToCassandra(keyspace_cluster, table_cluster, SomeColumns("uuid",
        "primaryobject", "secondaryobject", "timestamp", "rt_tag","pluginid","rt_smk")))

    }*/

    Ok(payload)
  }




}


