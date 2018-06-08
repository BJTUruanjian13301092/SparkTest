package com.example.sparktest.scala.spark

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.mllib.clustering.KMeans
import org.apache.spark.mllib.linalg.Vectors

object SparkML {

  def main(args: Array[String]): Unit = {
    kMeansAnalyzer
  }

  def kMeansAnalyzer: Unit = {

    val conf = new SparkConf().setAppName("KMeans")
    conf.setMaster("local")
    val sc = new SparkContext(conf)

    // 加载和解析数据文件
    val data = sc.textFile("mllib\\kmeans_data.txt")
    val parsedData = data.map{
      line => {
        Vectors.dense(line.split(" ").map(_.toDouble))
      }
    }.cache()

    // 设置迭代次数、类簇的个数
    val maxIterations = 100
    val numIterations = 20
    val numClusters = 2
    var clusterIndex = 0

    // 进行训练
    val clusters = KMeans.train(parsedData, numClusters, maxIterations, numIterations)

    println("Cluster Centers Information Overview:")
    clusters.clusterCenters.foreach(x => {
      println("Center Point of Cluster " + clusterIndex + " : " + x)
      clusterIndex+=1
    })

    // 统计聚类错误的样本比例
    val WSSSE = clusters.computeCost(parsedData)
    println("Within Set Sum of Squared Errors = " + WSSSE)

    //预测
    val predictVector = Vectors.dense(50.0)
    val index = clusters.predict(predictVector)
    println("Vector: " + predictVector + " belongs to cluster: " + index)
  }

}
