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

package io.gatling.logparser

import java.io.File

import scala.collection.mutable
import scala.util.{ Failure, Success, Try, Using }

import io.gatling.charts.stats._
import io.gatling.commons.stats.{ KO, OK }
import io.gatling.commons.util.StringHelper._
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.stats.message.MessageEvent
import io.gatling.core.stats.writer._
import io.gatling.logparser.cli.LogParserArgsParser

import com.typesafe.scalalogging.StrictLogging

object GatlingLogParserCli extends StrictLogging {

  def main(args: Array[String]): Unit = {
    val argsParser = new LogParserArgsParser(args)
    argsParser.parseArguments match {
      case Left(logParserArgs) =>
        val exitCode = run(logParserArgs)
        sys.exit(exitCode)
      case Right(statusCode) =>
        sys.exit(statusCode.code)
    }
  }

  private def run(args: LogParserArgs): Int = {
    val logFile = new File(args.logFilePath).getAbsoluteFile

    if (!logFile.exists()) {
      System.err.println(s"File not found: ${args.logFilePath}")
      1
    } else {

      Try {
        // Initialize string internals like the real LogFileReader does
        try {
          StringInternals.checkAvailability()
        } catch {
          case e: Exception =>
            if (args.debugEnabled) {
              System.err.println(s"[DEBUG] StringInternals not available: ${e.getMessage}")
            }
        }
        
        val configuration = GatlingConfiguration.loadForTest()
        val records = Using.resource(new RecordCollectingParser(logFile, configuration, args.debugEnabled))(_.parse())
        
        if (args.debugEnabled) {
          System.err.println(s"[DEBUG] Parsed log file with ${records.requestRecords.size} request records")
          System.err.println(s"[DEBUG] ${records.userRecords.size} user records")
          System.err.println(s"[DEBUG] ${records.groupRecords.size} group records")
          System.err.println(s"[DEBUG] ${records.errorRecords.size} error records")
        }
        
        outputCsv(records)
      } match {
        case Success(_) => 0
        case Failure(exception) =>
          System.err.println(s"Error parsing log file: ${exception.getMessage}")
          if (args.debugEnabled) {
            exception.printStackTrace()
          }
          1
      }
    }
  }

