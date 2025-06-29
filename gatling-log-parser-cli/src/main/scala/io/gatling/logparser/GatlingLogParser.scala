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
import io.gatling.core.config.GatlingConfiguration
import io.gatling.logparser.cli.{LogParserArgs, LogParserArgsParser}
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

    if (!logFile.exists()) {
      System.err.println(s"File not found: ${args.logFilePath}")
      1
    } else {
      io.gatling.core.stats.writer.StringInternals.checkAvailability() // Ensure method handle is initialized
      val configuration = GatlingConfiguration.loadForTest()
      new LogFileReader(logFile, configuration).read()
      0
    }
  }
}
