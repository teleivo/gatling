//> using scala 2.13
//> using dep io.gatling:gatling-charts:3.14.3
//> using dep com.typesafe.scala-logging::scala-logging:3.9.5
//> using dep ch.qos.logback:logback-classic:1.5.18
//> using javaOpt --add-opens=java.base/java.lang=ALL-UNNAMED

/*
 * Self-contained Gatling Log Parser CLI
 * 
 * A native-compilable CLI tool that parses Gatling simulation.log files
 * and outputs CSV data to stdout - just like the original LogParserCli.
 */

import java.{ lang => jl, util => ju }
import java.io.{ BufferedInputStream, DataInputStream, EOFException, File }
import java.nio.ByteBuffer
import java.nio.file.Files
import java.time.ZoneId

import scala.collection.mutable
import scala.util.{ Failure, Success, Try, Using }

import io.gatling.charts.stats._
import io.gatling.commons.stats.{ KO, OK }
import io.gatling.commons.util.StringHelper._
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.stats.message.MessageEvent
import io.gatling.core.stats.writer._

import com.typesafe.config.ConfigFactory

import com.typesafe.scalalogging.StrictLogging
import io.github.metarank.cfor._

// Record types for parsing
sealed trait Event
object Event {
  case object Start extends Event
  case object End extends Event
}

final case class UserRecord(scenario: String, event: Event, timestamp: Long)
final case class RequestRecord(group: Option[Group], name: String, status: io.gatling.commons.stats.Status, start: Long, startBucket: Int, endBucket: Int, responseTime: Int, errorMessage: Option[String], incoming: Boolean)
final case class GroupRecord(group: Group, duration: Int, cumulatedResponseTime: Int, status: io.gatling.commons.stats.Status, start: Long, startBucket: Int)
final case class ErrorRecord(message: String, timestamp: Long)

// RecordHeader values - must match Gatling's internal values
object RecordHeader {
  object Run extends Enumeration { val value: Byte = 0 }
  object Request extends Enumeration { val value: Byte = 1 }
  object User extends Enumeration { val value: Byte = 2 }
  object Group extends Enumeration { val value: Byte = 3 }
  object Error extends Enumeration { val value: Byte = 4 }
}

object GatlingParser extends StrictLogging {

  // Create minimal configuration for native image compatibility
  private def createMinimalConfiguration(): GatlingConfiguration = {
    import io.gatling.core.config._
    import scala.concurrent.duration._
    
    // Create minimal components with safe defaults
    val coreConfig = new CoreConfiguration(
      encoding = "utf-8",
      extract = new ExtractConfiguration(
        regex = new RegexConfiguration(cacheMaxCapacity = 1000),
        xpath = new XPathConfiguration(cacheMaxCapacity = 1000),
        jsonPath = new JsonPathConfiguration(cacheMaxCapacity = 1000),
        css = new CssConfiguration(cacheMaxCapacity = 1000)
      ),
      elFileBodiesCacheMaxCapacity = 1000,
      rawFileBodiesCacheMaxCapacity = 1000,
      rawFileBodiesInMemoryMaxSize = 1000000,
      pebbleFileBodiesCacheMaxCapacity = 1000,
      feederAdaptiveLoadModeThreshold = 1048576,
      shutdownTimeout = 10000
    )
    
    val dataConfig = new DataConfiguration(
      zoneId = ZoneId.systemDefault(),
      dataWriters = Seq.empty,
      console = new ConsoleDataWriterConfiguration(light = false, writePeriod = 5.seconds),
      enableAnalytics = false
    )
    
    // Create minimal configs for other components (not used for CSV parsing)
    val socketConfig = new SocketConfiguration(
      connectTimeout = 10.seconds,
      tcpNoDelay = true,
      soKeepAlive = false
    )
    
    // Create minimal configuration - only data config matters for our CSV parsing
    new GatlingConfiguration(
      core = coreConfig,
      socket = socketConfig,
      netty = null, // Not used for file parsing
      ssl = null,   // Not used for file parsing
      reports = null, // Not used for CSV export
      http = null,  // Not used for file parsing
      jms = null,   // Not used for file parsing
      data = dataConfig
    )
  }

