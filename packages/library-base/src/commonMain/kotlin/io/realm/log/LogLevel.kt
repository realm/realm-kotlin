package io.realm.log

import io.realm.RealmConfiguration
import io.realm.log.LogLevel.DEBUG
import io.realm.log.LogLevel.ERROR
import io.realm.log.LogLevel.INFO
import io.realm.log.LogLevel.TRACE
import io.realm.log.LogLevel.WARN
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
    INFO(4),
    WARN(5),
    ERROR(6),
    WTF(7),
    NONE(8);

    companion object {
        fun fromValue(level: Int): LogLevel {
            return when (level) {
                ALL.priority -> ALL
                TRACE.priority -> TRACE
                DEBUG.priority -> DEBUG
                INFO.priority -> INFO
                WARN.priority -> WARN
                ERROR.priority -> ERROR
                WTF.priority -> WTF
                NONE.priority -> NONE
                else -> throw IllegalArgumentException("Invalid log level: $level")
            }
        }
    }
}


//package io.realm.log
//
//import io.realm.RealmConfiguration
//import io.realm.internal.interop.CoreLogLevel
//import io.realm.log.LogLevel.TRACE
//import io.realm.log.LogLevel.WTF
//
///**
// * Enum describing the log levels available to Realms internal logger.
// *
// * Each log entry is assigned a priority between [TRACE] and [WTF]. If the log level is equal or
// * higher than the priority defined in [io.realm.RealmConfiguration.Builder.logLevel] the event will
// * be logged.
// *
// * @see RealmConfiguration.Builder.log
// */
//enum class LogLevel(val priority: Int) {
//    ALL(CoreLogLevel.RLM_LOG_LEVEL_ALL.priority),
//    TRACE(CoreLogLevel.RLM_LOG_LEVEL_TRACE.priority),
//    DEBUG(CoreLogLevel.RLM_LOG_LEVEL_DEBUG.priority),
//    INFO(CoreLogLevel.RLM_LOG_LEVEL_INFO.priority),
//    WARN(CoreLogLevel.RLM_LOG_LEVEL_WARNING.priority),
//    ERROR(CoreLogLevel.RLM_LOG_LEVEL_ERROR.priority),
//    WTF(CoreLogLevel.RLM_LOG_LEVEL_FATAL.priority),
//    NONE(CoreLogLevel.RLM_LOG_LEVEL_OFF.priority)
//}
