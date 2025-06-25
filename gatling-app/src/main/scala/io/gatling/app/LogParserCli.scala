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

import java.io.File
import java.nio.file.Paths

import scala.util.{ Failure, Success, Try }

import io.gatling.charts.stats._
import io.gatling.core.config.GatlingConfiguration

import com.typesafe.scalalogging.StrictLogging

object LogParserCli extends StrictLogging {

  def main(args: Array[String]): Unit = {
    if (args.length != 1) {
      System.err.println("Usage: LogParserCli <simulation.log>")
      sys.exit(1)
    }

    val logFilePath = args(0)
    val logFile = new File(logFilePath)

    if (!logFile.exists()) {
      System.err.println(s"File not found: $logFilePath")
      sys.exit(1)
    }

    Try {
      val configuration = GatlingConfiguration.load()
      val parser = new CustomLogFileReader(logFile, configuration)
      parser.parseAndOutputCsv()
    } match {
      case Success(_) => // Success, CSV written to stdout
      case Failure(exception) =>
        System.err.println(s"Error parsing log file: ${exception.getMessage}")
        logger.error("Parsing failed", exception)
        sys.exit(1)
    }
  }
}

class CustomLogFileReader(logFile: File, configuration: GatlingConfiguration) extends StrictLogging {

  def parseAndOutputCsv(): Unit = {
    // Print CSV header
    println("record_type,scenario_name,group_hierarchy,request_name,status,start_timestamp,end_timestamp,response_time_ms,error_message,event_type,duration_ms,cumulated_response_time_ms,is_incoming")

    // Create a custom parser that outputs CSV as it reads
    val parser = new CsvOutputParser(logFile, configuration)
    parser.parse()
  }
}

private class CsvOutputParser(logFile: File, configuration: GatlingConfiguration) extends StrictLogging {
  import java.{ lang => jl, util => ju }
  import java.io.{ BufferedInputStream, DataInputStream, EOFException }
  import java.nio.ByteBuffer
  import java.nio.file.Files
  import java.time.ZoneId

  import io.gatling.commons.stats.{ KO, OK }
  import io.gatling.commons.util.GatlingVersion
  import io.gatling.commons.util.StringHelper._
  import io.gatling.core.stats.message.MessageEvent
  import io.gatling.core.stats.writer._

  private val is = new DataInputStream(new BufferedInputStream(Files.newInputStream(logFile.toPath)))
  private val skipBuffer = new Array[Byte](1024)
  private val stringCache = new ju.HashMap[Int, String]
  private var runStart: Long = 0L
  private var scenarios: Array[String] = Array.empty

  private def read(): Int = is.read()
  private def readByte(): Byte = is.readByte()
  private def readBoolean(): Boolean = is.readBoolean()
  private def readInt(): Int = is.readInt()
  private def readByteArray(): Array[Byte] = is.readNBytes(readInt())
  private def readLong(): Long = is.readLong()
  private def readString(): String = {
    val length = readInt()
    if (length == 0) {
      ""
    } else {
      val value = is.readNBytes(length)
      val coder = readByte()
      StringInternals.newString(value, coder)
    }
  }
  private def sanitize(s: String): String = s.replaceIf(c => c == '\n' || c == '\r' || c == '\t', ' ')
  private def readSanitizedString(): String = sanitize(readString())
  private def readCachedSanitizedString(): String = {
    val cachedIndex = readInt()
    if (cachedIndex >= 0) {
      val string = sanitize(readString())
      stringCache.put(cachedIndex, string)
      string
    } else {
      val cachedString = stringCache.get(-cachedIndex)
      assert(cachedString != null, s"Cached string missing for ${-cachedIndex} index")
      cachedString
    }
  }

  private def skip(len: Int): Unit = {
    var n = 0
    while (n < len) {
      val count = is.read(skipBuffer, 0, math.min(len - n, skipBuffer.length))
      if (count < 0) {
        throw new EOFException(s"Failed to skip $len bytes")
      }
      n += count
    }
  }
  private def skipByte(): Unit = skip(jl.Byte.BYTES)
  private def skipInt(): Unit = skip(jl.Integer.BYTES)
  private def skipLong(): Unit = skip(jl.Long.BYTES)
  private def skipString(): Unit = {
    val length = readInt()
    if (length > 0) {
      skip(length + 1)
    }
  }
  private def skipCachedString(): Unit =
    if (readInt() >= 0) {
      skipString()
    }

