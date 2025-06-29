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
      case "--debug" :: "--scan-subdirs" :: logFile :: Nil => Left(LogParserArgs(logFile, debugEnabled = true, scanSubdirs = true))
      case "--scan-subdirs" :: "--debug" :: logFile :: Nil => Left(LogParserArgs(logFile, debugEnabled = true, scanSubdirs = true))
      case "--debug" :: logFile :: "--scan-subdirs" :: Nil => Left(LogParserArgs(logFile, debugEnabled = true, scanSubdirs = true))
      case "--scan-subdirs" :: logFile :: "--debug" :: Nil => Left(LogParserArgs(logFile, debugEnabled = true, scanSubdirs = true))
      case logFile :: "--debug" :: "--scan-subdirs" :: Nil => Left(LogParserArgs(logFile, debugEnabled = true, scanSubdirs = true))
      case logFile :: "--scan-subdirs" :: "--debug" :: Nil => Left(LogParserArgs(logFile, debugEnabled = true, scanSubdirs = true))
      case "--scan-subdirs" :: logFile :: Nil              => Left(LogParserArgs(logFile, debugEnabled = false, scanSubdirs = true))
      case logFile :: "--scan-subdirs" :: Nil              => Left(LogParserArgs(logFile, debugEnabled = false, scanSubdirs = true))
      case "--debug" :: logFile :: Nil                     => Left(LogParserArgs(logFile, debugEnabled = true, scanSubdirs = false))
      case logFile :: "--debug" :: Nil                     => Left(LogParserArgs(logFile, debugEnabled = true, scanSubdirs = false))
      case logFile :: Nil if !logFile.startsWith("-")      => Left(LogParserArgs(logFile, debugEnabled = false, scanSubdirs = false))
      case "--help" :: _                                   => printUsageAndExit()
      case "-h" :: _                                       => printUsageAndExit()
      case Nil                                             => printUsageAndExit()
      case _                                               => printUsageAndExit()
    }

  private def printUsageAndExit(): Either[LogParserArgs, StatusCode] = {
    println("Gatling Log Parser CLI")
    println()
    println("Usage: glog [--debug] [--scan-subdirs] <path>")
    println()
    println("Options:")
    println("  --debug         Enable debug logging output")
    println("  --scan-subdirs  Scan immediate subdirectories for simulation.log files")
    println("  --help          Show this help message")
    println()
    println("Arguments:")
    println("  path    Path to simulation.log file or directory to scan")
    println()
    Right(StatusCode.Success)
  }
}
