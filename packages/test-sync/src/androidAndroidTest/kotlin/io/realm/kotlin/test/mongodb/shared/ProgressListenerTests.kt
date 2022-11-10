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

package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.sync.Direction
import io.realm.kotlin.mongodb.sync.Progress
import io.realm.kotlin.mongodb.sync.ProgressMode
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TEST_APP_PARTITION
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
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

private const val TEST_SIZE = 5000
private val TIMEOUT = 10.seconds

private val schema = setOf(ProgressListenerTests.ProgressTestObject::class)

class ProgressListenerTests {

    private lateinit var app: TestApp
    private lateinit var partitionValue: String

    @BeforeTest
    fun setup() {
        app = TestApp(appName = TEST_APP_PARTITION)
        partitionValue = ObjectId.create().toString()
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun downloadProgressListener_changesOnly() = runBlocking {
        val uploadRealm = Realm.open(createSyncConfig(app.createUserAndLogIn()))

        // Verify that we:
        // - get a "transferComplete" event
        // - complete the flow, and
        // - that all objects are available afterwards
        Realm.open(createSyncConfig(app.createUserAndLogIn())).apply {
            // Ensure that we can do consecutive CURRENT_CHANGES registrations
            for (i in 0 until 3) {
                uploadRealm.writeSampleData(TEST_SIZE, idOffset = TEST_SIZE * i, timeout = TIMEOUT)
                // We are not sure when the realm actually knows of the remote changes and consider
                // them current, so wait a bit
                delay(1.seconds)
                syncSession.progress(Direction.DOWNLOAD, ProgressMode.CURRENT_CHANGES).run {
                    withTimeout(TIMEOUT) {
                        assertTrue(last().isTransferComplete)
                    }
                }
                // Progress.isTransferComplete does not guarantee that changes are integrated and
                // visible in the realm
                syncSession.downloadAllServerChanges(TIMEOUT)
                assertEquals(TEST_SIZE * (i + 1), query<ProgressTestObject>().find().size)
            }
        }.close()
        uploadRealm.close()
    }

    @Test
    fun downloadProgressListener_indefinitely() = runBlocking {
        val uploadRealm = Realm.open(createSyncConfig(app.createUserAndLogIn()))
        uploadRealm.writeSampleData(TEST_SIZE, timeout = TIMEOUT)

        Realm.open(createSyncConfig(app.createUserAndLogin())).apply {
            val flow = syncSession.progress(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
                .completionCounter()
            withTimeout(TIMEOUT) {
                flow.takeWhile { completed -> completed < 3 }
                    .collect { completed ->
                        with(uploadRealm) {
                            writeSampleData(TEST_SIZE, idOffset = (completed + 1) * TEST_SIZE, timeout = TIMEOUT)
                        }
                    }
            }
        }
        uploadRealm.close()
    }

    @Test
    fun uploadProgressListener_changesOnly() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogin())).apply {
            for (i in 0..3) {
                writeSampleData(TEST_SIZE, idOffset = TEST_SIZE * i, timeout = TIMEOUT)
                syncSession.progress(Direction.UPLOAD, ProgressMode.CURRENT_CHANGES).run {
                    withTimeout(TIMEOUT) {
                        assertTrue(last().isTransferComplete)
                    }
                }
            }
        }.close()
    }

    @Test
    fun uploadProgressListener_indefinitely() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogin())).apply {
            val flow = syncSession.progress(Direction.UPLOAD, ProgressMode.INDEFINITELY)
                .completionCounter()

            withTimeout(TIMEOUT) {
                flow.takeWhile { completed -> completed < 3 }
                    .collect { completed ->
                        writeSampleData(TEST_SIZE, idOffset = (completed + 1) * TEST_SIZE)
                        syncSession.uploadAllLocalChanges()
                    }
            }
        }.close()
    }

    @Test
    fun worksAfterExceptions() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogIn())).apply {
            writeSampleData(TEST_SIZE, timeout = TIMEOUT)
        }.close()

        Realm.open(createSyncConfig(app.createUserAndLogin())).apply {
            assertFailsWith<RuntimeException> {
                syncSession.progress(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
                    .collect {
                        @Suppress("TooGenericExceptionThrown")
                        throw RuntimeException("Crashing progress flow")
                    }
            }

            val flow = syncSession.progress(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
            withTimeout(TIMEOUT) {
                flow.first { it.isTransferComplete }
            }
        }.close()
    }

    @Test
    fun worksAfterCancel() = runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogIn())).apply {
            writeSampleData(TEST_SIZE, timeout = TIMEOUT)
        }.close()

        Realm.open(createSyncConfig(app.createUserAndLogin())).apply {
            // Setup a flow that we are just going to cancel
            val flow = syncSession.progress(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
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
            syncSession.progress(Direction.DOWNLOAD, ProgressMode.CURRENT_CHANGES).run {
                withTimeout(TIMEOUT) {
                    flow.first { it.isTransferComplete }
                }
            }
        }.close()
    }

    @Test
    fun triggerImmediatelyWhenRegistered() = kotlinx.coroutines.runBlocking {
        Realm.open(createSyncConfig(app.createUserAndLogIn())).apply {
            withTimeout(10000) {
                // Ensure that all data is already synced
                assertTrue { syncSession.uploadAllLocalChanges() }
                assertTrue { syncSession.downloadAllServerChanges() }
                // Ensure that progress listeners are triggered at least one time even though there
                // is no data
                syncSession.progress(Direction.DOWNLOAD, ProgressMode.CURRENT_CHANGES).first()
                syncSession.progress(Direction.UPLOAD, ProgressMode.CURRENT_CHANGES).first()
                syncSession.progress(Direction.DOWNLOAD, ProgressMode.INDEFINITELY).first()
                syncSession.progress(Direction.UPLOAD, ProgressMode.INDEFINITELY).first()
            }
        }.close()
    }

    @Test
    fun throwsOnFlexibleSync() = runBlocking {
        val app = TestApp(TEST_APP_FLEX)
        val user = app.createUserAndLogIn()
        val configuration: SyncConfiguration = SyncConfiguration.create(user, schema)
        Realm.open(configuration).apply {
            assertFailsWithMessage<UnsupportedOperationException>(
                "Progress listeners are not support for Flexible Sync"
            ) {
                syncSession.progress(Direction.DOWNLOAD, ProgressMode.CURRENT_CHANGES)
            }
        }.close()
    }

    private suspend fun Realm.writeSampleData(count: Int, idOffset: Int = 0, timeout: Duration? = null) {
        write {
            for (i in idOffset until count + idOffset) {
                copyToRealm(ProgressTestObject().apply { _id = "Object $i" })
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

    class ProgressTestObject : RealmObject {
        @PrimaryKey
        var _id: String = "DEFAULT"
    }
}
