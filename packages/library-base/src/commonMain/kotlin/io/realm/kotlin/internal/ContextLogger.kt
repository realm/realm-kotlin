package io.realm.kotlin.internal

import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog

/**
 * Internal logger class used to inject context aware information into log message
 * before they are passed on to the global [RealmLog].
 */
public class ContextLogger(public val context: String? = null) {

    // define a context specific "prefix" for a log message.
    // Note, this is in addition to the TAG defined by the RealmLogger.
    // This can be used to add further context to a message, e.g:
    // `[RealmTag] [CoreContext] message`
    private val contextPrefix: String = if (context.isNullOrBlank()) {
        ""
    } else {
        "[$context] "
    }

    // TRACE
    public fun trace(throwable: Throwable?) {
        RealmLog.doLog(LogLevel.TRACE, throwable, null)
    }
    public fun trace(throwable: Throwable?, message: String, vararg args: Any?) {
        RealmLog.doLog(LogLevel.TRACE, throwable, { contextPrefix + message }, *args)
    }
    public fun trace(message: String, vararg args: Any?) {
        RealmLog.doLog(LogLevel.TRACE, null, { contextPrefix + message }, *args)
    }
    // DEBUG
    public fun debug(throwable: Throwable?) {
        RealmLog.doLog(LogLevel.DEBUG, throwable, null)
    }
    public fun debug(throwable: Throwable?, message: String, vararg args: Any?) {
        RealmLog.doLog(LogLevel.DEBUG, throwable, { contextPrefix + message }, *args)
    }
    public fun debug(message: String, vararg args: Any?) {
        RealmLog.doLog(LogLevel.DEBUG, null, { contextPrefix + message }, *args)
    }
    // INFO
    public fun info(throwable: Throwable?) {
        RealmLog.doLog(LogLevel.INFO, throwable, null)
    }
    public fun info(throwable: Throwable?, message: String, vararg args: Any?) {
        RealmLog.doLog(LogLevel.INFO, throwable, { contextPrefix + message }, *args)
    }
    public fun info(message: String, vararg args: Any?) {
        RealmLog.doLog(LogLevel.INFO, null, { contextPrefix + message }, *args)
    }
    // WARN
    public fun warn(throwable: Throwable?) {
        RealmLog.doLog(LogLevel.WARN, throwable, null)
    }
    public fun warn(throwable: Throwable?, message: String, vararg args: Any?) {
        RealmLog.doLog(LogLevel.WARN, throwable, { contextPrefix + message }, *args)
    }
    public fun warn(message: String, vararg args: Any?) {
        RealmLog.doLog(LogLevel.WARN, null, { contextPrefix + message }, *args)
    }
    // ERROR
    public fun error(throwable: Throwable?) {
        RealmLog.doLog(LogLevel.ERROR, throwable, null)
    }
    public fun error(throwable: Throwable?, message: String, vararg args: Any?) {
        RealmLog.doLog(LogLevel.ERROR, throwable, { contextPrefix + message }, *args)
    }
    public fun error(message: String, vararg args: Any?) {
        RealmLog.doLog(LogLevel.ERROR, null, { contextPrefix + message }, *args)
    }
    // WTF
    public fun wtf(throwable: Throwable?) {
        RealmLog.doLog(LogLevel.WTF, throwable, null)
    }
    public fun wtf(throwable: Throwable?, message: String, vararg args: Any?) {
        RealmLog.doLog(LogLevel.WTF, throwable, { contextPrefix + message }, *args)
    }
    public fun wtf(message: String, vararg args: Any?) {
        RealmLog.doLog(LogLevel.WTF, null, { contextPrefix + message }, *args)
    }
}
