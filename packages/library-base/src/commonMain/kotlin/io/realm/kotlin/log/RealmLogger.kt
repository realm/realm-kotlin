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

import io.realm.kotlin.Configuration

/**
 * Interface describing a logger implementation.
 *
 * @see Configuration.Builder.log
 */
public interface RealmLogger {

    /**
     * The [LogLevel] used in this logger.
     */
    public val level: LogLevel

    /**
     * Tag that can be used to describe the output.
     */
    public val tag: String

    /**
     * Log an event.
     */
    public fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?)

    /**
     * Log an event.
     */
    public fun log(level: LogLevel, message: String) {
        log(level, null, message, null)
    }
}
