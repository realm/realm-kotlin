package io.realm.kotlin.log

import io.realm.kotlin.internal.interop.CoreLogLevel
import io.realm.kotlin.log.LogLevel.TRACE
import io.realm.kotlin.log.LogLevel.WTF

/**
 * Enum describing the log levels available to the Realm logger.
 *
 * Each log entry is assigned a priority between [TRACE] and [WTF]. If the log level is equal or
 * higher than the priority defined in [RealmLog.level] the event will be logged.
 */
@Suppress("MagicNumber")
public enum class LogLevel(public val priority: Int) {
    ALL(0),
    TRACE(1),
    DEBUG(2),
    INFO(3),
    WARN(4),
    ERROR(5),
    WTF(6),
    NONE(7);
}

internal fun LogLevel.toCoreLogLevel(): CoreLogLevel {
    return when (this) {
        LogLevel.ALL -> CoreLogLevel.RLM_LOG_LEVEL_ALL
        LogLevel.TRACE -> CoreLogLevel.RLM_LOG_LEVEL_TRACE
        LogLevel.DEBUG -> CoreLogLevel.RLM_LOG_LEVEL_DEBUG
        LogLevel.INFO -> CoreLogLevel.RLM_LOG_LEVEL_INFO
        LogLevel.WARN -> CoreLogLevel.RLM_LOG_LEVEL_WARNING
        LogLevel.ERROR -> CoreLogLevel.RLM_LOG_LEVEL_ERROR
        LogLevel.WTF -> CoreLogLevel.RLM_LOG_LEVEL_FATAL
        LogLevel.NONE -> CoreLogLevel.RLM_LOG_LEVEL_OFF
    }
}

internal fun CoreLogLevel.fromCoreLogLevel(): LogLevel {
    return when (this) {
        CoreLogLevel.RLM_LOG_LEVEL_ALL,
        CoreLogLevel.RLM_LOG_LEVEL_TRACE -> LogLevel.TRACE
        CoreLogLevel.RLM_LOG_LEVEL_DEBUG,
        CoreLogLevel.RLM_LOG_LEVEL_DETAIL -> LogLevel.DEBUG
        CoreLogLevel.RLM_LOG_LEVEL_INFO -> LogLevel.INFO
        CoreLogLevel.RLM_LOG_LEVEL_WARNING -> LogLevel.WARN
        CoreLogLevel.RLM_LOG_LEVEL_ERROR -> LogLevel.ERROR
        CoreLogLevel.RLM_LOG_LEVEL_FATAL -> LogLevel.WTF
        CoreLogLevel.RLM_LOG_LEVEL_OFF -> LogLevel.NONE
        else -> throw IllegalArgumentException("Invalid core log level: $this")
    }
}
