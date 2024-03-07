/*
 * Copyright 2023 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm.kotlin.log

import io.realm.kotlin.Realm
import io.realm.kotlin.internal.interop.CoreLogCategory
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
 *
 * When logging messages, it it possible to use a subset of String format options as known from
 * Java. Only `%s`, `%d` and `%f` are supported. See https://stackoverflow.com/a/64499248/1389357
 * and https://youtrack.jetbrains.com/issue/KT-25506 for more information.
 */
public object RealmLog : LogCategory("Realm") {

    /**
     * TODO
     */
    public val StorageLog: StorageLogCategory = StorageLogCategory
    /**
     * TODO
     */
    public val SyncLog: SyncLogCategory = SyncLogCategory
    /**
     * TODO
     */
    public val AppLog: LogCategory = AppLogCategory
    /**
     * TODO
     */
    public val SdkLog: LogCategory = SdkLogCategory

    // Lock preventing multiple threads modifying the list of loggers.
    private val loggersMutex = SynchronizableObject()
    // Reference to the currently installed system logger (if any)
    // `internal` until we can remove the old log API
    internal var systemLoggerInstalled: RealmLogger? = null
    // Kotlin Multiplatform is currently lacking primitives like CopyOnWriteArrayList. We could
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
                    // Create concatenated up front, since Core should already filter messages
                    // not within the log range.
                    val level: LogLevel = CoreLogLevel.valueFromPriority(logLevel).fromCoreLogLevel()
                    doLog(
                        level,
                        null,
                        if (message.isNullOrBlank()) { null } else { "[Core] $message" }
                    )
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
            val updatedLoggers = MutableList(loggers.size) { loggers[it] }
            updatedLoggers.add(logger).also {
                loggers = updatedLoggers
            }
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
     * Removes all loggers, including the default system logger. The default logger can be re-added
     * by calling [addDefaultSystemLogger] again.
     *
     * @return `true` will be returned if one or more loggers were removed, `false` if no loggers were
     * removed.
     */
    public fun removeAll(): Boolean {
        loggersMutex.withLock {
            val loggerRemoved = loggers.isNotEmpty()
            loggers = mutableListOf()
            systemLoggerInstalled = null
            return loggerRemoved
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
     * Logs a [LogLevel.TRACE] event.
     *
     * @param throwable optional exception to log.
     */
    internal fun trace(throwable: Throwable?) {
        doLog(LogLevel.TRACE, throwable, null)
    }

    /**
     * Logs a [LogLevel.TRACE] event.
     *
     * @param throwable optional exception to log.
     * @param message optional message.
     * @param args optional args used to format the message using a subset of `String.format`
     * options. Only `%s`, `%d` and `%f` are supported.
     */
    internal fun trace(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.TRACE, throwable, message, *args)
    }

    /**
     * Logs a [LogLevel.TRACE] event.
     *
     * @param message optional message.
     * @param args optional args used to format the message using a subset of `String.format`
     * options. Only `%s`, `%d` and `%f` are supported.
     */
    internal fun trace(message: String, vararg args: Any?) {
        doLog(LogLevel.TRACE, null, message, *args)
    }

    /**
     * Logs a [LogLevel.DEBUG] event.
     *
     * @param throwable optional exception to log.
     */
    internal fun debug(throwable: Throwable?) {
        doLog(LogLevel.DEBUG, throwable, null)
    }

    /**
     * Logs a [LogLevel.DEBUG] event.
     *
     * @param throwable optional exception to log.
     * @param message optional message.
     * @param args optional args used to format the message using a subset of `String.format`
     * options. Only `%s`, `%d` and `%f` are supported.
     */
    internal fun debug(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.DEBUG, throwable, message, *args)
    }

    /**
     * Logs a [LogLevel.DEBUG] event.
     *
     * @param message optional message.
     * @param args optional args used to format the message using a subset of `String.format`
     * options. Only `%s`, `%d` and `%f` are supported.
     */
    internal fun debug(message: String, vararg args: Any?) {
        doLog(LogLevel.DEBUG, null, message, *args)
    }

    /**
     * Logs a [LogLevel.INFO] event.
     *
     * @param throwable optional exception to log.
     */
    internal fun info(throwable: Throwable?) {
        doLog(LogLevel.INFO, throwable, null)
    }

