package io.realm.internal

import android.util.Log
import io.realm.log.LogLevel
import io.realm.log.RealmLogger
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Create a logger that outputs to Android LogCat.
 *
 * Credit to https://github.com/JakeWharton/timber/blob/master/timber/src/main/java/timber/log/Timber.kt
 * for message creation and formatting
 */
internal class LogCatLogger(override val tag: String = "REALM") : RealmLogger {
    companion object {
        private const val MAX_TAG_LENGTH = 23 // This limit was removed in API 24
        private const val MAX_LOG_LENGTH = 4000
    }

    override fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?) {
        val priority: Int = level.priority
        val logMessage: String = prepareLogMessage(throwable, message, args)

        // Short circuit if message can fit into a single line in LogCat
        if (logMessage.length < MAX_LOG_LENGTH) {
            if (level.priority == LogLevel.WTF.priority) {
                Log.wtf(tag, logMessage)
            } else {
                Log.println(priority, tag, logMessage)
            }
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
                if (priority == Log.ASSERT) {
                    Log.wtf(tag, part)
                } else {
                    Log.println(priority, tag, part)
                }
                i = end
            } while (i < newline)
            i++
        }
    }

    private fun prepareLogMessage(throwable: Throwable?, message: String?, vararg args: Any?): String {
        var message = message
        if (message.isNullOrEmpty()) {
            if (throwable == null) {
                return ""
            }
            message = getStackTraceString(throwable)
        } else {
            if (args.isNotEmpty()) {
                message = formatMessage(message, args)
            }
            if (throwable != null) {
                message += "\n" + getStackTraceString(throwable)
            }
        }
        return message
    }

    private fun formatMessage(message: String, vararg args: Any?): String {
        return message.format(args)
    }

    private fun getStackTraceString(t: Throwable): String {
        // Don't replace this with Log.getStackTraceString() - it hides
        // UnknownHostException, which is not what we want.
        @Suppress("MagicNumber")
        val sw = StringWriter(256)
        val pw = PrintWriter(sw, false)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
}
