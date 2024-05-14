/*
 * Copyright 2024 Realm Inc.
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

package io.realm.kotlin.internal

import io.realm.kotlin.internal.interop.CoreLogLevel
import io.realm.kotlin.log.LogCategory
import io.realm.kotlin.log.LogCategoryImpl
import io.realm.kotlin.log.LogLevel


internal fun LogLevel.toCoreLogLevel(): CoreLogLevel {
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

internal fun CoreLogLevel.fromCoreLogLevel(): LogLevel {
    return when (this) {
        CoreLogLevel.RLM_LOG_LEVEL_ALL -> LogLevel.ALL
        CoreLogLevel.RLM_LOG_LEVEL_TRACE -> LogLevel.TRACE
        CoreLogLevel.RLM_LOG_LEVEL_DEBUG,
        CoreLogLevel.RLM_LOG_LEVEL_DETAIL -> LogLevel.DEBUG
        CoreLogLevel.RLM_LOG_LEVEL_INFO -> LogLevel.INFO
        CoreLogLevel.RLM_LOG_LEVEL_WARNING -> LogLevel.WARN
        CoreLogLevel.RLM_LOG_LEVEL_ERROR -> LogLevel.ERROR
        CoreLogLevel.RLM_LOG_LEVEL_FATAL -> LogLevel.WTF
        CoreLogLevel.RLM_LOG_LEVEL_OFF -> LogLevel.NONE
        else -> throw IllegalArgumentException("Invalid core log level: $this")
    }
}

internal fun newCategory(
    name: String,
    parent: LogCategory? = null,
): LogCategory = LogCategoryImpl(name, parent).also { category ->
    categoriesByPath["$category"] = category
}

// at package level as a workaround to ensure compatibility with darwin and jvm
internal val categoriesByPath: MutableMap<String, LogCategory> = mutableMapOf()

internal fun messageWithCategory(
    category: LogCategory,
    message: String?,
): String? = if (message.isNullOrBlank()) { null } else {
    "[${category}] $message" // TODO: .toString().substring(6)
}