package io.realm.kotlin.internal

import io.realm.kotlin.LogConfiguration
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger

/**
 * Logger class used by Realm components. One logger is created for each Realm instance.
 */
// TODO Public as it is used by `library-sync`
public class RealmLog(public val tag: String = "REALM", public val configuration: LogConfiguration) {

    public val logLevel: LogLevel = configuration.level

    private val loggers: List<RealmLogger> = configuration.loggers

    public fun trace(throwable: Throwable?) {
        doLog(LogLevel.TRACE, throwable, null)
    }
    public fun trace(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.TRACE, throwable, message, *args)
    }
    public fun trace(message: String, vararg args: Any?) {
        doLog(LogLevel.TRACE, null, message, *args)
    }
    public fun debug(throwable: Throwable?) {
        doLog(LogLevel.DEBUG, throwable, null)
    }
    public fun debug(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.DEBUG, throwable, message, *args)
    }
    public fun debug(message: String, vararg args: Any?) {
        doLog(LogLevel.DEBUG, null, message, *args)
    }
    public fun info(throwable: Throwable?) {
        doLog(LogLevel.INFO, throwable, null)
    }
    public fun info(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.INFO, throwable, message, *args)
    }
    public fun info(message: String, vararg args: Any?) {
        doLog(LogLevel.INFO, null, message, *args)
    }
    public fun warn(throwable: Throwable?) {
        doLog(LogLevel.WARN, throwable, null)
    }
    public fun warn(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.WARN, throwable, message, *args)
    }
    public fun warn(message: String, vararg args: Any?) {
        doLog(LogLevel.WARN, null, message, *args)
    }
    public fun error(throwable: Throwable?) {
        doLog(LogLevel.ERROR, throwable, null)
    }
    public fun error(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.ERROR, throwable, message, *args)
    }
    public fun error(message: String, vararg args: Any?) {
        doLog(LogLevel.ERROR, null, message, *args)
    }
    public fun wtf(throwable: Throwable?) {
        doLog(LogLevel.WTF, throwable, null)
    }
    public fun wtf(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.WTF, throwable, message, *args)
    }
    public fun wtf(message: String, vararg args: Any?) {
        doLog(LogLevel.WTF, null, message, *args)
    }

    private fun doLog(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?) {
        if (level.priority >= logLevel.priority) {
            loggers.forEach {
                it.log(level, throwable, message, *args)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as RealmLog

        if (tag != (other.tag)) return false
        if (logLevel != (other.logLevel)) return false
        return configuration.level == other.configuration.level
    }

    override fun hashCode(): Int {
        var result = tag.hashCode()
        result = 31 * result + configuration.level.hashCode()
        result = 31 * result + logLevel.hashCode()
        return result
    }
}
