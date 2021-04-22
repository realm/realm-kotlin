package io.realm.log

import io.realm.LogConfiguration
import io.realm.internal.TypeFactory

/**
 * Logger class used by Realm components. One logger is created for each Realm instance.
 */
// FIXME: Should be internal, but makes testing from the test module impossible
public class RealmLog(val tag: String = "REALM", val configuration: LogConfiguration) {

    @Suppress("JoinDeclarationAndAssignment")
    private val logLevel: LogLevel
    private val loggers: MutableList<RealmLogger> = mutableListOf()

    init {
        logLevel = configuration.level
        if (!configuration.removeSystemLogger) {
            loggers.add(TypeFactory.createDefaultSystemLogger(tag))
        }
        configuration.customLoggers.forEach { loggers.add(it) }
    }

    fun trace(throwable: Throwable?) {
        doLog(LogLevel.TRACE, throwable, null)
    }
    fun trace(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.TRACE, throwable, message, *args)
    }
    fun trace(message: String, vararg args: Any?) {
        doLog(LogLevel.TRACE, null, message, *args)
    }
    fun debug(throwable: Throwable?) {
        doLog(LogLevel.DEBUG, throwable, null)
    }
    fun debug(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.DEBUG, throwable, message, *args)
    }
    fun debug(message: String, vararg args: Any?) {
        doLog(LogLevel.DEBUG, null, message, *args)
    }
    fun info(throwable: Throwable?) {
        doLog(LogLevel.INFO, throwable, null)
    }
    fun info(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.INFO, throwable, message, *args)
    }
    fun info(message: String, vararg args: Any?) {
        doLog(LogLevel.INFO, null, message, *args)
    }
    fun warn(throwable: Throwable?) {
        doLog(LogLevel.WARN, throwable, null)
    }
    fun warn(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.WARN, throwable, message, *args)
    }
    fun warn(message: String, vararg args: Any?) {
        doLog(LogLevel.WARN, null, message, *args)
    }
    fun error(throwable: Throwable?) {
        doLog(LogLevel.ERROR, throwable, null)
    }
    fun error(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.ERROR, throwable, message, *args)
    }
    fun error(message: String, vararg args: Any?) {
        doLog(LogLevel.ERROR, null, message, *args)
    }
    fun wtf(throwable: Throwable?) {
        doLog(LogLevel.WTF, throwable, null)
    }
    fun wtf(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.WTF, throwable, message, *args)
    }
    fun wtf(message: String, vararg args: Any?) {
        doLog(LogLevel.WTF, null, message, *args)
    }

    private fun doLog(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?) {
        if (level.priority >= logLevel.priority) {
            loggers.forEach {
                it.log(level, throwable, message, *args)
            }
        }
    }
}
