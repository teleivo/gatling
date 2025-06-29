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

package io.gatling.logparser.cli

import io.gatling.app.cli.StatusCode

final class LogParserArgsParser(args: Array[String]) {

  def parseArguments: Either[LogParserArgs, StatusCode] =
    args.toList match {
      case "--debug" :: logFile :: Nil                => Left(LogParserArgs(logFile, debugEnabled = true))
      case logFile :: "--debug" :: Nil                => Left(LogParserArgs(logFile, debugEnabled = true))
      case logFile :: Nil if !logFile.startsWith("-") => Left(LogParserArgs(logFile, debugEnabled = false))
      case "--help" :: _                              => printUsageAndExit()
      case "-h" :: _                                  => printUsageAndExit()
      case Nil                                        => printUsageAndExit()
      case _                                          => printUsageAndExit()
    }

  private def printUsageAndExit(): Either[LogParserArgs, StatusCode] = {
    println("Gatling Log Parser CLI")
    println()
    println("Usage: glog [--debug] <simulation.log>")
    println()
    println("Options:")
    println("  --debug    Enable debug logging output")
    println("  --help     Show this help message")
    println()
    println("Arguments:")
    println("  simulation.log    Path to the Gatling binary simulation log file")
    println()
    Right(StatusCode.Success)
  }
}
