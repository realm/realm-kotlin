package io.realm.log

/**
 * Enum describing the log levels available to [RealmLog].
 * Each log entry is assigned a priority between [TRACE] and [WTF].
 * The log entry will be logged i
 */
enum class LogLevel(internal val priority: Int) {
    ALL(0),
    TRACE(1),
    DEBUG(2),
    INFO(3),
    WARN(4),
    ERROR(5),
    WTF(6),
    NONE(7)
}