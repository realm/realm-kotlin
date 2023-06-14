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

package io.realm.kotlin.test.mongodb.common.utils

import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Logged collecting all logs it has seen.
 */
class CustomLogCollector(
    override val tag: String,
    override val level: LogLevel
) : RealmLogger {

    private val mutex = Mutex()
    private val _logs = mutableListOf<String>()
    /**
     * Returns a snapshot of the current state of the logs.
     */
    val logs: List<String>
        get() = runBlocking {
            mutex.withLock {
                _logs.toList()
            }
        }

    override fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?) {
        val logMessage: String = message!!
        runBlocking {
            mutex.withLock {
                _logs.add(logMessage)
            }
        }
    }
}
