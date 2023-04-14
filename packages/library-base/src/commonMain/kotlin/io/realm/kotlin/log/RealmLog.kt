package io.realm.kotlin.log

import io.realm.kotlin.Realm
import io.realm.kotlin.internal.interop.CoreLogLevel
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.LogCallback
import io.realm.kotlin.internal.interop.SynchronizableObject
import io.realm.kotlin.internal.platform.createDefaultSystemLogger
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject

/**
 * Logger class used by Realm components. One logger is created for each Realm instance.
 */
public object RealmLog {

    /**
     * TODO
     */
    public var logLevel: LogLevel = LogLevel.WARN
        set(value) {
            RealmInterop.realm_set_log_level(value.toCoreLogLevel())
            field = value
        }

    private val tag: String = Realm.DEFAULT_LOG_TAG
    private val systemLoggerInstalled = atomic<RealmLogger?>(null)
    private val loggersMutex = SynchronizableObject()
    private val loggers: MutableList<RealmLogger> = mutableListOf()

    init {
        val initialLevel = logLevel.toCoreLogLevel()
        registerDefaultSystemLogger()
        RealmInterop.realm_set_log_callback(initialLevel, object: LogCallback {
            override fun log(logLevel: Short, message: String?) {
                doLog(fromCoreLogLevel(CoreLogLevel.valueFromPriority(logLevel)), null, message)
            }
        })
    }

    private fun LogLevel.toCoreLogLevel(): CoreLogLevel {
        return when(this) {
            LogLevel.ALL -> CoreLogLevel.RLM_LOG_LEVEL_ALL
            LogLevel.TRACE -> CoreLogLevel.RLM_LOG_LEVEL_TRACE
            LogLevel.DEBUG -> CoreLogLevel.RLM_LOG_LEVEL_DEBUG
            LogLevel.INFO -> CoreLogLevel.RLM_LOG_LEVEL_INFO
            LogLevel.WARN -> CoreLogLevel.RLM_LOG_LEVEL_WARNING
            LogLevel.ERROR -> CoreLogLevel.RLM_LOG_LEVEL_ERROR
            LogLevel.WTF -> CoreLogLevel.RLM_LOG_LEVEL_FATAL
            LogLevel.NONE -> CoreLogLevel.RLM_LOG_LEVEL_OFF
        }
    }

    private fun fromCoreLogLevel(level: CoreLogLevel): LogLevel {
        return when (level) {
            CoreLogLevel.RLM_LOG_LEVEL_ALL,
            CoreLogLevel.RLM_LOG_LEVEL_TRACE -> LogLevel.TRACE
            CoreLogLevel.RLM_LOG_LEVEL_DEBUG -> LogLevel.DEBUG
            CoreLogLevel.RLM_LOG_LEVEL_DETAIL,
            CoreLogLevel.RLM_LOG_LEVEL_INFO -> LogLevel.INFO
            CoreLogLevel.RLM_LOG_LEVEL_WARNING -> LogLevel.WARN
            CoreLogLevel.RLM_LOG_LEVEL_ERROR -> LogLevel.ERROR
            CoreLogLevel.RLM_LOG_LEVEL_FATAL -> LogLevel.WTF
            CoreLogLevel.RLM_LOG_LEVEL_OFF -> LogLevel.NONE
            else -> throw IllegalArgumentException("Invalid core log level: $level")
        }
    }

    /**
     * TODO
     */
    public fun addLogger(logger: RealmLogger) {
        loggersMutex.withLock {
            loggers.add(logger)
        }
    }

    /**
     * TODO
     */
    public fun removeLogger(logger: RealmLogger): Boolean {
        loggersMutex.withLock {
            return loggers.remove(logger)
        }
    }

    /**
     * TODO
     */
    public fun removeAllLoggers(removeDefaultSystemLogger: Boolean = false): Boolean {
        loggersMutex.withLock {
            return loggers.removeAll {
                if (!removeDefaultSystemLogger) {
                    it != systemLoggerInstalled.value
                } else {
                    true
                }
            }
        }
    }

    /**
     * TODO
     */
    public fun registerDefaultSystemLogger(): Boolean {
        loggersMutex.withLock {
            if (systemLoggerInstalled.value == null) {
                val systemLogger = createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG)
                loggers.add(systemLogger)
                systemLoggerInstalled.value = systemLogger
                return true
            }
            return false
        }
    }

    /**
     * TODO
     */
    public fun trace(throwable: Throwable?) {
        doLog(LogLevel.TRACE, throwable, null)
    }

    /**
     * TODO
     */
    public fun trace(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.TRACE, throwable, message, *args)
    }

    /**
     * TODO
     */
    public fun trace(message: String, vararg args: Any?) {
        doLog(LogLevel.TRACE, null, message, *args)
    }

    /**
     * TODO
     */
    public fun debug(throwable: Throwable?) {
        doLog(LogLevel.DEBUG, throwable, null)
    }

    /**
     * TODO
     */
    public fun debug(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.DEBUG, throwable, message, *args)
    }

    /**
     * TODO
     */
    public fun debug(message: String, vararg args: Any?) {
        doLog(LogLevel.DEBUG, null, message, *args)
    }

    /**
     * TODO
     */
    public fun info(throwable: Throwable?) {
        doLog(LogLevel.INFO, throwable, null)
    }

    /**
     * TODO
     */
    public fun info(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.INFO, throwable, message, *args)
    }

    /**
     * TODO
     */
    public fun info(message: String, vararg args: Any?) {
        doLog(LogLevel.INFO, null, message, *args)
    }

    /**
     * TODO
     */
    public fun warn(throwable: Throwable?) {
        doLog(LogLevel.WARN, throwable, null)
    }

    /**
     * TODO
     */
    public fun warn(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.WARN, throwable, message, *args)
    }

    /**
     * TODO
     */
    public fun warn(message: String, vararg args: Any?) {
        doLog(LogLevel.WARN, null, message, *args)
    }

    /**
     * TODO
     */
    public fun error(throwable: Throwable?) {
        doLog(LogLevel.ERROR, throwable, null)
    }

    /**
     * TODO
     */
    public fun error(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.ERROR, throwable, message, *args)
    }

    /**
     * TODO
     */
    public fun error(message: String, vararg args: Any?) {
        doLog(LogLevel.ERROR, null, message, *args)
    }

    /**
     * TODO
     */
    public fun wtf(throwable: Throwable?) {
        doLog(LogLevel.WTF, throwable, null)
    }

    /**
     * TODO
     */
    public fun wtf(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.WTF, throwable, message, *args)
    }

    /**
     * TODO
     */
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
}
