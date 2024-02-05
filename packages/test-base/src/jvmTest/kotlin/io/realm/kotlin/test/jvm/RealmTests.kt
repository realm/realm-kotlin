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

package io.realm.kotlin.test.jvm

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.link.Child
import io.realm.kotlin.entities.link.Parent
import io.realm.kotlin.internal.platform.singleThreadDispatcher
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestChannel
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.test.util.use
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

fun totalThreadCount() = Thread.getAllStackTraces().size

/**
 * Realm tests that are specific to the JVM platform (both Desktop and Android).
 */
class RealmTests {

    // Test for https://github.com/Kotlin/kotlinx.coroutines/issues/3993
    @Test
    fun submittingToClosedDispatcherIsANoop() {
        val dispatcher = singleThreadDispatcher("test-${Random.nextUInt()}")
        dispatcher.close()
        runBlocking {
            launch(dispatcher) {
                fail("Dispatcher was running")
            }
        }
    }

    @Test
    fun cleanupDispatcherThreadsOnClose() = runBlocking {
        val tmpDir = PlatformUtils.createTempDir()

        val initialThreads = Thread.getAllStackTraces().keys
        fun newThreads(): List<Thread> = Thread.getAllStackTraces().keys.filter { !initialThreads.contains(it) }

        // Finalizer might be running if a another Realm has been opened first. Once started it will
        // for as long as the process is alive.
        val finalizerRunning = Thread.getAllStackTraces().filter { it.key.name == "RealmFinalizingDaemon" }.isNotEmpty()
        val configuration = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .build()

        newThreads().let {
            assertTrue(it.isEmpty(), "Creating a configuration should not start any threads, but started $it")
        }

        Realm.open(configuration).use { it ->
            // Opening a Realm will also start a Notifier and Writer dispatcher
            // For some reason the notifier thread is only started if something is actually executed
            // on it, so trigger some notifications
            it.asFlow().first()

            val realmThreads = 2 + if (finalizerRunning) 0 else 1
            newThreads().let {
                assertEquals(realmThreads, it.size, "Unexpected thread count after Realm.open: Newly created threads are $it")
            }

            // Doing updates will trigger the core notifier and attach with a shadow thread
            it.write { }
            newThreads().let {
                assertTrue(realmThreads + 1 >= it.size, "Unexpected thread count after Realm.write: Newly created threads are $it")
            }
        }
        // Closing a Realm should also cleanup our default (internal) dispatchers.
        // The core notifier and the finalizer thread will never be closed.
        val expectedThreadCount = initialThreads.size + 1 /* core-notifier */ + if (finalizerRunning) 0 else 1
        var counter = 10 // Wait 10 seconds for threads to settle
        while (newThreads().any { !it.isDaemon } && counter > 0) {
            delay(1000)
            counter--
        }
        val totalThreadCount = totalThreadCount()
        assertTrue(totalThreadCount <= expectedThreadCount, "Unexpected thread count after closing realm: $expectedThreadCount <= $totalThreadCount. New threads: ${newThreads()}. Threads: ${threadTrace()}")

        // Verify that all remaining threads are daemon threads, so that we don't keep the JVM alive
        newThreads().filter { !it.isDaemon }.let {
            assertTrue(it.isEmpty(), "Some left over threads are not daemon threads: $it")
        }
    }

    @Test
    @Suppress("invisible_reference", "invisible_member")
    fun providedDispatchersAreNotClosed() = runBlocking {
        withTimeout(30.seconds) {
            val tmpDir = PlatformUtils.createTempDir()
            val notificationDispatcher: CloseableCoroutineDispatcher = singleThreadDispatcher("custom-notifier-dispatcher")
            val writeDispatcher: CloseableCoroutineDispatcher = singleThreadDispatcher("custom-write-dispatcher")
            val configuration = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
                .directory(tmpDir)
                .notificationDispatcher(notificationDispatcher)
                .writeDispatcher(writeDispatcher)
                .build()
            Realm.open(configuration).close()
            // ClosableCoroutineDispatcher doesn't expose whether or not it has been closed, so test
            // if it has been closed by running work on it.
            val channel = TestChannel<Int>()
            async(notificationDispatcher) {
                channel.send(1)
            }
            assertEquals(1, channel.receiveOrFail())
            async(writeDispatcher) {
                channel.send(2)
            }
            assertEquals(2, channel.receiveOrFail())
            channel.close()
            Unit
        }
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
