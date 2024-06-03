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

package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.Realm
import io.realm.kotlin.entities.sync.SyncObjectWithAllTypes
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.sync.Direction
import io.realm.kotlin.mongodb.sync.Progress
import io.realm.kotlin.mongodb.sync.ProgressMode
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncSession
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.test.mongodb.TEST_APP_PARTITION
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.common.utils.uploadAllLocalChangesOrFail
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.mongodb.use
import io.realm.kotlin.test.util.TestChannel
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.test.util.use
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
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
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PBSProgressListenerTests {
    private val TEST_SIZE = 10
    private val TIMEOUT = 30.seconds

    private lateinit var app: TestApp
    private lateinit var partitionValue: String

    @BeforeTest
    fun setup() {
        RealmLog.setLevel(LogLevel.INFO)
        app = TestApp(this::class.simpleName, appName = TEST_APP_PARTITION)
        partitionValue = org.mongodb.kbson.ObjectId().toString()
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    @Ignore // https://github.com/realm/realm-core/issues/7627
    fun downloadProgressListener_changesOnly() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogIn())).use { uploadRealm ->
            // Verify that we:
            // - get a "transferComplete" event
            // - complete the flow, and
            // - that all objects are available afterwards
            Realm.open(createSyncConfig(app.createUserAndLogIn())).use { realm ->
                // Ensure that we can do consecutive CURRENT_CHANGES registrations
                repeat(3) { iteration ->
                    val transferCompleteJob = async {
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
                    realm.syncSession.runWhilePaused {
                        uploadRealm.writeSampleData(TEST_SIZE, timeout = TIMEOUT)
                    }

                    transferCompleteJob.await()

                    // Progress.isTransferComplete does not guarantee that changes are integrated and
                    // visible in the realm
                    realm.syncSession.downloadAllServerChanges(TIMEOUT)
                    assertEquals(
                        TEST_SIZE * (iteration + 1),
                        realm.query<SyncObjectWithAllTypes>().find().size
                    )
                }
            }
        }
    }

    @Test
    fun downloadProgressListener_indefinitely() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogIn())).use { uploadRealm ->
            Realm.open(createSyncConfig(app.createUserAndLogin())).use { realm ->
                val flow = realm.syncSession
                    .progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
                    .completionCounter()

                withTimeout(TIMEOUT) {
                    flow.takeWhile { completed -> completed < 3 }
                        .collect { completed ->
                            realm.syncSession.runWhilePaused {
                                uploadRealm.writeSampleData(
                                    TEST_SIZE,
                                    timeout = TIMEOUT
                                )
                            }
                        }
                }
            }
        }
    }

    @Test
    fun uploadProgressListener_changesOnly() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogin())).use { realm ->
            repeat(3) {
                realm.writeSampleData(TEST_SIZE, timeout = TIMEOUT)
                realm.syncSession.progressAsFlow(Direction.UPLOAD, ProgressMode.CURRENT_CHANGES)
                    .run {
                        withTimeout(TIMEOUT) {
                            last().let {
                                assertTrue(it.isTransferComplete)
                                assertEquals(1.0, it.estimate)
                            }
                        }
                    }
            }
        }
    }

    @Test
    fun uploadProgressListener_indefinitely() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogin())).use { realm ->
            val flow = realm.syncSession
                .progressAsFlow(Direction.UPLOAD, ProgressMode.INDEFINITELY)
                .completionCounter()

            withTimeout(TIMEOUT) {
                flow.takeWhile { completed -> completed < 3 }
                    .collect { _ ->
                        realm.syncSession.runWhilePaused {
                            realm.writeSampleData(TEST_SIZE)
                        }
                        realm.syncSession.uploadAllLocalChangesOrFail()
                    }
            }
        }
    }

    @Test
    @Ignore // https://github.com/realm/realm-core/issues/7627
    fun worksAfterExceptions() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogIn())).use { realm ->
            realm.writeSampleData(TEST_SIZE, timeout = TIMEOUT)
        }

        Realm.open(createSyncConfig(app.createUserAndLogin())).use { realm ->
            val flow = realm.syncSession
                .progressAsFlow(Direction.UPLOAD, ProgressMode.INDEFINITELY)

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
    @Ignore // https://github.com/realm/realm-core/issues/7627
    fun worksAfterCancel() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogIn())).use { realm ->
            realm.writeSampleData(TEST_SIZE, timeout = TIMEOUT)
        }

        Realm.open(createSyncConfig(app.createUserAndLogin())).use { realm ->
            // Setup a flow that we are just going to cancel
            val flow =
                realm.syncSession
                    .progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)

            supervisorScope {
                val mutex = Mutex(true)
                val task = async {
                    flow.collect {
                        mutex.unlock()
                    }
                }
                // Await the flow actually being active
                mutex.lock()
                task.cancel()
            }

            // Verify that progress listeners still work
            withTimeout(TIMEOUT) {
                flow.first { it.isTransferComplete }
            }
        }
    }

    @Test
    @Ignore // https://github.com/realm/realm-core/issues/7627
    fun triggerImmediatelyWhenRegistered() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogIn())).use { realm ->
            withTimeout(10.seconds) {
                // Ensure that all data is already synced
                realm.syncSession.uploadAllLocalChangesOrFail()
                assertTrue { realm.syncSession.downloadAllServerChanges() }
                // Ensure that progress listeners are triggered at least one time even though there
                // is no data
                realm.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.CURRENT_CHANGES)
                    .first()
                realm.syncSession.progressAsFlow(Direction.UPLOAD, ProgressMode.CURRENT_CHANGES)
                    .first()
                realm.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
                    .first()
                realm.syncSession.progressAsFlow(Direction.UPLOAD, ProgressMode.INDEFINITELY)
                    .first()
            }
        }
    }

    @Test
    fun completesOnClose() = runBlocking {
        val channel =
            TestChannel<Boolean>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        TestApp("completesOnClose", TEST_APP_PARTITION).use { app ->
            val user = app.createUserAndLogIn()
            val realm = Realm.open(createSyncConfig(user))
            try {
                val flow =
                    realm.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
                val job = async {
                    withTimeout(10.seconds) {
                        flow.collect {
                            channel.send(true)
                        }
                    }
                }
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
                    SyncObjectWithAllTypes()
                        .apply {
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
            .scan(0) { accumulator, _ ->
                accumulator + 1
            }

    private fun createSyncConfig(
        user: User,
        partitionValue: String = getTestPartitionValue(),
    ): SyncConfiguration {
        return SyncConfiguration.Builder(user, partitionValue, PARTITION_BASED_SCHEMA)
            .build()
    }

    private fun getTestPartitionValue(): String {
        if (!this::partitionValue.isInitialized) {
            fail("Test not setup correctly. Partition value is missing")
        }
        return partitionValue
    }

    private suspend fun SyncSession.runWhilePaused(block: suspend () -> Unit) {
        pause()
        block()
        resume()
    }
}