  private def escapeCsv(value: String): String = {
    if (value.contains("\"") || value.contains(",") || value.contains("\n")) {
      "\"" + value.replace("\"", "\"\"") + "\""
    } else {
      value
    }
  }

  private def parseRunRecord(): Unit = {
    val gatlingVersion = readString()
    // Skip version check to allow parsing logs from different versions
    logger.debug(s"Log file was generated with Gatling $gatlingVersion, parsing with ${GatlingVersion.ThisVersion.fullVersion}")

    val simulationClassName = readString()
    runStart = readLong()
    val runDescription = readString()

    scenarios = Array.fill(readInt())(readSanitizedString())

    val assertionsSize = readInt()
    (0 until assertionsSize).foreach(_ => skip(readInt()))
  }

  private def parseUserRecord(): Unit = {
    val scenarioIndex = readInt()
    val event = if (readBoolean()) MessageEvent.Start else MessageEvent.End
    val timestamp = readInt() + runStart

    val scenarioName = if (scenarioIndex < scenarios.length) scenarios(scenarioIndex) else s"unknown_$scenarioIndex"
    val eventType = if (event == MessageEvent.Start) "start" else "end"

    println(s"user,${escapeCsv(scenarioName)},,,,${timestamp},,,,${eventType},,,")
  }

  private def parseRequestRecord(): Unit = {
    val groupsSize = readInt()
    val groupHierarchy = if (groupsSize > 0) {
      (0 until groupsSize).map(_ => readCachedSanitizedString()).mkString("|")
    } else {
      ""
    }
    val name = readCachedSanitizedString()
    val startTimestamp = readInt() + runStart
    val endTimestamp = readInt() + runStart
    val status = if (readBoolean()) "OK" else "KO"
    val errorMessage = readCachedSanitizedString().trimToOption.getOrElse("")

    val responseTime = if (endTimestamp != Long.MinValue) {
      (endTimestamp - startTimestamp).toString
    } else {
      "0"
    }
    val isIncoming = if (endTimestamp == Long.MinValue) "true" else "false"

    println(s"request,,${escapeCsv(groupHierarchy)},${escapeCsv(name)},${status},${startTimestamp},${endTimestamp},${responseTime},${escapeCsv(errorMessage)},,,,${isIncoming}")
  }

  private def parseGroupRecord(): Unit = {
    val groupsSize = readInt()
    val groupHierarchy = (0 until groupsSize).map(_ => readCachedSanitizedString()).mkString("|")
    val startTimestamp = readInt() + runStart
    val endTimestamp = readInt() + runStart
    val cumulatedResponseTime = readInt()
    val status = if (readBoolean()) "OK" else "KO"

    val duration = (endTimestamp - startTimestamp).toInt

    println(s"group,,${escapeCsv(groupHierarchy)},,${status},${startTimestamp},${endTimestamp},,,,${duration},${cumulatedResponseTime},")
  }

  private def parseErrorRecord(): Unit = {
    val message = readCachedSanitizedString()
    val timestamp = readInt() + runStart

    println(s"error,,,,,${timestamp},,${escapeCsv(message)},,,,")
  }

  def parse(): Unit = {
    try {
      readByte() match {
        case RecordHeader.Run.value => parseRunRecord()
        case _ => throw new UnsupportedOperationException(s"The log file $logFile is malformed and doesn't start with a proper record")
      }

      var continue = true
      while (continue) {
        val headerValue = read().toByte
        try {
          headerValue match {
            case RecordHeader.User.value    => parseUserRecord()
            case RecordHeader.Request.value => parseRequestRecord()
            case RecordHeader.Group.value   => parseGroupRecord()
            case RecordHeader.Error.value   => parseErrorRecord()
            case -1                         => continue = false
            case _                          => throw new UnsupportedOperationException(s"Unsupported header $headerValue")
          }
        } catch {
          case e: EOFException =>
            logger.error("Log file is truncated, can only generate partial results.", e)
            continue = false
        }
      }
    } finally {
      is.close()
    }
  }
}