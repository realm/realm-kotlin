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

package io.realm.kotlin.test.mongodb.jvm

import io.realm.kotlin.Realm
import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.ParentPk
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.util.TestHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RealmTests {

    // Ensure that we cleanup all threads owned by Realm when closing the Realm as it will
    // otherwise prevent things like the JVM main thread from exiting.
    //
    // Since both coroutines and OkHttp spin up a fair amount of workers with unpredictable
    // lifecycle, it is very difficult to track all threads. Instead this test just makes a best
    // effort in detecting the cases we do know about.
    @Test
    fun cleanupAllRealmThreadsOnClose() = runBlocking {
        val app = TestApp()
        val user = app.login(Credentials.anonymous())
        val configuration = SyncConfiguration.create(user, TestHelper.randomPartitionValue(), setOf(ParentPk::class, ChildPk::class))
        Realm.open(configuration).close()
        app.close()

        // Wait max 30 seconds for threads to settle
        var activeThreads = 0
        var fullyClosed = false
        var count = 30
        while (!fullyClosed && count > 0) {
            delay(1.seconds)
            // Ensure we only have daemon threads after closing Realms and Apps
            activeThreads = Thread.getAllStackTraces().keys
                .filter { !it.isDaemon }
                .filterNot {
                    // Android Studio or Gradle worker threads
                    it.name.startsWith("/127.0.0.1")
                }
                .filterNot {
                    // Test thread
                    it.name.startsWith("Test worker")
                }
                .size
            if (activeThreads == 0) {
                fullyClosed = true
            } else {
                count -= 1
            }
        }
        assertEquals(0, activeThreads, "Active threads where found: ${threadTrace()}")
    }

    private fun threadTrace(): String {
        val sb = StringBuilder()
        sb.appendLine("--------------------------------")
        val stack = Thread.getAllStackTraces()
        stack.keys
            .sortedBy { it.name }
            .forEach { t: Thread ->
                sb.appendLine("${t.name} - Is Daemon ${t.isDaemon} - Is Alive ${t.isAlive}")
            }
        sb.appendLine("All threads: ${stack.keys.size}")
        sb.appendLine("Active threads: ${Thread.activeCount()}")
        return sb.toString()
    }
}
