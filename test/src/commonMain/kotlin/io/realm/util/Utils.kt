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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

// Platform dependant helper methods
expect object PlatformUtils {
    fun createTempDir(): String
    fun deleteTempDir(path: String)
    @OptIn(ExperimentalTime::class)
    fun sleep(duration: Duration)
    fun threadId(): ULong
}

// Platform independent helper methods
object Utils {

    fun createRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    fun printlntid(message: String) {
        println("<" + PlatformUtils.threadId() + "> $message")
    }
}

/**
 * Terminate a job because it is considered "done". This will throw a [CancellationException] so using this function
 * should be paired with [Job.awaitTestComplete] or [Deferred.awaitTestComplete].
 */

val testFinishedException = CancellationException("Test is done!")

fun Job.completeTest() {
    cancel(testFinishedException)
}

suspend fun Job.awaitTestComplete() {
    try {
        join()
    } catch (ex: CancellationException) {
        if (ex != testFinishedException) {
            throw ex
        }
    }
}

suspend fun CoroutineContext.completeTest() {
    cancel(testFinishedException)
}

suspend fun <T> Deferred<T>.awaitTestComplete() {
    try {
        await() /* Ignore return value */
    } catch (ex: CancellationException) {
        if (ex != testFinishedException) {
            throw ex
        }
    }
}
