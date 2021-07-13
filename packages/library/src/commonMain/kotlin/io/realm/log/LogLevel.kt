package io.realm.log

import io.realm.RealmConfiguration

/**
 * Enum describing the log levels available to Realms internal logger.
 *
 * Each log entry is assigned a priority between [TRACE] and [WTF]. If the log level is equal or higher
 * than the priority defined in [io.realm.RealmConfiguration.Builder.logLevel] the event will be logged.
 *
 * @see RealmConfiguration.Builder.log
 */
@Suppress("MagicNumber")
public enum class LogLevel(internal val priority: Int) {
    ALL(0),
    TRACE(1),
    DEBUG(2),
    INFO(3),
    WARN(4),
    ERROR(5),
    WTF(6),
    NONE(7)
}
