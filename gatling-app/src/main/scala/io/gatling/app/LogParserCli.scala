/*
 * Copyright 2011-2025 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.app

import java.{ lang => jl, util => ju }
import java.io.{ BufferedInputStream, DataInputStream, EOFException, File }
import java.nio.ByteBuffer
import java.nio.file.Files
import java.time.ZoneId

import scala.collection.mutable
import scala.util.{ Failure, Success, Try, Using }

import io.gatling.charts.stats._
import io.gatling.commons.stats.{ KO, OK }
import io.gatling.commons.util.GatlingVersion
import io.gatling.commons.util.StringHelper._
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.stats.message.MessageEvent
import io.gatling.core.stats.writer._

import com.typesafe.scalalogging.StrictLogging

object LogParserCli extends StrictLogging {

  def main(args: Array[String]): Unit = {
    val (debugEnabled, logFilePath) = parseArgs(args)

    val logFile = new File(logFilePath).getAbsoluteFile

    if (!logFile.exists()) {
      System.err.println(s"File not found: $logFilePath")
      sys.exit(1)
    }

    Try {
      // Initialize string internals required for parsing
      StringInternals.checkAvailability()
      // Create a minimal configuration just for parsing - avoid loading full HTTP configuration
      val zoneId = ZoneId.systemDefault()
      // Use Gatling's internal deserializer by extending LogFileParser
      val records = Using.resource(new CsvRecordCollector(logFile, zoneId))(_.parse())
      outputCsv(records)
    } match {
      case Success(_) => // Success, CSV written to stdout
      case Failure(exception) =>
        System.err.println(s"Error parsing log file: ${exception.getMessage}")
        if (debugEnabled) {
          exception.printStackTrace()
        }
        sys.exit(1)
    }
  }

  private def parseArgs(args: Array[String]): (Boolean, String) =
    args.toList match {
      case "--debug" :: logFile :: Nil => (true, logFile)
      case logFile :: "--debug" :: Nil => (true, logFile)
      case logFile :: Nil              => (false, logFile)
      case _ =>
        System.err.println("Usage: LogParserCli [--debug] <simulation.log>")
        sys.exit(1)
    }

  private def outputCsv(records: AllRecords): Unit = {
    // Print CSV header
    println(
      "record_type,scenario_name,group_hierarchy,request_name,status,start_timestamp,end_timestamp,response_time_ms,error_message,event_type,duration_ms,cumulated_response_time_ms,is_incoming"
    )

    // Output all records in the order they appeared in the log file
    records.allRecords.foreach {
      case Left(userRecord) =>
        val eventType = if (userRecord.event == MessageEvent.Start) "start" else "end"
        println(s"user,${escapeCsv(userRecord.scenario)},,,,${userRecord.timestamp},,,,$eventType,,,")

      case Right(Left(requestRecord)) =>
        val groupHierarchy = requestRecord.group.map(_.hierarchy.mkString("|")).getOrElse("")
        val status = if (requestRecord.status == OK) "OK" else "KO"
        val errorMessage = requestRecord.errorMessage.getOrElse("")
        val isIncoming = requestRecord.incoming.toString
        val endTimestamp = if (requestRecord.incoming) "" else (requestRecord.start + requestRecord.responseTime).toString
        println(
          s"request,,${escapeCsv(groupHierarchy)},${escapeCsv(requestRecord.name)},$status,${requestRecord.start},$endTimestamp,${requestRecord.responseTime},${escapeCsv(errorMessage)},,,,$isIncoming"
        )

      case Right(Right(Left(groupRecord))) =>
        val groupHierarchy = groupRecord.group.hierarchy.mkString("|")
        val status = if (groupRecord.status == OK) "OK" else "KO"
        val endTimestamp = groupRecord.start + groupRecord.duration
        println(
          s"group,,${escapeCsv(groupHierarchy)},,$status,${groupRecord.start},$endTimestamp,,,,${groupRecord.duration},${groupRecord.cumulatedResponseTime},"
        )

      case Right(Right(Right(errorRecord))) =>
        println(s"error,,,,,${errorRecord.timestamp},,${escapeCsv(errorRecord.message)},,,,")
    }
  }

  private def escapeCsv(value: String): String =
    if (value.contains("\"") || value.contains(",") || value.contains("\n")) {
      "\"" + value.replace("\"", "\"\"") + "\""
    } else {
      value
    }
}

// Data holder for all records
final case class AllRecords(allRecords: List[Either[UserRecord, Either[RequestRecord, Either[GroupRecord, ErrorRecord]]]])

// Custom parser that extends Gatling's LogFileParser to collect records instead of processing them
private final class CsvRecordCollector(logFile: File, zoneId: ZoneId) extends LogFileParser[AllRecords](logFile) with StrictLogging {
  import io.github.metarank.cfor._

  private val allRecords = mutable.ListBuffer[Either[UserRecord, Either[RequestRecord, Either[GroupRecord, ErrorRecord]]]]()
  private var runStart: Long = 0L
  private var scenarios: Array[String] = Array.empty

  private def parseRunRecord(): Unit = {
    val gatlingVersion = readString()
    // Log version information for debugging
    logger.info(s"Log file was generated with Gatling $gatlingVersion")
    logger.info(s"Parsing with Gatling ${GatlingVersion.ThisVersion.fullVersion}")
    // Version check is relaxed to allow parsing logs from stable releases with SNAPSHOT versions
    // This is safe because we're using Gatling's internal deserializer

    val simulationClassName = readString()
    runStart = readLong()
    val runDescription = readString()

    scenarios = Array.fill(readInt())(readSanitizedString())

    // Skip assertions
    val assertionsSize = readInt()
    cfor(0 until assertionsSize)(_ => skip(readInt()))
  }

  private def parseUserRecord(): Unit = {
    val scenarioIndex = readInt()
    val event = if (readBoolean()) MessageEvent.Start else MessageEvent.End
    val timestamp = readInt() + runStart

    val scenario = if (scenarioIndex < scenarios.length) scenarios(scenarioIndex) else s"unknown_$scenarioIndex"
    val userRecord = UserRecord(scenario, event, timestamp)
    allRecords += Left(userRecord)
  }

  private def parseRequestRecord(): Unit = {
    val groupsSize = readInt()
    val group = Option.when(groupsSize > 0)(Group(List.fill(groupsSize)(readCachedSanitizedString())))
    val name = readCachedSanitizedString()
    val startTimestamp = readInt() + runStart
    val endTimestamp = readInt() + runStart
    val status = if (readBoolean()) OK else KO
    val errorMessage = readCachedSanitizedString().trimToOption

    val requestRecord = if (endTimestamp != Long.MinValue) {
      // regular request
      RequestRecord(
        group,
        name,
        status,
        startTimestamp,
        0, // buckets not needed for CSV
        0,
        (endTimestamp - startTimestamp).toInt,
        errorMessage,
        incoming = false
      )
    } else {
      // unmatched incoming event
      RequestRecord(group, name, status, startTimestamp, 0, 0, 0, errorMessage, incoming = true)
    }
    allRecords += Right(Left(requestRecord))
  }

  private def parseGroupRecord(): Unit = {
    val groupsSize = readInt()
    val group = Group(List.fill(groupsSize)(readCachedSanitizedString()))
    val startTimestamp = readInt() + runStart
    val endTimestamp = readInt() + runStart
    val cumulatedResponseTime = readInt()
    val status = if (readBoolean()) OK else KO

    val groupRecord = GroupRecord(group, (endTimestamp - startTimestamp).toInt, cumulatedResponseTime, status, startTimestamp, 0)
    allRecords += Right(Right(Left(groupRecord)))
  }

  private def parseErrorRecord(): Unit = {
    val message = readCachedSanitizedString()
    val timestamp = readInt() + runStart
    val errorRecord = ErrorRecord(message, timestamp)
    allRecords += Right(Right(Right(errorRecord)))
  }

  override def parse(): AllRecords = {
    logger.info("Parsing log file for CSV export using Gatling's internal deserializer")

    readByte() match {
      case RecordHeader.Run.value => parseRunRecord()
      case _                      => throw new UnsupportedOperationException(s"The log file $logFile is malformed and doesn't start with a proper record")
    }

    var count = 1
    var continue = true
    while (continue) {
      count += 1
      if (count % 10000 == 0) logger.info(s"Processed $count records")
      val headerValue = read().toByte
      try {
        headerValue match {
          case RecordHeader.User.value    => parseUserRecord()
          case RecordHeader.Request.value => parseRequestRecord()
          case RecordHeader.Group.value   => parseGroupRecord()
          case RecordHeader.Error.value   => parseErrorRecord()
          case -1                         => continue = false
          case _                          => throw new UnsupportedOperationException(s"Unsupported header $headerValue for record $count")
        }
      } catch {
        case e: EOFException =>
          logger.error(s"Log file is truncated after record $count, can only generate partial results.", e)
          continue = false
      }
    }

    logger.info(s"Parsing complete: processed $count records")
    AllRecords(allRecords.toList)
  }
}