    /**
     * Logs a [LogLevel.INFO] event.
     *
     * @param throwable optional exception to log.
     * @param message optional message.
     * @param args optional args used to format the message using a subset of `String.format`
     * options. Only `%s`, `%d` and `%f` are supported.
     */
    internal fun info(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.INFO, throwable, message, *args)
    }

    /**
     * Logs a [LogLevel.INFO] event.
     *
     * @param message optional message.
     * @param args optional args used to format the message using a subset of `String.format`
     * options. Only `%s`, `%d` and `%f` are supported.
     */
    internal fun info(message: String, vararg args: Any?) {
        doLog(LogLevel.INFO, null, message, *args)
    }

    /**
     * Logs a [LogLevel.WARN] event.
     *
     * @param throwable optional exception to log.
     */
    internal fun warn(throwable: Throwable?) {
        doLog(LogLevel.WARN, throwable, null)
    }

    /**
     * Logs a [LogLevel.WARN] event.
     *
     * @param throwable optional exception to log.
     * @param message optional message.
     * @param args optional args used to format the message using a subset of `String.format`
     * options. Only `%s`, `%d` and `%f` are supported.
     */
    internal fun warn(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.WARN, throwable, message, *args)
    }

    /**
     * Logs a [LogLevel.INFO] event.
     *
     * @param message optional message.
     * @param args optional args used to format the message using a subset of `String.format`
     * options. Only `%s`, `%d` and `%f` are supported.
     */
    internal fun warn(message: String, vararg args: Any?) {
        doLog(LogLevel.WARN, null, message, *args)
    }

    /**
     * Logs a [LogLevel.ERROR] event.
     *
     * @param throwable optional exception to log.
     */
    internal fun error(throwable: Throwable?) {
        doLog(LogLevel.ERROR, throwable, null)
    }

    /**
     * Logs a [LogLevel.ERROR] event.
     *
     * @param throwable optional exception to log.
     * @param message optional message.
     * @param args optional args used to format the message using a subset of `String.format`
     * options. Only `%s`, `%d` and `%f` are supported.
     */
    internal fun error(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.ERROR, throwable, message, *args)
    }

    /**
     * Logs a [LogLevel.ERROR] event.
     *
     * @param message optional message.
     * @param args optional args used to format the message using a subset of `String.format`
     * options. Only `%s`, `%d` and `%f` are supported.
     */
    internal fun error(message: String, vararg args: Any?) {
        doLog(LogLevel.ERROR, null, message, *args)
    }

    /**
     * Logs a [LogLevel.WTF] event.
     *
     * @param throwable optional exception to log.
     */
    internal fun wtf(throwable: Throwable?) {
        doLog(LogLevel.WTF, throwable, null)
    }

    /**
     * Logs a [LogLevel.WTF] event.
     *
     * @param throwable optional exception to log.
     * @param message optional message.
     * @param args optional args used to format the message using a subset of `String.format`
     * options. Only `%s`, `%d` and `%f` are supported.
     */
    internal fun wtf(throwable: Throwable?, message: String, vararg args: Any?) {
        doLog(LogLevel.WTF, throwable, message, *args)
    }

    /**
     * Logs a [LogLevel.WTF] event.
     *
     * @param message optional message.
     * @param args optional args used to format the message using a subset of `String.format`
     * options. Only `%s`, `%d` and `%f` are supported.
     */
    internal fun wtf(message: String, vararg args: Any?) {
        doLog(LogLevel.WTF, null, message, *args)
    }

    /**
     * Log a message.
     */
    internal fun doLog(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?) {
        if (level.priority >= this.level.priority) {
            // Copy the reference to loggers so they are stable while iterating them.
            val loggers = this.loggers
            loggers.forEach {
                it.log(level, throwable, message, *args)
            }
        }
    }

    /***
     * Internal method used to optimize logging from internal components. See
     * [io.realm.kotlin.internal.ContextLogger] for more details.
     */
    internal inline fun doLog(level: LogLevel, throwable: Throwable?, message: () -> String?, vararg args: Any?) {
        if (level.priority >= this.level.priority) {
            // Copy the reference to loggers so they are stable while iterating them.
            val loggers = this.loggers
            val msg = message()
            loggers.forEach {
                it.log(level, throwable, msg, *args)
            }
        }
    }

    /**
     * Reset the log configuration to its initial default setup.
     */
    internal fun reset() {
        removeAll()
        addDefaultSystemLogger()
        level = LogLevel.WARN
    }
}
