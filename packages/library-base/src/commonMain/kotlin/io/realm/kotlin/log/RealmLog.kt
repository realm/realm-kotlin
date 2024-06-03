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
import io.realm.kotlin.internal.fromCoreLogLevel
import io.realm.kotlin.internal.interop.CoreLogLevel
import io.realm.kotlin.internal.interop.LogCallback
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.SynchronizableObject
import io.realm.kotlin.internal.platform.createDefaultSystemLogger
import io.realm.kotlin.internal.toCoreLogLevel
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
public object RealmLog {

    // Lock preventing multiple threads modifying the list of loggers.
    private val loggersMutex = SynchronizableObject()
    // Reference to the currently installed system logger (if any)
    // `internal` until we can remove the old log API
    private var systemLoggerInstalled: RealmLogger? = null
    // Kotlin Multiplatform is currently lacking primitives like CopyOnWriteArrayList. We could
    // use `io.realm.kotlin.internal.interop.SynchronizableObject`, but it would require locking
    // when reporting a log statement which feel a bit heavy, so instead we have added locks around
    // all modifications to this array (which are expected to be rare) and the `doLog` method must
    // copy this reference before using it.
    private var loggers: MutableList<RealmLogger> = mutableListOf()

    // Log level that would be set by default
    private val defaultLogLevel = LogLevel.WARN

    // Cached value of the SDK log level
    internal var sdkLogLevel = defaultLogLevel

    /**
     * Sets the log level of a log category. By setting the log level of a category all its subcategories
     * would also be updated to match its level.
     *
     * @param level target log level.
     * @param category target log category, [LogCategory.Realm] by default.
     */
    public fun setLevel(level: LogLevel, category: LogCategory = LogCategory.Realm) {
        RealmInterop.realm_set_log_level_category(category.toString(), level.toCoreLogLevel())
        sdkLogLevel = getLevel(SdkLogCategory)
    }

    /**
     * Gets the current log level of a log category.
     *
     * @param category target log category.
     * @return current [category] log level.
     */
    public fun getLevel(category: LogCategory = LogCategory.Realm): LogLevel {
        return RealmInterop.realm_get_log_level_category(category.toString()).fromCoreLogLevel()
    }

    init {
        addDefaultSystemLogger()
        setLevel(level = defaultLogLevel) // Set the log level to the SDKs (might be different from cores default INFO)
        RealmInterop.realm_set_log_callback(
            object : LogCallback {
                override fun log(logLevel: Short, categoryValue: String, message: String?) {
                    // Create concatenated up front, since Core should already filter messages
                    // not within the log range.
                    val category: LogCategory = LogCategory.fromCoreValue(categoryValue)
                    val level: LogLevel = CoreLogLevel.valueFromPriority(logLevel).fromCoreLogLevel()

                    doLog(
                        category,
                        level,
                        null,
                        message,
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
     * Log a message.
     */
    internal inline fun doLog(
        category: LogCategory,
        level: LogLevel,
        throwable: Throwable?,
        message: String?,
        vararg args: Any?,
    ) = loggers.forEach {
        it.log(
            category = category,
            level = level,
            throwable = throwable,
            message = message,
            args = *args
        )
    }

    /**
     * Reset the log configuration to its initial default setup.
     */
    internal fun reset() {
        removeAll()
        addDefaultSystemLogger()
        setLevel(LogLevel.WARN)
    }
}
