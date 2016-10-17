package com.uebercomputing.analytics.basic

import org.apache.log4j.Logger
import org.apache.spark.SparkContext._
import com.uebercomputing.mailrecord.ExecutionTimer
import com.uebercomputing.mailrecord.Implicits.mailRecordToMailRecordOps
import com.uebercomputing.mailrecord.MailRecordAnalytic
import com.uebercomputing.mailparser.enronfiles.AvroMessageProcessor
import com.uebercomputing.mailparser.enronfiles.MessageProcessor
import java.nio.charset.StandardCharsets
import scala.collection.mutable.{ Set => MutableSet }
import org.apache.spark.rdd.RDD

/**
 * Run with two args:
 *
 * Enron:
 * --avroMailInput /opt/rpm1/enron/filemail.avro --master local[4]
 */
object FoldersPerUserStatistics extends ExecutionTimer {

  val LOGGER = Logger.getLogger(FoldersPerUserStatistics.getClass)

  def main(args: Array[String]): Unit = {
    startTimer()
    val appName = "FoldersPerUserStatistics"
    val additionalSparkProps = Map[String, String]()
    val analyticInput = MailRecordAnalytic.getAnalyticInput(appName, args, additionalSparkProps, LOGGER)
    val userFolderTuplesRdd: RDD[(String, String)] = analyticInput.mailRecordsRdd.flatMap { mailRecord =>
      val userNameOpt = mailRecord.getMailFieldOpt(MessageProcessor.UserName)
      val folderNameOpt = mailRecord.getMailFieldOpt(MessageProcessor.FolderName)
      if (userNameOpt.isDefined && folderNameOpt.isDefined) {
        Some((userNameOpt.get, folderNameOpt.get))
      } else {
        None
      }
    }
    userFolderTuplesRdd.cache()

    //
    // mutable set - reduce object creation/garbage collection
    val uniqueFoldersByUserRdd: RDD[(String, MutableSet[String])] =
      userFolderTuplesRdd.aggregateByKey(MutableSet[String]())(
        seqOp = (folderSet, folder) => folderSet + folder,
        combOp = (set1, set2) => set1 ++ set2)
    val folderPerUserRddExact: RDD[(String, Int)] = uniqueFoldersByUserRdd.mapValues { set => set.size }.sortByKey()

    folderPerUserRddExact.saveAsTextFile("exact")

    val folderCounts: RDD[Int] = folderPerUserRddExact.values

    val stats = folderCounts.stats()
    println(stats)

    //
    // Who has 193 folders?!?
    //
    // see ordering example in OrderedRDDFunctions
    //
    // http://spark.apache.org/docs/1.2.0/api/scala/index.html#org.apache.spark.rdd.OrderedRDDFunctions
    implicit val orderByFolderCount = new Ordering[(String, Int)] {
      override def compare(a: (String, Int), b: (String, Int)): Int = {
        val folderCountComparison = a._2.compare(b._2)
        if (folderCountComparison != 0) folderCountComparison else a._1.compare(b._1)
      }
    }

    //
    // (kean-s,193) - uses implicit ordering
    println(folderPerUserRddExact.max)
    //
    // or explicitly
    println(folderPerUserRddExact.max()(orderByFolderCount))

    //
    // or if we don't care about ties
    println(folderPerUserRddExact.max()(
      Ordering.by(tuple => tuple._2)))

    val folderPerUserRddEstimate = userFolderTuplesRdd.countApproxDistinctByKey().sortByKey()

    val estimatedStats = folderPerUserRddEstimate.values.stats()
    println(estimatedStats)

    folderPerUserRddEstimate.saveAsTextFile("estimate")

    analyticInput.sc.stop()
    stopTimer()
    val prefixMsg = s"Executed over ${analyticInput.config.avroMailInput} in: "
    logTotalTime(prefixMsg, LOGGER)
  }
}
