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

/**
 * Interface describing a logger implementation.
 *
 * @see RealmConfiguration.Builder.log
 */
interface RealmLogger {

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

    /**
     * Log an event. For internal use only.
     */
    fun log(level: CoreLogLevel, message: String) {
        log(level.toLogLevel(), null, message, null)
    }

    private fun CoreLogLevel.toLogLevel(): LogLevel {
        return when (this) {
            CoreLogLevel.RLM_LOG_LEVEL_ALL -> LogLevel.ALL
            CoreLogLevel.RLM_LOG_LEVEL_TRACE -> LogLevel.TRACE
            CoreLogLevel.RLM_LOG_LEVEL_DEBUG -> LogLevel.DEBUG
            CoreLogLevel.RLM_LOG_LEVEL_DETAIL, // convert to INFO
            CoreLogLevel.RLM_LOG_LEVEL_INFO -> LogLevel.INFO
            CoreLogLevel.RLM_LOG_LEVEL_WARNING -> LogLevel.WARN
            CoreLogLevel.RLM_LOG_LEVEL_ERROR -> LogLevel.ERROR
            CoreLogLevel.RLM_LOG_LEVEL_FATAL -> LogLevel.WTF
            CoreLogLevel.RLM_LOG_LEVEL_OFF -> LogLevel.NONE
            else -> throw IllegalArgumentException("Invalid priority level: ${this.priority}.")
        }
    }
}
