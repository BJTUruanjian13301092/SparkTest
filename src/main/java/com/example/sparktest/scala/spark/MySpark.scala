package com.example.sparktest.scala.spark

import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext, sql}

case class Words(word: String, count: Long)

object MySpark {

  def main(args: Array[String]): Unit = {
    //sparkNormalTest
    //sparkStreamTest
    sparkSQLTest
    //sparkGraphXTest
    //sparkPageRankTest
  }

  /**
    * Spark Stream
    */
  def sparkStreamTest: Unit = {
    val conf = new SparkConf().setAppName("mySpark")
    //setMaster("local") 本机的spark就用local，远端的就写ip
    //如果是打成jar包运行则需要去掉 setMaster("local")因为在参数中会指定。
    conf.setMaster("local")

    val ssc = new StreamingContext(conf, Seconds(20))
    val fileRDD = ssc.textFileStream("myFile").cache().flatMap(_.split(" ")).map((_,1)).reduceByKey(_+_)
    fileRDD.print()
    ssc.start()
    ssc.awaitTermination()
  }

  /**
    * Spark 常规操作
    */
  def sparkNormalTest: Unit = {
    val conf = new SparkConf().setAppName("mySpark")
    //setMaster("local") 本机的spark就用local，远端的就写ip
    //如果是打成jar包运行则需要去掉 setMaster("local")因为在参数中会指定。
    conf.setMaster("local")
    val sc = new SparkContext(conf)
    val rdd = sc.parallelize(List(1,2,3,4,5,6)).map(_*3)
    val mappedRDD = rdd.filter(_>10).collect()
    //对集合求和
    println
    println(rdd.reduce(_+_))
    //输出大于10的元素
    for(arg <- mappedRDD)
      print(arg + " ")

    println()
    println("math is work")

    val filePath = "logFile.txt"
    val logData = sc.textFile(filePath).cache()
    // How many lines contain a or b?
    val numA = logData.filter(line => line.contains("a")).count()
    val numB = logData.filter(line => line.contains("b")).count()
    println("Lines with a: %s, Lines with b: %s".format(numA, numB))

    val maxCountLine = logData.map(line => line.split(" ").length).reduce((a, b) => Math.max(a, b))
    println("Max line words : " + maxCountLine)

    val logDataRDD = logData.flatMap(line => line.split(" ")).map((_,1)).reduceByKey(_+_)
    logDataRDD.collect().foreach(println)
    println(logDataRDD.toDebugString)

  }

  /**
    * Spark SQL
    */
  def sparkSQLTest: java.util.List[_] = {
    val spark = SparkSession
      .builder()
      .master("local")
      .appName("My Spark SQL basic example")
      .config("spark.some.config.option", "some-value")
      .getOrCreate()

    val df = spark.read.json("testJson.json")
    df.show()
    df.printSchema()
    df.select("name").show()

    df.createOrReplaceTempView("people")
    val sqlDF = spark.sql("select * from people where age between 16 and 25")
    sqlDF.show()

    //读取文件到RDD,处理之后转化为DataFrame用户SQL搜索
    val fileData = spark.sparkContext.textFile("logFile.txt").flatMap(line => line.split(" "))
      .map((_,1)).reduceByKey(_+_).map(attributes => Words(attributes._1, attributes._2))
    val fileDataDF = spark.createDataFrame(fileData)
    fileDataDF.createOrReplaceTempView("words")
    spark.sql("select * from words").show()
    spark.sql("select * from words").collect().foreach(println)

    val list = spark.sql("select * from words").rdd.map{
      case Row(word: String, count: Long) => Words(word, count)
    }.toJavaRDD().collect()

    return list
  }

