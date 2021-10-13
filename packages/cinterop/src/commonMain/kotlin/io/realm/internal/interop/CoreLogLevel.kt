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

package io.realm.internal.interop

expect enum class CoreLogLevel {
    RLM_LOG_LEVEL_ALL,
    RLM_LOG_LEVEL_TRACE,
    RLM_LOG_LEVEL_DEBUG,
    RLM_LOG_LEVEL_INFO,
    RLM_LOG_LEVEL_WARNING,
    RLM_LOG_LEVEL_ERROR,
    RLM_LOG_LEVEL_FATAL,
    RLM_LOG_LEVEL_OFF;

    // We need this property since it isn't allowed to have constructor params in an expect enum
    val priority: Int
}
