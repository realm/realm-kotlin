/*
 * Copyright 2022 Realm Inc.
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

import io.realm.kotlin.mongodb.sync.SubscriptionSet
import io.realm.kotlin.mongodb.sync.SyncSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// NOTE: Copy from :base:commonTest. It is unclear if there is an easy way to share test code like
// this between :base and :sync

/**
 * Assert that a statement fails with a specific Exception type AND message. The message match is
 * fuzzy, i.e. we only check that the provided message is contained within the whole exception
 * message. The match is case sensitive.
 */
fun <T : Throwable> assertFailsWithMessage(exceptionClass: KClass<T>, exceptionMessage: String, block: () -> Unit): T {
    val exception: T = assertFailsWith(exceptionClass, null, block)
    if (exception.message?.contains(exceptionMessage, ignoreCase = false) != true) {
        throw AssertionError(
            """
            The exception message did not match.
            
            Expected:
            $exceptionMessage
            
            Actual:
            ${exception.message}
            
            """.trimIndent()
        )
    }
    return exception
}

inline fun <reified T : Throwable> assertFailsWithMessage(exceptionMessage: String, noinline block: () -> Unit): T =
    assertFailsWithMessage(T::class, exceptionMessage, block)

inline fun <reified T : Throwable> CoroutineScope.assertFailsWithMessage(exceptionMessage: String, noinline block: suspend CoroutineScope.() -> Unit): T =
    assertFailsWithMessage(T::class, exceptionMessage) {
        runBlocking(this.coroutineContext) {
            block()
        }
    }

suspend inline fun SubscriptionSet<*>.waitForSynchronizationOrFail() {
    val timeout = 5.minutes
    assertTrue(this.waitForSynchronization(timeout), "Failed to synchronize subscriptions in time: $timeout")
}

suspend inline fun SyncSession.uploadAllLocalChangesOrFail() {
    val timeout = 5.minutes
    assertTrue(this.uploadAllLocalChanges(timeout), "Failed to upload local changes in time: $timeout")
}

suspend fun <R> retry(action: suspend () -> R?, until: (R?) -> Boolean, retries: Int = 5, delay: Duration = 1.seconds): R? {
    repeat(retries) {
        action().let {
            if (until(it)) {
                return it
            } else {
                delay(delay)
            }
        }
    }
    fail("Exceeded retries")
}
