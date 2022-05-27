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

package io.realm.test.util

import io.realm.Realm
import io.realm.RealmInstant
import io.realm.RealmObject
import io.realm.test.platform.PlatformUtils
import kotlinx.datetime.Instant

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
    val realm = ((this as io.realm.internal.RealmObjectInternal).`io_realm_kotlin_objectReference`!!.owner).owner as Realm
    return realm.write {
        val liveObject: T = findLatest(this@update)!!
        block(liveObject)
        liveObject
    }
}

// Expose a try-with-resource pattern for Realms
fun Realm.use(action: (Realm) -> Unit) {
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
        RealmInstant.fromEpochSeconds(s, ns)
    } else {
        val adjustedSeconds = s + 1
        val adjustedNanoSeconds = ns - 1_000_000_000
        RealmInstant.fromEpochSeconds(adjustedSeconds, adjustedNanoSeconds)
    }
}
