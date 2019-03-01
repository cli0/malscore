package workflow.score_calculation


import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.regression.LinearRegressionWithSGD
import org.apache.spark.mllib.regression.LinearRegressionModel



object LR {

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

  def linreg(data: RDD[LabeledPoint], training: RDD[LabeledPoint]): RDD[Double] = {

    val iterations = 3000
    val gradient = 0.00000001
    val model = LinearRegressionWithSGD.train(training,iterations,gradient)
    val vectors = data.map(_.features)
    val prediction = model.predict(vectors)
    return prediction
  }

}