  private def outputCsv(records: CollectedRecords): Unit = {
    // Print CSV header
    println(
      "record_type,scenario_name,group_hierarchy,request_name,status,start_timestamp,end_timestamp,response_time_ms,error_message,event_type,duration_ms,cumulated_response_time_ms,is_incoming"
    )

    // Output user records
    records.userRecords.foreach { userRecord =>
      val eventType = if (userRecord.event == MessageEvent.Start) "start" else "end"
      println(s"user,${escapeCsv(userRecord.scenario)},,,,${userRecord.timestamp},,,,$eventType,,,")
    }

    // Output request records
    records.requestRecords.foreach { requestRecord =>
      val groupHierarchy = requestRecord.group.map(_.hierarchy.mkString("|")).getOrElse("")
      val status = if (requestRecord.status == OK) "OK" else "KO"
      val errorMessage = requestRecord.errorMessage.getOrElse("")
      val isIncoming = requestRecord.incoming.toString
      val endTimestamp = if (requestRecord.incoming) "" else (requestRecord.start + requestRecord.responseTime).toString
      println(
        s"request,,${escapeCsv(groupHierarchy)},${escapeCsv(requestRecord.name)},$status,${requestRecord.start},$endTimestamp,${requestRecord.responseTime},${escapeCsv(errorMessage)},,,,$isIncoming"
      )
    }

    // Output group records
    records.groupRecords.foreach { groupRecord =>
      val groupHierarchy = groupRecord.group.hierarchy.mkString("|")
      val status = if (groupRecord.status == OK) "OK" else "KO"
      val endTimestamp = groupRecord.start + groupRecord.duration
      println(
        s"group,,${escapeCsv(groupHierarchy)},,$status,${groupRecord.start},$endTimestamp,,,,${groupRecord.duration},${groupRecord.cumulatedResponseTime},"
      )
    }

    // Output error records
    records.errorRecords.foreach { errorRecord =>
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

final case class CollectedRecords(
    userRecords: List[UserRecord],
    requestRecords: List[RequestRecord],
    groupRecords: List[GroupRecord],
    errorRecords: List[ErrorRecord]
)

private final class RecordCollectingParser(logFile: File, configuration: GatlingConfiguration, debugEnabled: Boolean) 
    extends SafeLogFileParser[CollectedRecords](logFile) {

  private val userRecords = mutable.ListBuffer[UserRecord]()
  private val requestRecords = mutable.ListBuffer[RequestRecord]()
  private val groupRecords = mutable.ListBuffer[GroupRecord]()
  private val errorRecords = mutable.ListBuffer[ErrorRecord]()
  
  private var runStart: Long = 0L
  private var scenarios: Array[String] = Array.empty

  private def debugLog(message: String): Unit = {
    if (debugEnabled) {
      System.err.println(s"[DEBUG] $message")
    }
  }

  private def parseRunRecord(): Unit = {
    val gatlingVersion = readString()
    debugLog(s"Log file was generated with Gatling $gatlingVersion")
    logger.info(s"Log file was generated with Gatling $gatlingVersion")

    val simulationClassName = readString()
    runStart = readLong()
    val runDescription = readString()

    scenarios = Array.fill(readInt())(readSanitizedString())

    // Skip assertions
    val assertionsSize = readInt()
    (0 until assertionsSize).foreach(_ => skip(readInt()))
  }

  private def parseUserRecord(): Unit = {
    val scenarioIndex = readInt()
    val event = if (readBoolean()) MessageEvent.Start else MessageEvent.End
    val timestamp = readInt() + runStart

    val scenario = if (scenarioIndex < scenarios.length) scenarios(scenarioIndex) else s"unknown_$scenarioIndex"
    val userRecord = UserRecord(scenario, event, timestamp)
    userRecords += userRecord
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
    requestRecords += requestRecord
  }

  private def parseGroupRecord(): Unit = {
    val groupsSize = readInt()
    val group = Group(List.fill(groupsSize)(readCachedSanitizedString()))
    val startTimestamp = readInt() + runStart
    val endTimestamp = readInt() + runStart
    val cumulatedResponseTime = readInt()
    val status = if (readBoolean()) OK else KO

    val groupRecord = GroupRecord(group, (endTimestamp - startTimestamp).toInt, cumulatedResponseTime, status, startTimestamp, 0)
    groupRecords += groupRecord
  }

  private def parseErrorRecord(): Unit = {
    val message = readCachedSanitizedString()
    val timestamp = readInt() + runStart
    val errorRecord = ErrorRecord(message, timestamp)
    errorRecords += errorRecord
  }

  override def parse(): CollectedRecords = {
    logger.info("Parsing log file for CSV export using Gatling's internal deserializer")

    readByte() match {
      case RecordHeader.Run.value => parseRunRecord()
      case _                      => throw new UnsupportedOperationException(s"The log file $logFile is malformed and doesn't start with a proper record")
    }

    var count = 1
    var continue = true
    while (continue) {
      count += 1
      if (count % 10000 == 0) {
        debugLog(s"Processed $count records")
        logger.info(s"Processed $count records")
      }
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
        case e: java.io.EOFException =>
          logger.error(s"Log file is truncated after record $count, can only generate partial results.", e)
          logger.debug(s"Records collected so far: ${userRecords.size + requestRecords.size + groupRecords.size + errorRecords.size}")
          continue = false
      }
    }

    debugLog(s"Parsing complete: processed $count records")
    logger.info(s"Parsing complete: processed $count records")
    CollectedRecords(userRecords.toList, requestRecords.toList, groupRecords.toList, errorRecords.toList)
  }
}