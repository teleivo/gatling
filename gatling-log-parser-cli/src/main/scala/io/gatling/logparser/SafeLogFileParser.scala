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

import java.{ util => ju }
import java.io.{ BufferedInputStream, DataInputStream, EOFException, File }
import java.nio.file.Files

import io.gatling.commons.util.StringHelper._
import io.gatling.core.stats.writer.StringInternals

import com.typesafe.scalalogging.StrictLogging

abstract class SafeLogFileParser[T](logFile: File) extends AutoCloseable with StrictLogging {
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
          // Fallback for environments where StringInternals is not available
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