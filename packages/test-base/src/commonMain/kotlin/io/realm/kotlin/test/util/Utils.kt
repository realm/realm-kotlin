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

package io.realm.kotlin.test.util

import io.realm.kotlin.Realm
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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
 * Helper method for easily updating a single object. The updated object will be returned.
 * This method control its own write transaction, so cannot be called inside a write transaction
 */
suspend fun <T : RealmObject> T.update(block: T.() -> Unit): T {
    @Suppress("invisible_reference", "invisible_member")
    val realm = ((this as io.realm.kotlin.internal.RealmObjectInternal).`io_realm_kotlin_objectReference`!!.owner).owner as Realm
    return realm.write {
        val liveObject: T = findLatest(this@update)!!
        block(liveObject)
        liveObject
    }
}

// Expose a try-with-resource pattern for Realms
inline fun Realm.use(action: (Realm) -> Unit) {
    try {
        action(this)
    } finally {
        this.close()
    }
}
// Expose a try-with-resource pattern for Realms, but with support for Coroutines
suspend fun Realm.useInContext(action: suspend (Realm) -> Unit) {
    try {
        action(this)
    } finally {
        this.close()
    }
}

// Convert Kotlinx-datatime Instant to RealmInstant
fun Instant.toRealmInstant(): RealmInstant {
    val s: Long = this.epochSeconds
    val ns: Int = this.nanosecondsOfSecond

    // Both Instant and RealmInstant uses positive numbers above epoch
    // Below epoch:
    //  - RealmInstant uses both negative seconds and nanonseconds
    //  - Instant uses negative seconds but ONLY positive nanoseconds
    return if (s >= 0) {
        RealmInstant.from(s, ns)
    } else {
        val adjustedSeconds = s + 1
        val adjustedNanoSeconds = ns - 1_000_000_000
        RealmInstant.from(adjustedSeconds, adjustedNanoSeconds)
    }
}

/**
 * Channel implementation specifically suited for tests. Its size is unlimited, but will fail
 * the test if canceled while still containing unconsumed elements.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T> TestChannel(
    capacity: Int = Channel.UNLIMITED,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    failIfBufferIsEmptyOnCancel: Boolean = true
): Channel<T> {
    return Channel<T>(capacity = capacity, onBufferOverflow = onBufferOverflow) {
        if (failIfBufferIsEmptyOnCancel) {
            throw AssertionError("Failed to deliver: $it")
        }
    }
}

/**
 * Helper method that will use `trySend` to send a message to a Channel and throw
 * an error if `trySend` returns false
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T : Any?> Channel<T>.trySendOrFail(value: T) {
    val result: ChannelResult<Unit> = this.trySend(value)
    if (result.isFailure) {
        val additionalErrorInfo = result.exceptionOrNull()?.let { throwable ->
            " An exception was thrown:\n${throwable.stackTraceToString()}"
        } ?: ""
        throw AssertionError("Could not send message to channel: $value.$additionalErrorInfo")
    }
}

// Variant of `Channel.receiveOrFail()` that will will throw if a timeout is hit.
suspend fun <T : Any?> Channel<T>.receiveOrFail(timeout: Duration = 1.minutes, message: String? = null): T {
    try {
        return withTimeout(timeout) {
            // Note, using `select` with `onReceive` seems to cause some tests to hang for unknown
            // reasons. Right now the hypothesis is that because `onReceive` does not consume the
            // elements, it is causing some kind of race condition we have not been able to
            // find. For previous iterations of this method, see the Git history.
            this@receiveOrFail.receive()
        }
    } catch (ex: TimeoutCancellationException) {
        @Suppress("invisible_reference", "invisible_member")
        throw TimeoutCancellationException("Timeout after $timeout: ${if (message.isNullOrBlank()) "<no message>" else message}")
    }
}