  /**
    * Spark GraphX
    */
  def sparkGraphXTest: Unit = {
    //设置运行环境
    val conf = new SparkConf().setAppName("SimpleGraphX").setMaster("local")
    val sc = new SparkContext(conf)

    //设置顶点和边，注意顶点和边都是用元组定义的Array
    //顶点的数据类型是VD:(String,Int)
    val vertexArray = Array(
      (1L, ("Alice", 28)),
      (2L, ("Bob", 27)),
      (3L, ("Charlie", 65)),
      (4L, ("David", 42)),
      (5L, ("Ed", 55)),
      (6L, ("Fran", 50))
    )

    //边的数据类型ED:Int
    val edgeArray = Array(
      Edge(2L, 1L, 7),
      Edge(2L, 4L, 2),
      Edge(3L, 2L, 4),
      Edge(3L, 6L, 3),
      Edge(4L, 1L, 1),
      Edge(5L, 2L, 2),
      Edge(5L, 3L, 8),
      Edge(5L, 6L, 3)
    )

    //构造vertexRDD和edgeRDD
    val vertexRDD: RDD[(Long, (String, Int))] = sc.parallelize(vertexArray)
    val edgeRDD: RDD[Edge[Int]] = sc.parallelize(edgeArray)

    //构造图Graph[VD,ED]
    val graph: Graph[(String, Int), Int] = Graph(vertexRDD, edgeRDD)

    //*****************************************************************************
    //***************************  图的属性  ****************************************
    //******************************************************************************
    println("**********************************************************")
    println("属性演示")
    println("**********************************************************")
    println("找出图中年龄大于30的顶点：")
    graph.vertices.filter { case (id, (name, age)) => age > 30}.collect.foreach {
      case (id, (name, age)) => println(s"$name is $age")
    }

    //边操作：找出图中属性大于5的边
    println("找出图中属性大于5的边：")
    graph.edges.filter(e => e.attr > 5).collect.foreach(e => println(s"${e.srcId} to ${e.dstId} att ${e.attr}"))
    println

    //triplets操作，((srcId, srcAttr), (dstId, dstAttr), attr)
    println("列出边属性>5的tripltes：")
    for (triplet <- graph.triplets.filter(t => t.attr > 5).collect) {
      println(s"${triplet.srcAttr._1} likes ${triplet.dstAttr._1}")
    }
    println

    //Degrees操作
    println("找出图中最大的出度、入度、度数：")
    def max(a: (VertexId, Int), b: (VertexId, Int)): (VertexId, Int) = {
      if (a._2 > b._2) a else b
    }
    println("max of outDegrees:" + graph.outDegrees.reduce(max) + " max of inDegrees:" + graph.inDegrees.reduce(max) + " max of Degrees:" + graph.degrees.reduce(max))
    println

    //*****************************************************************************
    //***************************  转换操作  ****************************************
    //******************************************************************************
    println("**********************************************************")
    println("转换操作")
    println("**********************************************************")
    println("顶点的转换操作，顶点age + 10：")
    graph.mapVertices{ case (id, (name, age)) => (id, (name, age+10))}.vertices.collect.foreach(v => println(s"${v._2._1} is ${v._2._2}"))
    println

    println("边的转换操作，边的属性*2：")
    graph.mapEdges(e=>e.attr*2).edges.collect.foreach(e => println(s"${e.srcId} to ${e.dstId} att ${e.attr}"))
    println

    //*****************************************************************************
    //***************************  结构操作  ****************************************
    //******************************************************************************
    println("**********************************************************")
    println("结构操作")
    println("**********************************************************")
    println("顶点年纪>30的子图：")
    val subGraph = graph.subgraph(vpred = (id, vd) => vd._2 >= 30)
    println("子图所有顶点：")
    subGraph.vertices.collect.foreach(v => println(s"${v._2._1} is ${v._2._2}"))
    println

    println("子图所有边：")
    subGraph.edges.collect.foreach(e => println(s"${e.srcId} to ${e.dstId} att ${e.attr}"))
    println

    //*****************************************************************************
    //***************************  连接操作  ****************************************
    //******************************************************************************
    println("**********************************************************")
    println("连接操作")
    println("**********************************************************")
    val inDegrees: VertexRDD[Int] = graph.inDegrees
    case class User(name: String, age: Int, inDeg: Int, outDeg: Int)

    //创建一个新图，顶点VD的数据类型为User，并从graph做类型转换
    val initialUserGraph: Graph[User, Int] = graph.mapVertices { case (id, (name, age)) => User(name, age, 0, 0)}

    //initialUserGraph与inDegrees、outDegrees（RDD）进行连接，并修改initialUserGraph中inDeg值、outDeg值
    val userGraph = initialUserGraph.outerJoinVertices(initialUserGraph.inDegrees) {
      case (id, u, inDegOpt) => User(u.name, u.age, inDegOpt.getOrElse(0), u.outDeg)
    }.outerJoinVertices(initialUserGraph.outDegrees) {
      case (id, u, outDegOpt) => User(u.name, u.age, u.inDeg,outDegOpt.getOrElse(0))
    }

    println("连接图的属性：")
    userGraph.vertices.collect.foreach(v => println(s"${v._2.name} inDeg: ${v._2.inDeg}  outDeg: ${v._2.outDeg}"))
    println

    println("出度和入读相同的人员：")
    userGraph.vertices.filter {
      case (id, u) => u.inDeg == u.outDeg
    }.collect.foreach {
      case (id, property) => println(property.name)
    }
    println

    //*****************************************************************************
    //***************************  实用操作  ****************************************
    //******************************************************************************
    println("**********************************************************")
    println("聚合操作")
    println("**********************************************************")
    println("找出5到各顶点的最短：")
    val sourceId: VertexId = 5L // 定义源点
    val initialGraph = graph.mapVertices((id, _) => if (id == sourceId) 0.0 else Double.PositiveInfinity)
    val sssp = initialGraph.pregel(Double.PositiveInfinity)(
      (id, dist, newDist) => math.min(dist, newDist),
      triplet => {  // 计算权重
        if (triplet.srcAttr + triplet.attr < triplet.dstAttr) {
          Iterator((triplet.dstId, triplet.srcAttr + triplet.attr))
        } else {
          Iterator.empty
        }
      },
      (a,b) => math.min(a,b) // 最短距离
    )
    println(sssp.vertices.collect.mkString("\n"))
    sc.stop()

  }

