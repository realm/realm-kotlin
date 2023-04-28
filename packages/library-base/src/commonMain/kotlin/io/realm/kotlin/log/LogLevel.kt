package io.realm.kotlin.log

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
