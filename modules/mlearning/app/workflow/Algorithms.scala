package workflow.mlearning


import org.apache.spark.mllib.feature.HashingTF
import org.apache.spark.mllib.feature.{IDF , IDFModel}
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.mllib.clustering.{BisectingKMeans, BisectingKMeansModel}
import org.apache.spark.rdd.RDD

object Algorithms {



  /*This object contains all the ML methods that will be applied for ML detection of the different service
  * result similarities*/



  //This method hashes the query's data vector and all the vectors corresponding to a particular service.
  //The output is the RDD of hashed vectors and the vector for the query
  def hashTF(features: Int, db_vectors: RDD[Seq[String]], query_vector: Seq[String]) : (RDD[Vector], Vector) = {
    val hashingTF = new HashingTF(features)
    val hashtf_features = hashingTF.transform(db_vectors)
    val hashtf_vec = hashingTF.transform(query_vector)
    return (hashtf_features, hashtf_vec)
  }

  //This method applies the IDF algorithm to the previously hashed vectors.
  //The output is again an RDD of vectors where IDF was applies and the query vector.
  def hashIDF(minFreq: Int, dbTF: RDD[Vector], queryTF: Vector) : (RDD[Vector], Vector) = {
    val idf = new IDF(minFreq).fit(dbTF)
    val hashIDF_features = idf.transform(dbTF)
    val hashIDF_vec = idf.transform(queryTF)
    return (hashIDF_features, hashIDF_vec)
  }

  //This method applies Bisecting K-Means clustering to the data.
  //The output is an RDD[Int] with the cluster number for each element in the RDD + the cluster for the query vector
  def cluster_bkm(clusters: Int, iterations : Int, training: RDD[Vector], query: Vector) : (RDD[Int], Int) = {
    val bkm = new BisectingKMeans().setK(clusters).setMaxIterations(iterations)
    val model = bkm.run(training)
    val cluster_rdd = model.predict(training)
    val cluster_query = model.predict(query)
    return (cluster_rdd, cluster_query)
  }

  //method for calculating cosine_similarity
  def cosine_similarity(v1: org.apache.spark.mllib.linalg.Vector, v2: org.apache.spark.mllib.linalg.Vector) : Double = {
    val arr1 = v1.toArray
    val arr2 = v2.toArray
    val enorm1 = Vectors.norm(v1,2.0)
    val enorm2 = Vectors.norm(v2,2.0)
    if (enorm1==0 || enorm2==0 || arr1.size!=arr2.size){
      //undefined similarity actually, but return 0
      return 0.0
    }
    val product = arr1.zip(arr2).map(x=> x._1*x._2).sum
    return (product/(enorm1*enorm2))
  }

  def round(x:Double) : Double = {
    if (x > 1.0) {
      return 1.0
    }
    else if (x < 0.0) {
      return 0.0
    }
    else {
      return x
    }
  }

}