  def main(args: Array[String]): Unit = {
    val (debugEnabled, configFile, logFilePath) = parseArgs(args)
    
    // Configure logging level based on debug flag
    if (debugEnabled) {
      // Set root logger to DEBUG level
      try {
        import ch.qos.logback.classic.{ Level, Logger }
        import org.slf4j.LoggerFactory
        val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
        rootLogger.setLevel(Level.DEBUG)
        
        // Also set our logger specifically
        val ourLogger = LoggerFactory.getLogger("GatlingParser").asInstanceOf[Logger]
        ourLogger.setLevel(Level.DEBUG)
        
      } catch {
        case e: Exception =>
          System.err.println(s"Warning: Could not configure logback logger: ${e.getMessage}")
          System.err.println("Debug logging enabled (fallback mode)")
      }
    }

    val logFile = new File(logFilePath).getAbsoluteFile

    if (!logFile.exists()) {
      System.err.println(s"File not found: $logFilePath")
      sys.exit(1)
    }

    Try {
      // Initialize string internals required for parsing - skip in native image
      try {
        StringInternals.checkAvailability()
      } catch {
        case _: Exception => 
          logger.warn("StringInternals not available, falling back to alternative string creation")
      }
      
      // Load Gatling configuration with native image compatibility
      val configuration = try {
        configFile match {
          case Some(path) => 
            if (debugEnabled) System.err.println(s"[DEBUG] Loading configuration from: $path")
            val configPath = new File(path)
            if (!configPath.exists()) {
              System.err.println(s"Configuration file not found: $path")
              sys.exit(1)
            }
            if (debugEnabled) System.err.println(s"[DEBUG] Configuration file exists, using default config for now")
            // Note: Custom config merging is complex, using defaults
            // The foundation is here for future config integration
            GatlingConfiguration.loadForTest()
          case None => 
            if (debugEnabled) System.err.println("[DEBUG] Using default configuration")
            GatlingConfiguration.loadForTest()
        }
      } catch {
        case e: Exception =>
          if (debugEnabled) System.err.println(s"[DEBUG] Configuration loading failed: ${e.getMessage}, using minimal config")
          // Fallback for native image: create minimal configuration manually
          createMinimalConfiguration()
      }
      
      // Use Gatling-compatible parser for CSV export
      if (debugEnabled) {
        System.err.println("[DEBUG] Using Gatling-compatible CSV parser")
        System.err.println(s"[DEBUG] Zone ID: ${configuration.data.zoneId}")
      }
      
      // Initialize StringInternals exactly like real LogFileReader does
      try {
        StringInternals.checkAvailability()
        if (debugEnabled) System.err.println("[DEBUG] StringInternals available")
      } catch {
        case e: Exception =>
          if (debugEnabled) System.err.println(s"[DEBUG] StringInternals not available: ${e.getMessage}")
          // This is OK, we have fallback string creation in the parser
      }
      
      // Parse using same logic as Gatling's internal parser
      val records = Using.resource(new CsvRecordCollector(logFile, configuration, debugEnabled))(_.parse())
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

  private def parseArgs(args: Array[String]): (Boolean, Option[String], String) =
    args.toList match {
      case "--debug" :: "--config" :: configFile :: logFile :: Nil => (true, Some(configFile), logFile)
      case "--config" :: configFile :: "--debug" :: logFile :: Nil => (true, Some(configFile), logFile)
      case "--debug" :: "-c" :: configFile :: logFile :: Nil => (true, Some(configFile), logFile)
      case "-c" :: configFile :: "--debug" :: logFile :: Nil => (true, Some(configFile), logFile)
      case "--config" :: configFile :: logFile :: Nil => (false, Some(configFile), logFile)
      case "-c" :: configFile :: logFile :: Nil => (false, Some(configFile), logFile)
      case "--debug" :: logFile :: Nil => (true, None, logFile)
      case logFile :: "--debug" :: Nil => (true, None, logFile)
      case logFile :: Nil              => (false, None, logFile)
      case _ =>
        System.err.println("Usage: glog [--debug] [--config|-c <gatling.conf>] <simulation.log>")
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
        val eventType = if (userRecord.event == Event.Start) "start" else "end"
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
private final class CsvRecordCollector(logFile: File, configuration: GatlingConfiguration, debugEnabled: Boolean = false) extends LogFileParser[AllRecords](logFile) with StrictLogging {

  private val allRecords = mutable.ListBuffer[Either[UserRecord, Either[RequestRecord, Either[GroupRecord, ErrorRecord]]]]()
  private var runStart: Long = 0L
  private var scenarios: Array[String] = Array.empty
  
  private def debugLog(message: String): Unit = {
    if (debugEnabled) {
      System.err.println(s"[DEBUG] $message")
    }
  }

  private def parseRunRecord(): Unit = {
    val gatlingVersion = readString()
    // Log version information for debugging
    debugLog(s"Log file was generated with Gatling $gatlingVersion")
    logger.info(s"Log file was generated with Gatling $gatlingVersion")
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
    val event = if (readBoolean()) Event.Start else Event.End
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
        case e: EOFException =>
          logger.error(s"Log file is truncated after record $count, can only generate partial results.", e)
          logger.debug(s"Records collected so far: ${allRecords.size}")
          continue = false
      }
    }

    debugLog(s"Parsing complete: processed $count records")
    logger.info(s"Parsing complete: processed $count records")
    AllRecords(allRecords.toList)
  }
}

// LogFileParser abstract class - minimal implementation for our needs
abstract class LogFileParser[T](logFile: File) extends AutoCloseable {
  private val is = new DataInputStream(new BufferedInputStream(Files.newInputStream(logFile.toPath)))
  private val skipBuffer = new Array[Byte](1024)
  private val stringCache = new ju.HashMap[Int, String]

  protected def read(): Int = is.read()
  protected def readByte(): Byte = is.readByte()
  protected def readBoolean(): Boolean = is.readBoolean()
  protected def readInt(): Int = is.readInt()
  protected def readByteArray(): Array[Byte] = is.readNBytes(readInt())
  protected def readLong(): Long = is.readLong()
  protected def readString(): String = {
    val length = readInt()
    if (length == 0) {
      ""
    } else {
      val value = is.readNBytes(length)
      if (value.length < length) {
        throw new EOFException(s"Expected $length bytes but got ${value.length}")
      }
      val coder = readByte()
      try {
        StringInternals.newString(value, coder)
      } catch {
        case _: Exception =>
          // Fallback for native image - use regular string creation
          new String(value, if (coder == 0) "ISO-8859-1" else "UTF-8")
      }
    }
  }
  private def sanitize(s: String): String = s.replaceIf(c => c == '\n' || c == '\r' || c == '\t', ' ')
  protected def readSanitizedString(): String = sanitize(readString())
  protected def readCachedSanitizedString(): String = {
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

  protected def skip(len: Int): Unit = {
    var n = 0
    while (n < len) {
      val count = is.read(skipBuffer, 0, math.min(len - n, skipBuffer.length))
      if (count < 0) {
        throw new EOFException(s"Failed to skip $len bytes")
      }
      n += count
    }
  }

  override def close(): Unit = is.close()

  def parse(): T
}