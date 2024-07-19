/*
 * Copyright 2024 Realm Inc.
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

package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.Realm
import io.realm.kotlin.entities.sync.SyncObjectWithAllTypes
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.sync.Direction
import io.realm.kotlin.mongodb.sync.Progress
import io.realm.kotlin.mongodb.sync.ProgressMode
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.mongodb.use
import io.realm.kotlin.test.mongodb.util.DefaultFlexibleSyncAppInitializer
import io.realm.kotlin.test.util.TestChannel
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.test.util.use
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class FLXProgressListenerTests {

    private val TEST_SIZE = 10
    private val TIMEOUT = 30.seconds

    private lateinit var app: TestApp
    private lateinit var partitionValue: String

    @BeforeTest
    fun setup() {
        app = TestApp(this::class.simpleName, DefaultFlexibleSyncAppInitializer)
        partitionValue = org.mongodb.kbson.ObjectId().toString()
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun downloadProgressListener_changesOnly() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogIn())).use { uploadRealm ->
            // Verify that we:
            // - get a "transferComplete" event
            // - complete the flow, and
            // - that all objects are available afterwards
            Realm.open(createSyncConfig(app.createUserAndLogIn())).use { realm ->
                // Ensure that we can do consecutive CURRENT_CHANGES registrations
                for (i in 0 until 3) {
                    val transferCompleteJob = async {
                        // Postpone the progress listener flow so that it is started after the
                        // following downloadAllServerChanges. This should ensure that we are
                        // actually downloading stuff.
                        realm.syncSession.progressAsFlow(
                            Direction.DOWNLOAD,
                            ProgressMode.CURRENT_CHANGES
                        ).run {
                            withTimeout(TIMEOUT) {
                                last().let { progress: Progress ->
                                    assertTrue(progress.isTransferComplete)
                                    assertEquals(1.0, progress.estimate)
                                }
                            }
                        }
                    }
                    uploadRealm.writeSampleData(
                        TEST_SIZE,
                        timeout = TIMEOUT
                    )
                    transferCompleteJob.await()

                    // Progress.isTransferComplete does not guarantee that changes are integrated and
                    // visible in the realm
                    realm.syncSession.downloadAllServerChanges(TIMEOUT)
                    assertEquals(
                        TEST_SIZE * (i + 1),
                        realm.query<SyncObjectWithAllTypes>().find().size
                    )
                }
            }
        }
    }

    @Test
    fun downloadProgressListener_indefinitely() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogIn())).use { uploadRealm ->
            uploadRealm.writeSampleData(TEST_SIZE, timeout = TIMEOUT)

            Realm.open(createSyncConfig(app.createUserAndLogin())).use { realm ->
                val flow = realm.syncSession
                    .progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
                    .completionCounter()

                withTimeout(TIMEOUT) {
                    flow.takeWhile { completed -> completed < 3 }
                        .collect { _ ->
                            uploadRealm.writeSampleData(
                                TEST_SIZE,
                                timeout = TIMEOUT
                            )
                        }
                }
            }
        }
    }

    @Test
    fun uploadProgressListener_changesOnly() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogin())).use { realm ->
            for (i in 0..3) {
                val task = async {
                    realm.syncSession.progressAsFlow(Direction.UPLOAD, ProgressMode.CURRENT_CHANGES)
                        .run {
                            last().let {
                                assertTrue(it.isTransferComplete)
                                assertEquals(1.0, it.estimate)
                            }
                        }
                }
                realm.writeSampleData(TEST_SIZE, timeout = TIMEOUT)
                withTimeout(TIMEOUT) {
                    task.await()
                }
            }
        }
    }

    @Test
    fun uploadProgressListener_indefinitely() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogin())).use { realm ->
            val flow = realm.syncSession.progressAsFlow(Direction.UPLOAD, ProgressMode.INDEFINITELY)
                .completionCounter()

            withTimeout(TIMEOUT) {
                flow.takeWhile { completed -> completed < 3 }
                    .collect { _ ->
                        realm.writeSampleData(TEST_SIZE)
                    }
            }
        }
    }

    @Test
    fun worksAfterExceptions() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogIn())).use { realm ->
            realm.writeSampleData(TEST_SIZE, timeout = TIMEOUT)
        }

        Realm.open(createSyncConfig(app.createUserAndLogin())).use { realm ->
            val flow = realm.syncSession.progressAsFlow(Direction.UPLOAD, ProgressMode.INDEFINITELY)
            assertFailsWith<RuntimeException> {
                flow.collect {
                    @Suppress("TooGenericExceptionThrown")
                    throw RuntimeException("Crashing progress flow")
                }
            }

            withTimeout(TIMEOUT) {
                flow.first { it.isTransferComplete }
            }
        }
    }

    @Test
    fun worksAfterCancel() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogIn())).use { writerRealm ->
            Realm.open(createSyncConfig(app.createUserAndLogin())).use { realm ->
                // Setup a flow that we are just going to cancel
                val flow = realm.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)

                supervisorScope {
                    val mutex = Mutex(true)
                    val task = async {
                        flow.collect {
                            mutex.unlock()
                        }
                    }
                    // Await the flow actually being active, this requires actual data transfer as
                    // we arent guaranteed any initial events.
                    writerRealm.writeSampleData(TEST_SIZE, timeout = TIMEOUT)
                    mutex.lock()
                    task.cancel()
                }

                // Verify that progress listeners still work
                withTimeout(TIMEOUT) {
                    val task = async { flow.first { it.isTransferComplete } }
                    // Trigger data transfer to ensure we get an event at some point
                    writerRealm.writeSampleData(TEST_SIZE, timeout = TIMEOUT)
                    task.await()
                }
            }
        }
    }

    @Test
    fun completesOnClose() = runBlocking {
        val channel = TestChannel<Boolean>(capacity = 5, onBufferOverflow = BufferOverflow.DROP_OLDEST, failIfBufferIsEmptyOnCancel = false)
        TestApp("completesOnClose", DefaultFlexibleSyncAppInitializer).use { app ->
            val user = app.createUserAndLogIn()
            val realm = Realm.open(createSyncConfig(user))
            try {
                val flow = realm.syncSession.progressAsFlow(Direction.UPLOAD, ProgressMode.INDEFINITELY)
                val job = async {
                    withTimeout(30.seconds) {
                        flow.collect {
                            channel.trySend(true)
                        }
                    }
                }
                realm.writeSampleData(TEST_SIZE, timeout = TIMEOUT)
                // Wait for Flow to start, so we do not close the Realm before
                // `flow.collect()` can be called.
                channel.receiveOrFail()
                realm.close()
                job.await()
            } finally {
                channel.close()
                if (!realm.isClosed()) {
                    realm.close()
                }
            }
        }
    }

    private suspend fun Realm.writeSampleData(count: Int, timeout: Duration? = null) {
        repeat(count) {
            write {
                copyToRealm(
                    SyncObjectWithAllTypes().apply {
                        stringField = getTestPartitionValue()
                        binaryField = Random.nextBytes(100)
                    }
                )
            }
        }
        timeout?.let {
            assertTrue { syncSession.uploadAllLocalChanges(timeout) }
        }
    }

    // Operator that will return a flow that emits an increasing integer on each completion event
    private fun Flow<Progress>.completionCounter(): Flow<Int> =
        filter { it.isTransferComplete }
            .buffer(5, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            .scan(0) { accumulator, _ ->
                accumulator + 1
            }

    private fun createSyncConfig(
        user: User,
    ): SyncConfiguration {
        return SyncConfiguration.Builder(user, FLEXIBLE_SYNC_SCHEMA)
            .initialSubscriptions {
                add(it.query<SyncObjectWithAllTypes>("stringField = $0", getTestPartitionValue()))
            }
            .build()
    }

    private fun getTestPartitionValue(): String {
        if (!this::partitionValue.isInitialized) {
            fail("Test not setup correctly. Partition value is missing")
        }
        return partitionValue
    }
}
