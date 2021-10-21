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
package io.realm.internal.platform

import android.util.Log
import io.realm.log.LogLevel
import io.realm.log.RealmLogger
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale

/**
 * Create a logger that outputs to Android LogCat.
 *
 * Credit to https://github.com/JakeWharton/timber/blob/master/timber/src/main/java/timber/log/Timber.kt
 * for message creation and formatting
 */
internal class LogCatLogger(
    override val tag: String = "REALM",
    override val level: LogLevel
) : RealmLogger {

    override fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?) {
        val priority: Int = level.priority
        val logMessage: String = prepareLogMessage(throwable, message, *args)

        // Short circuit if message can fit into a single line in LogCat
        if (logMessage.length < MAX_LOG_LENGTH) {
            printMessage(priority, logMessage)
            return
        }

        // Split by line, then ensure each line can fit into Log's maximum length.
        var i = 0
        val length = logMessage.length
        while (i < length) {
            var newline = logMessage.indexOf('\n', i)
            newline = if (newline != -1) newline else length
            do {
                val end = Math.min(newline, i + MAX_LOG_LENGTH)
                val part = logMessage.substring(i, end)
                printMessage(priority, part)
                i = end
            } while (i < newline)
            i++
        }
    }

    private fun printMessage(priority: Int, logMessage: String) {
        if (priority == LogLevel.WTF.priority) {
            Log.wtf(tag, logMessage)
        } else if (priority < LogLevel.DEBUG.priority) {
            // ALL (0) and TRACE (1) don't exist on Android's Log so use VERBOSE instead
            Log.v(tag, logMessage)
        } else if (priority == LogLevel.DEBUG.priority) {
            // Core's DEBUG (2) doesn't match Android's DEBUG (3) so match it manually
            Log.d(tag, logMessage)
        } else {
            Log.println(priority, tag, logMessage)
        }
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
        return message.format(Locale.US, *args)
    }

    private fun getStackTraceString(t: Throwable): String {
        // Don't replace this with Log.getStackTraceString() - it hides
        // UnknownHostException, which is not what we want.
        val sw = StringWriter(INITIAL_BUFFER_SIZE)
        val pw = PrintWriter(sw, false)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    companion object {
        private const val MAX_TAG_LENGTH = 23 // This limit was removed in API 24
        private const val MAX_LOG_LENGTH = 4000
        private const val INITIAL_BUFFER_SIZE = 256
    }
}
