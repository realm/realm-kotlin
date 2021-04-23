package io.realm.internal

import io.realm.log.LogLevel
import io.realm.log.RealmLogger
import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.stringWithFormat

/**
 * Logger implementation outputting to NSLog.
 *
 * Inspiration from: https://github.com/touchlab/Kermit/blob/master/kermit/src/darwinMain/kotlin/co/touchlab/kermit/NSLogLogger.kt
 */
internal class NSLogLogger(override val tag: String = "REALM") : RealmLogger {

    override fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?) {
        val logMessage: String = prepareLogMessage(throwable, message, *args)
        NSLog("%s: [%s] %s", level.name, tag, logMessage)
    }

    private fun prepareLogMessage(throwable: Throwable?, message: String?, vararg args: Any?): String {
        var message = message
        if (message.isNullOrEmpty()) {
            if (throwable == null) {
                return ""
            }
            message = dumpStackTrace(throwable)
        } else {
            if (args.isNotEmpty()) {
                message = formatMessage(message, args)
            }
            if (throwable != null) {
                message += "\n" + dumpStackTrace(throwable)
            }
        }
        return message
    }

    private fun formatMessage(message: String, args: Array<out Any?>): String {
        // Formatting a string with varargs is not supported in Kotlin Multiplatform right now,
        // so we attempt to work around with a best effort.
        // See https://youtrack.jetbrains.com/issue/KT-25506
        // See https://stackoverflow.com/questions/64495182/kotlin-native-ios-string-formatting-with-vararg/64499248#64499248
        var formattedMessage = ""
        val regEx = "%[\\d|.]*[sdf]|[%]".toRegex()
        val singleFormats = regEx.findAll(message).map {
            it.groupValues.first()
        }.asSequence().toList()
        val newStrings = message.split(regEx)
        for (i in 0 until args.count()) {
            val arg = args[i]
            formattedMessage += when (arg) {
                is Double -> {
                    NSString.stringWithFormat(newStrings[i] + singleFormats[i], args[i] as Double)
                }
                is Int -> {
                    NSString.stringWithFormat(newStrings[i] + singleFormats[i], args[i] as Int)
                }
                else -> {
                    NSString.stringWithFormat(newStrings[i] + "%@", args[i])
                }
            }
        }

        // args.count() + 1 == newStrings.size is only true if the string contain content after the last placeholder.
        // This also mark the end of the String.
        if (args.count() + 1 == newStrings.size) {
            formattedMessage += newStrings.last()
        }

        return formattedMessage
    }

    // FIXME `throwable.stackTraceToString()` have a memory leak. See https://youtrack.jetbrains.com/issue/KT-46291.
    //  So use a slimmed down version until it has been fixed.
    private inline fun dumpStackTrace(throwable: Throwable): String = throwable.toString()
}