  /**
    * Spark PageRank
    */
  def sparkPageRankTest: Unit = {
    //设置运行环境
    val conf = new SparkConf().setAppName("PageRank").setMaster("local")
    val sc = new SparkContext(conf)

    //读入数据文件
    val articles: RDD[String] = sc.textFile("pageVertex.txt")
    val links: RDD[String] = sc.textFile("pageEdge.txt")

    //装载顶点和边
    val vertices = articles.map { line =>
      val fields = line.split(' ')
      (fields(0).toLong, fields(1))
    }
    val edges = links.map { line =>
      val fields = line.split(' ')
      Edge(fields(0).toLong, fields(1).toLong, 0)
    }

    //cache操作
    //val graph = Graph(vertices, edges, "").persist(StorageLevel.MEMORY_ONLY_SER)
    val graph = Graph(vertices, edges, "").persist()
    //graph.unpersistVertices(false)

    //测试
    println("**********************************************************")
    println("获取5个triplet信息")
    println("**********************************************************")
    graph.triplets.take(5).foreach(println(_))

    //pageRank算法里面的时候使用了cache()，故前面persist的时候只能使用MEMORY_ONLY
    println("**********************************************************")
    println("PageRank计算，获取最有价值的数据")
    println("**********************************************************")
    val prGraph = graph.pageRank(0.001).cache()

    val titleAndPrGraph = graph.outerJoinVertices(prGraph.vertices) {
      (v, title, rank) => (rank.getOrElse(0.0), title)
    }

    titleAndPrGraph.vertices.top(10) {
      Ordering.by((entry: (VertexId, (Double, String))) => entry._2._1)
    }.foreach(t => println(t._2._2 + ": " + t._2._1))


    sc.stop()

  }

}
