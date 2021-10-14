/*
 * Copyright 2021 Realm Inc.
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

package io.realm.log

import io.realm.RealmConfiguration
import io.realm.internal.interop.CoreLogLevel
import io.realm.internal.interop.CoreLogger

/**
 * Interface describing a logger implementation.
 *
 * @see RealmConfiguration.Builder.log
 */
interface RealmLogger : CoreLogger {

    override val coreLogLevel: CoreLogLevel
        get() = when (level) {
            LogLevel.ALL -> CoreLogLevel.RLM_LOG_LEVEL_ALL
            LogLevel.TRACE -> CoreLogLevel.RLM_LOG_LEVEL_TRACE
            LogLevel.DEBUG -> CoreLogLevel.RLM_LOG_LEVEL_DEBUG
            LogLevel.INFO -> CoreLogLevel.RLM_LOG_LEVEL_INFO
            LogLevel.WARN -> CoreLogLevel.RLM_LOG_LEVEL_WARNING
            LogLevel.ERROR -> CoreLogLevel.RLM_LOG_LEVEL_ERROR
            LogLevel.WTF -> CoreLogLevel.RLM_LOG_LEVEL_FATAL
            LogLevel.NONE -> CoreLogLevel.RLM_LOG_LEVEL_OFF
        }

    /**
     * The [LogLevel] used in this logger.
     */
    val level: LogLevel

    /**
     * Tag that can be used to describe the output.
     */
    val tag: String

    /**
     * Log an event.
     */
    fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?)

    override fun log(level: Short, message: String) {
        log(level.toLogLevel(), null, message, null)
    }

    private fun Short.toLogLevel(): LogLevel {
        return when (this.toInt()) {
            CoreLogLevel.RLM_LOG_LEVEL_ALL.priority -> LogLevel.ALL
            CoreLogLevel.RLM_LOG_LEVEL_TRACE.priority -> LogLevel.TRACE
            CoreLogLevel.RLM_LOG_LEVEL_DEBUG.priority -> LogLevel.DEBUG
            CoreLogLevel.RLM_LOG_LEVEL_DETAIL.priority, // convert to INFO
            CoreLogLevel.RLM_LOG_LEVEL_INFO.priority -> LogLevel.INFO
            CoreLogLevel.RLM_LOG_LEVEL_WARNING.priority -> LogLevel.WARN
            CoreLogLevel.RLM_LOG_LEVEL_ERROR.priority -> LogLevel.ERROR
            CoreLogLevel.RLM_LOG_LEVEL_FATAL.priority -> LogLevel.WTF
            CoreLogLevel.RLM_LOG_LEVEL_OFF.priority -> LogLevel.NONE
            else -> throw IllegalArgumentException("Invalid priority level: $this.")
        }
    }
}
