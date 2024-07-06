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
import io.realm.kotlin.test.mongodb.util.DefaultPartitionBasedAppInitializer
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestHelper
import kotlinx.coroutines.runBlocking
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class RealmTests {

    // Ensure that we cleanup all threads owned by Realm when closing the Realm as it will
    // otherwise prevent things like the JVM main thread from exiting.
    //
    // Since both coroutines and OkHttp spin up a fair amount of workers with unpredictable
    // lifecycle, it is very difficult to track all threads. Instead this test just makes a best
    // effort in detecting the cases we do know about.
    @Test
    @Ignore // See https://github.com/realm/realm-kotlin/issues/1627
    fun cleanupAllRealmThreadsOnClose() = runBlocking {
        @OptIn(ExperimentalKBsonSerializerApi::class)
        val app = TestApp("cleanupAllRealmThreadsOnClose", DefaultPartitionBasedAppInitializer)
        val user = app.login(Credentials.anonymous())
        val configuration = SyncConfiguration.create(user, TestHelper.randomPartitionValue(), setOf(ParentPk::class, ChildPk::class))
        Realm.open(configuration).close()
        app.close()

        // Wait max 30 seconds for threads to settle
        var activeThreads: List<Thread> = emptyList()
        var fullyClosed = false
        var count = 5
        while (!fullyClosed && count > 0) {
            PlatformUtils.triggerGC()
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
            if (activeThreads.isEmpty()) {
                fullyClosed = true
            } else {
                count -= 1
            }
        }
        assertEquals(0, activeThreads.size, "Active threads where found ($activeThreads.size): ${threadTrace(activeThreads)}")
    }

    private fun threadTrace(threads: List<Thread>? = null): String {
        val sb = StringBuilder()
        sb.appendLine("--------------------------------")
        val stack: List<Thread> = threads ?: Thread.getAllStackTraces().keys.toList()
        stack
            .sortedBy { it.name }
            .forEach { t: Thread ->
                sb.appendLine("${t.name} - Is Daemon ${t.isDaemon} - Is Alive ${t.isAlive}")
            }
        sb.appendLine("All threads: ${stack.size}")
        sb.appendLine("Active threads: ${Thread.activeCount()}")
        return sb.toString()
    }
}
