package io.realm.log

import io.realm.RealmConfiguration
import io.realm.internal.interop.CoreLogLevel
import io.realm.log.LogLevel.TRACE
import io.realm.log.LogLevel.WTF

/**
 * Enum describing the log levels available to Realms internal logger.
 *
 * Each log entry is assigned a priority between [TRACE] and [WTF]. If the log level is equal or higher
 * than the priority defined in [io.realm.RealmConfiguration.Builder.logLevel] the event will be logged.
 *
 * @see RealmConfiguration.Builder.log
 */
@Suppress("MagicNumber")
enum class LogLevel(val priority: Int) {
    ALL(CoreLogLevel.RLM_LOG_LEVEL_ALL.value),
    TRACE(CoreLogLevel.RLM_LOG_LEVEL_TRACE.value),
    DEBUG(CoreLogLevel.RLM_LOG_LEVEL_DEBUG.value),
    DETAIL(CoreLogLevel.RLM_LOG_LEVEL_DETAIL.value),
    INFO(CoreLogLevel.RLM_LOG_LEVEL_INFO.value),
    WARN(CoreLogLevel.RLM_LOG_LEVEL_WARNING.value),
    ERROR(CoreLogLevel.RLM_LOG_LEVEL_ERROR.value),
    WTF(CoreLogLevel.RLM_LOG_LEVEL_FATAL.value),
    NONE(CoreLogLevel.RLM_LOG_LEVEL_OFF.value)
}
