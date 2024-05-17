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

package io.realm.kotlin.log

import io.realm.kotlin.log.LogLevel.TRACE
import io.realm.kotlin.log.LogLevel.WTF

/**
 * Enum describing the log levels available to the Realm logger.
 *
 * Each log entry is assigned a priority between [TRACE] and [WTF]. If the log level is equal or
 * higher than the priority defined in [RealmLog.getLevel] the event will be logged.
 */
@Suppress("MagicNumber")
public enum class LogLevel(public val priority: Int) {
    ALL(0),
    TRACE(1),
    DEBUG(2),
    INFO(3),
    WARN(4),
    ERROR(5),
    WTF(6),
    NONE(7);
}
