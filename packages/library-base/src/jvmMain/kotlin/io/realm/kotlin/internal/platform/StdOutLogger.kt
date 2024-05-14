/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm.kotlin.internal.platform

import io.realm.kotlin.log.LogCategory
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Logger implementation outputting to stdout.
 */
internal class StdOutLogger(
    private val tag: String,
) : RealmLogger {

    override fun log(
        category: LogCategory,
        level: LogLevel,
        throwable: Throwable?,
        message: String?,
        vararg args: Any?,
    ) {
        val logMessage: String = prepareLogMessage(throwable, message, *args)
        val timestamp: String = getTimestamp()
        println("$timestamp ${level.name}: [$tag] $logMessage")
    }

    /**
     * The `StdOutLogger` is only used on pure JVM, but is also included in our Android builds,
     * which means that the use of `DateTimeFormatter` trigger warnings as it is only available
     * from API 26+. Just suppress these warnings.
     *
     * We cannot use `DateFormatter` as it isn't thread-safe.
     */
    @Suppress("NewApi")
    private inline fun getTimestamp(): String {
        return TIMESTAMP_FORMATTER.format(Instant.now().atZone(ZoneId.systemDefault()))
    }

    private fun prepareLogMessage(
        throwable: Throwable?,
        message: String?,
        vararg args: Any?
    ): String {
        var messageToLog = message
        if (messageToLog.isNullOrEmpty()) {
            if (throwable == null) {
                return ""
            }
            messageToLog = getStackTraceString(throwable)
        } else {
            if (args.isNotEmpty()) {
                messageToLog = formatMessage(messageToLog, *args)
            }
            if (throwable != null) {
                messageToLog += "\n" + getStackTraceString(throwable)
            }
        }
        return messageToLog
    }

    private fun formatMessage(message: String, vararg args: Any?): String {
        return message.format(*args)
    }

    private fun getStackTraceString(t: Throwable): String {
        val sw = StringWriter(INITIAL_BUFFER_SIZE)
        val pw = PrintWriter(sw, false)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    companion object {
        const val INITIAL_BUFFER_SIZE = 256
        @Suppress("NewApi")
        val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MMM-dd hh:mm:ss,SSS")
    }
}
