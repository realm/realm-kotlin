package io.realm.internal

import io.realm.log.LogLevel
import io.realm.log.RealmLogger
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Logger implementation outputting to stdout.
 */
internal class StdOutLogger(override val tag: String = "REALM") : RealmLogger {

    override fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?) {
        val logMessage: String = prepareLogMessage(throwable, message, *args)
        println("${level.name}: [$tag] $logMessage")
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
        val sw = StringWriter(Companion.INITIAL_BUFFER_SIZE)
        val pw = PrintWriter(sw, false)
        t.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    companion object {
        const val INITIAL_BUFFER_SIZE = 256
    }
}
