package io.realm.log

import io.realm.RealmConfiguration
import io.realm.internal.interop.CoreLogLevel
import io.realm.log.LogLevel.TRACE
import io.realm.log.LogLevel.WTF

/**
 * Enum describing the log levels available to Realms internal logger.
 *
 * Each log entry is assigned a priority between [TRACE] and [WTF]. If the log level is equal or
 * higher than the priority defined in [io.realm.RealmConfiguration.Builder.logLevel] the event will
 * be logged.
 *
 * @see RealmConfiguration.Builder.log
 */
enum class LogLevel(val priority: Int) {
    ALL(0),
    TRACE(1),
    DEBUG(2),
    INFO(3),
    WARN(4),
    ERROR(5),
    WTF(6),
    NONE(7);

    companion object {
        /**
         * Converts a Core log level value to a library log level value. Values that represent the
         * same level from the library perspective are folded together.
         *
         * For internal use only.
         */
        fun fromCoreLogLevel(coreLogLevel: CoreLogLevel): LogLevel = when (coreLogLevel) {
            CoreLogLevel.RLM_LOG_LEVEL_ALL,
            CoreLogLevel.RLM_LOG_LEVEL_TRACE -> TRACE
            CoreLogLevel.RLM_LOG_LEVEL_DEBUG -> DEBUG
            CoreLogLevel.RLM_LOG_LEVEL_DETAIL,
            CoreLogLevel.RLM_LOG_LEVEL_INFO -> INFO
            CoreLogLevel.RLM_LOG_LEVEL_WARNING -> WARN
            CoreLogLevel.RLM_LOG_LEVEL_ERROR -> ERROR
            CoreLogLevel.RLM_LOG_LEVEL_FATAL -> WTF
            CoreLogLevel.RLM_LOG_LEVEL_OFF -> NONE
            else -> throw IllegalArgumentException("Invalid core log level: $coreLogLevel")
        }
    }
}
