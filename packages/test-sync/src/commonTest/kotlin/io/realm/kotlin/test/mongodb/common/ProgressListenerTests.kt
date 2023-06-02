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
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.sync.Direction
import io.realm.kotlin.mongodb.sync.Progress
import io.realm.kotlin.mongodb.sync.ProgressMode
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TEST_APP_PARTITION
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.use
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val TEST_SIZE = 500
private val TIMEOUT = 30.seconds

private val schema = setOf(SyncObjectWithAllTypes::class)

class ProgressListenerTests {

    private lateinit var app: TestApp
    private lateinit var partitionValue: String

    @BeforeTest
    fun setup() {
        app = TestApp(appName = TEST_APP_PARTITION)
        partitionValue = ObjectId().toString()
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
                    uploadRealm.writeSampleData(
                        TEST_SIZE,
                        idOffset = TEST_SIZE * i,
                        timeout = TIMEOUT
                    )
                    // We are not sure when the realm actually knows of the remote changes and consider
                    // them current, so wait a bit
                    delay(10.seconds)
                    realm.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.CURRENT_CHANGES)
                        .run {
                            withTimeout(TIMEOUT) {
                                assertTrue(last().isTransferComplete)
                            }
                        }
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
                val flow = realm.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
                    .completionCounter()
                withTimeout(TIMEOUT) {
                    flow.takeWhile { completed -> completed < 3 }
                        .collect { completed ->
                            uploadRealm.writeSampleData(
                                TEST_SIZE,
                                idOffset = (completed + 1) * TEST_SIZE,
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
                realm.writeSampleData(TEST_SIZE, idOffset = TEST_SIZE * i, timeout = TIMEOUT)
                realm.syncSession.progressAsFlow(Direction.UPLOAD, ProgressMode.CURRENT_CHANGES).run {
                    withTimeout(TIMEOUT) {
                        assertTrue(last().isTransferComplete)
                    }
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
                    .collect { completed ->
                        realm.writeSampleData(TEST_SIZE, idOffset = (completed + 1) * TEST_SIZE)
                        realm.syncSession.uploadAllLocalChanges()
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
            assertFailsWith<RuntimeException> {
                realm.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
                    .collect {
                        @Suppress("TooGenericExceptionThrown")
                        throw RuntimeException("Crashing progress flow")
                    }
            }

            val flow = realm.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
            withTimeout(TIMEOUT) {
                flow.first { it.isTransferComplete }
            }
        }
    }

    @Test
    fun worksAfterCancel() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogIn())).use { realm ->
            realm.writeSampleData(TEST_SIZE, timeout = TIMEOUT)
        }

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
                // Await the flow actually being active
                mutex.lock()
                task.cancel()
            }

            // Verify that progress listeners still work
            realm.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.CURRENT_CHANGES).run {
                withTimeout(TIMEOUT) {
                    flow.first { it.isTransferComplete }
                }
            }
        }
    }

    @Test
    fun triggerImmediatelyWhenRegistered() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogIn())).use { realm ->
            withTimeout(10.seconds) {
                // Ensure that all data is already synced
                assertTrue { realm.syncSession.uploadAllLocalChanges() }
                assertTrue { realm.syncSession.downloadAllServerChanges() }
                // Ensure that progress listeners are triggered at least one time even though there
                // is no data
                realm.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.CURRENT_CHANGES).first()
                realm.syncSession.progressAsFlow(Direction.UPLOAD, ProgressMode.CURRENT_CHANGES).first()
                realm.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY).first()
                realm.syncSession.progressAsFlow(Direction.UPLOAD, ProgressMode.INDEFINITELY).first()
            }
        }
    }

    @Test
    fun throwsOnFlexibleSync() = runBlocking {
        val app = TestApp(TEST_APP_FLEX)
        val user = app.createUserAndLogIn()
        val configuration: SyncConfiguration = SyncConfiguration.create(user, schema)
        Realm.open(configuration).use { realm ->
            assertFailsWithMessage<UnsupportedOperationException>(
                "Progress listeners are not supported for Flexible Sync"
            ) {
                realm.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.CURRENT_CHANGES)
            }
        }
    }

    @Test
    fun completesOnClose() = runBlocking {
        val app = TestApp(TEST_APP_PARTITION)
        val user = app.createUserAndLogIn()
        val realm = Realm.open(createSyncConfig(user))
        try {
            val flow = realm.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
            val job = async {
                withTimeout(10.seconds) {
                    flow.collect { }
                }
            }
            realm.close()
            job.await()
        } finally {
            if (!realm.isClosed()) {
                realm.close()
            }
        }
    }

    private suspend fun Realm.writeSampleData(count: Int, idOffset: Int = 0, timeout: Duration? = null) {
        write {
            for (i in idOffset until count + idOffset) {
                copyToRealm(SyncObjectWithAllTypes().apply { stringField = "Object $i" })
            }
        }
        timeout?.let {
            assertTrue { syncSession.uploadAllLocalChanges(timeout) }
        }
    }

    // Operator that will return a flow that emits an incresing integer on each completion event
    private fun Flow<Progress>.completionCounter(): Flow<Int> =
        filter { it.isTransferComplete }
            .distinctUntilChanged()
            // Increment completed count if we are done transferring and the amount of bytes has
            // increased
            .scan(0UL to 0) { (bytes, completed), progress ->
                if (progress.isTransferComplete && progress.transferableBytes > bytes) {
                    (progress.transferredBytes to completed + 1)
                } else {
                    (bytes to completed)
                }
            }
            .drop(1)
            .map { (_, completed) -> completed }

    private fun createSyncConfig(
        user: User,
        partitionValue: String = getTestPartitionValue()
    ): SyncConfiguration {
        return SyncConfiguration.Builder(user, partitionValue, schema)
            .build()
    }

    private fun getTestPartitionValue(): String {
        if (!this::partitionValue.isInitialized) {
            fail("Test not setup correctly. Partition value is missing")
        }
        return partitionValue
    }
}
