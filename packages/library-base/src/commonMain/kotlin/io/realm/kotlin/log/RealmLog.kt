package io.realm.kotlin.log

import io.realm.kotlin.Realm
import io.realm.kotlin.internal.interop.CoreLogLevel
import io.realm.kotlin.internal.interop.LogCallback
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.SynchronizableObject
import io.realm.kotlin.internal.platform.createDefaultSystemLogger
import io.realm.kotlin.log.RealmLog.add
import io.realm.kotlin.log.RealmLog.addDefaultSystemLogger

/**
 * Global logger class used by all Realm components.
 *
 * By default all logs will go to a default system logger that will depend on the system. See
 * [addDefaultSystemLogger] for more details.
 *
 * Custom loggers can be added by registering a class implementing [RealmLogger] using [add].
 */
public object RealmLog {

    /**
     * The current [LogLevel]. Changing this will affect all registered loggers.
    */
    public var level: LogLevel = LogLevel.WARN
        set(value) {
            RealmInterop.realm_set_log_level(value.toCoreLogLevel())
            field = value
        }

    // Lock preventing multiple threads modifying the list of loggers.
    private val loggersMutex = SynchronizableObject()
    // Reference to the currently installed system logger (if any)
    private var systemLoggerInstalled: RealmLogger? = null
    // Kotlin Multiplatform are currently lacking primitives like CopyOnWriteArrayList. We could
    // use `io.realm.kotlin.internal.interop.SynchronizableObject`, but it would require locking
    // when reporting a log statement which feel a bit heavy, so instead we have added locks around
    // all modifications to this array (which are expected to be rare) and the `doLog` method must
    // copy this reference before using it.
    private var loggers: MutableList<RealmLogger> = mutableListOf()

    init {
        addDefaultSystemLogger()
        RealmInterop.realm_set_log_callback(
            level.toCoreLogLevel(),
            object : LogCallback {
                override fun log(logLevel: Short, message: String?) {
                    doLog(fromCoreLogLevel(CoreLogLevel.valueFromPriority(logLevel)), null, message)
                }
            }
        )
    }

    /**
     * Add a logger that will be notified on log events that are equal to or exceed the currently
     * configured [level].
     *
     * @param logger logger to add.
     */
    public fun add(logger: RealmLogger) {
        loggersMutex.withLock {
            loggers.add(logger)
        }
    }

    /**
     * Removes the given logger if possible.
     *
     * @param logger logger that should be removed.
     * @return `true` if the logger was removed, `false` if it wasn't registered.
     */
    public fun remove(logger: RealmLogger): Boolean {
        loggersMutex.withLock {
            val updatedLoggers = MutableList(loggers.size) { loggers[it] }
            return updatedLoggers.remove(logger).also {
                loggers = updatedLoggers
            }
        }
    }

    /**
     * Removes all loggers. The default system logger will be removed as well unless
     * [removeDefaultSystemLogger] is set to `false`. [addDefaultSystemLogger] can be used
     * to add the default logger again if it was removed.
     *
     * @param removeDefaultSystemLogger whether or not to also remove the default system logger.
     * @return `true` will be returned if one or more loggers were removed, `false` if no loggers were
     * removed.
     */
    public fun removeAll(removeDefaultSystemLogger: Boolean = true): Boolean {
        loggersMutex.withLock {
            val updatedLoggers = MutableList(loggers.size) { loggers[it] }
            return updatedLoggers.removeAll {
                if (!removeDefaultSystemLogger) {
                    it != systemLoggerInstalled
                } else {
                    true
                }
            }.also {
                loggers = updatedLoggers
            }
        }
    }

    /**
     * Adds a default system logger. Where it report log events will depend on the system:
     * - On Android it will go to LogCat.
     * - On JVM it will go to std out.
     * - On MacOS it will go to NSLog.
     * - On iOS it will go to NSLog.
     *
     * @return `true` if the system logger was added, `false` if it was already present.
     */
    public fun addDefaultSystemLogger(): Boolean {
        loggersMutex.withLock {
            if (systemLoggerInstalled == null) {
                val systemLogger = createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG)
                val updatedLoggers = MutableList(loggers.size) { loggers[it] }
                updatedLoggers.add(systemLogger)
                systemLoggerInstalled = systemLogger
                loggers = updatedLoggers
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
        if (level.priority >= this.level.priority) {
            // Copy the reference to loggers so they are stable while iterating them.
            val loggers = this.loggers
            loggers.forEach {
                it.log(level, throwable, message, *args)
            }
        }
    }

    private fun LogLevel.toCoreLogLevel(): CoreLogLevel {
        return when (this) {
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
}