package io.realm.log

import io.realm.RealmConfiguration
import io.realm.internal.interop.CoreLogLevel
import io.realm.internal.interop.CoreLogger

/**
 * Interface describing a logger implementation.
 *
 * @see RealmConfiguration.Builder.log
 */
interface RealmLogger : CoreLogger {

    /**
     * Tag that can be used to describe the output.
     */
    val tag: String

    /**
     * TODO
     */
    val level: LogLevel

    /**
     * Log an event.
     */
    fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?)

    override fun log(level: Short, message: String) {
        log(level.toLogLevel(), null, message, null)
    }

    private fun Short.toLogLevel(): LogLevel {
        return when (this.toInt()) {
            LogLevel.ALL.priority -> LogLevel.ALL
            LogLevel.TRACE.priority -> LogLevel.TRACE
            LogLevel.DEBUG.priority -> LogLevel.DEBUG
            LogLevel.INFO.priority -> LogLevel.INFO
            LogLevel.WARN.priority -> LogLevel.WARN
            LogLevel.ERROR.priority -> LogLevel.ERROR
            LogLevel.WTF.priority -> LogLevel.WTF
            LogLevel.NONE.priority -> LogLevel.NONE
            else -> throw IllegalArgumentException("Invalid priority level: $this.")
        }
    }
}
