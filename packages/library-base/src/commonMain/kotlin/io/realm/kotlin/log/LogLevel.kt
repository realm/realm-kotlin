package io.realm.kotlin.log

import io.realm.kotlin.Configuration
import io.realm.kotlin.Configuration.SharedBuilder
import io.realm.kotlin.internal.interop.CoreLogLevel
import io.realm.kotlin.log.LogLevel.TRACE
import io.realm.kotlin.log.LogLevel.WTF

/**
 * Enum describing the log levels available to Realms internal logger.
 *
 * Each log entry is assigned a priority between [TRACE] and [WTF]. If the log level is equal or
 * higher than the priority defined in [SharedBuilder.logLevel] the event will be logged.
 *
 * @see Configuration.SharedBuilder.log
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
