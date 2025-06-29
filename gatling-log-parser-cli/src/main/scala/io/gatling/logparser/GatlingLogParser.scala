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

import io.gatling.charts.stats._
import io.gatling.commons.stats.{ KO, OK }
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.stats.message.MessageEvent
import io.gatling.logparser.cli.{ LogParserArgs, LogParserArgsParser }

import com.typesafe.scalalogging.StrictLogging

object GatlingLogParser extends StrictLogging {

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
    logger.info(s"Looking for log file at: ${logFile.getAbsolutePath}")

    if (!logFile.exists()) {
      System.err.println(s"File not found: ${args.logFilePath}")
      System.err.println(s"Absolute path: ${logFile.getAbsolutePath}")
      1
    } else {
      try {
        io.gatling.core.stats.writer.StringInternals.checkAvailability() // Ensure method handle is initialized
      } catch {
        case e: IllegalAccessException =>
          logger.warn("Could not initialize StringInternals due to module access restrictions. Continuing anyway.", e)
      }
      val configuration = GatlingConfiguration.loadForTest()
      val logFileReader = new LogFileReader(logFile, configuration)
      val records = logFileReader.parseRaw()
      outputCsv(records)
      0
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
