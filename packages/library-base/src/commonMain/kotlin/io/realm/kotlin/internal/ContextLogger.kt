package io.realm.kotlin.internal

import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.log.SdkLogCategory

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
        doLog(LogLevel.TRACE, throwable)
    }
    public fun trace(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.TRACE, throwable, { contextPrefix + message }, *args)
    }
    public fun trace(message: String, vararg args: Any?) {
        doLog(LogLevel.TRACE, null, { contextPrefix + message }, *args)
    }
    // DEBUG
    public fun debug(throwable: Throwable?) {
        doLog(LogLevel.DEBUG, throwable)
    }
    public fun debug(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.DEBUG, throwable, { contextPrefix + message }, *args)
    }
    public fun debug(message: String, vararg args: Any?) {
        doLog(LogLevel.DEBUG, null, { contextPrefix + message }, *args)
    }
    // INFO
    public fun info(throwable: Throwable?) {
        doLog(LogLevel.INFO, throwable)
    }
    public fun info(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.INFO, throwable, { contextPrefix + message }, *args)
    }
    public fun info(message: String, vararg args: Any?) {
        doLog(LogLevel.INFO, null, { contextPrefix + message }, *args)
    }
    // WARN
    public fun warn(throwable: Throwable?) {
        doLog(LogLevel.WARN, throwable)
    }
    public fun warn(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.WARN, throwable, { contextPrefix + message }, *args)
    }
    public fun warn(message: String, vararg args: Any?) {
        doLog(LogLevel.WARN, null, { contextPrefix + message }, *args)
    }
    // ERROR
    public fun error(throwable: Throwable?) {
        doLog(LogLevel.ERROR, throwable)
    }
    public fun error(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.ERROR, throwable, { contextPrefix + message }, *args)
    }
    public fun error(message: String, vararg args: Any?) {
        doLog(LogLevel.ERROR, null, { contextPrefix + message }, *args)
    }
    // WTF
    public fun wtf(throwable: Throwable?) {
        doLog(LogLevel.WTF, throwable)
    }
    public fun wtf(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.WTF, throwable, { contextPrefix + message }, *args)
    }
    public fun wtf(message: String, vararg args: Any?) {
        doLog(LogLevel.WTF, null, { contextPrefix + message }, *args)
    }

    private inline fun checkPriority(
        level: LogLevel,
        block: () -> Unit,
    ) {
        if (level.priority >= RealmLog.sdkLogLevel.priority) {
            block()
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun doLog(level: LogLevel, throwable: Throwable?) {
        checkPriority(level) {
            RealmLog.doLog(SdkLogCategory, level, throwable, null)
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun doLog(
        level: LogLevel,
        throwable: Throwable?,
        message: () -> String?,
        vararg args: Any?,
    ) {
        checkPriority(level) {
            RealmLog.doLog(SdkLogCategory, level, throwable, message(), *args)
        }
    }
}
