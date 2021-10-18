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
import io.realm.internal.interop.CoreLogger

/**
 * Interface describing a logger implementation.
 *
 * @see RealmConfiguration.Builder.log
 */
// FIXME do not expose CoreLogger publicly: https://github.com/realm/realm-kotlin/issues/499
interface RealmLogger : CoreLogger {

    /**
     * Tag that can be used to describe the output.
     */
    val tag: String

    /**
     * Log an event.
     */
    fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?)

    fun log(message: String) {
        log(LogLevel.ALL, null, message, null)
    }

    // FIXME https://github.com/realm/realm-kotlin/issues/499
    override fun log(level: Short, message: String) {
        log(LogLevel.ALL, null, message, null)
    }
}
