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
import io.realm.kotlin.test.util.use
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Realm tests that are specific to the JVM platform (both Desktop and Android).
 */
class RealmTests {

    @Test
    fun cleanupDispatcherThreadsOnClose() = runBlocking {
        val tmpDir = PlatformUtils.createTempDir()
        val startingThreadCount = Thread.activeCount()
        // Finalizer might be running if a another Realm has been opened first. Once started it will
        // for as long as the process is alive.
        val finalizerRunning = Thread.getAllStackTraces().filter { it.key.name == "RealmFinalizerThread" }.isNotEmpty()
        val configuration = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .build()
        assertEquals(startingThreadCount, Thread.activeCount(), "Creating a configuration should not start any threads.")
        Realm.open(configuration).use {
            // Opening a Realm will also start a Notifier and Writer dispatcher
            val realmThreads = 2 + if (finalizerRunning) 0 else 1
            assertEquals(startingThreadCount + realmThreads, Thread.activeCount(), "Failed to start Realm dispatchers.")
        }
        // Closing a Realm should also cleanup our default (internal) dispatchers.
        // The finalizer thread will never be closed.
        val expectedThreadCount = startingThreadCount + if (finalizerRunning) 0 else 1
        var counter = 5 // Wait 5 seconds for threads to settle
        while (Thread.activeCount() != expectedThreadCount && counter > 0) {
            delay(1000)
            counter--
        }
        assertEquals(expectedThreadCount, Thread.activeCount(), "Failed to close notifier dispatchers in time.")
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
            val channel = Channel<Int>(1)
            async(notificationDispatcher) {
                channel.send(1)
            }
            assertEquals(1, channel.receive())
            async(writeDispatcher) {
                channel.send(2)
            }
            assertEquals(2, channel.receive())
            channel.close()
            Unit
        }
    }
}
