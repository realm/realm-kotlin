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
@file:Suppress("invisible_member", "invisible_reference") // Needed to call session.simulateError()

package io.realm.test.mongodb.shared

import io.realm.MutableRealm
import io.realm.Realm
import io.realm.TypedRealm
import io.realm.entities.sync.flx.FlexParentObject
import io.realm.internal.interop.sync.ProtocolClientErrorCode
import io.realm.internal.interop.sync.SyncErrorCodeCategory
import io.realm.internal.platform.fileExists
import io.realm.internal.platform.runBlocking
import io.realm.mongodb.User
import io.realm.mongodb.sync.ClientResetRequiredError
import io.realm.mongodb.sync.DiscardUnsyncedChangesStrategy
import io.realm.mongodb.sync.ManuallyRecoverUnsyncedChangesStrategy
import io.realm.mongodb.sync.SyncConfiguration
import io.realm.mongodb.sync.SyncSession
import io.realm.mongodb.syncSession
import io.realm.notifications.ResultsChange
import io.realm.query
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.createUserAndLogIn
import io.realm.test.util.TestHelper
import io.realm.test.util.use
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SyncClientResetIntegrationTests {

    private lateinit var partitionValue: String
    private lateinit var user: User
    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        partitionValue = TestHelper.randomPartitionValue()
        app = TestApp()
        val (email, password) = TestHelper.randomEmail() to "password1234"
        user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    private enum class ClientResetEvents {
        ON_BEFORE_RESET,
        ON_AFTER_RESET,
        ON_ERROR
    }

    @Test
    fun discardUnsyncedLocalChanges_success() {
        // Validate that the discard local strategy onBeforeReset and onAfterReset callbacks
        // are invoked successfully when a client reset is triggered.

        // Test with multiple Realm instances as they need to be updated automatically.

        val channel = Channel<ClientResetEvents>(2)

        val config = SyncConfiguration.Builder(
            user,
            partitionValue,
            schema = setOf(FlexParentObject::class) // Use a class that is present in the server schema
        ).syncClientResetStrategy(
            object : DiscardUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    // This realm contains something as we wrote an object while the session was paused
                    assertEquals(1, realm.query<FlexParentObject>().count().find())

                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                }

                override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                    // The before-Realm contains the object we wrote while the session was paused
                    assertEquals(1, before.query<FlexParentObject>().count().find())

                    // The after-Realm contains no objects
                    assertEquals(0, after.query<FlexParentObject>().count().find())

                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_AFTER_RESET)
                }

                override fun onError(session: SyncSession, error: ClientResetRequiredError) {
                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_ERROR)
                }
            }
        ).build()

        Realm.open(config).use { realm ->
            runBlocking {
                // This channel helps to validate that the Realm gets updated
                val objectChannel = Channel<ResultsChange<FlexParentObject>>(1)

                val job = async {
                    realm.query<FlexParentObject>().asFlow()
                        .collect {
                            objectChannel.trySend(it)
                        }
                }

                // No initial data
                assertEquals(0, objectChannel.receive().list.size)

                with(realm.syncSession) {
                    downloadAllServerChanges()

                    // Pause the session to avoid receiving any network interrupted error
                    pause()

                    app.triggerClientReset(user.identity) // Removes the client file triggering a Client reset

                    // Write something while the session is paused to make sure the before realm contains something
                    realm.writeBlocking {
                        copyToRealm(FlexParentObject())
                    }
                    assertEquals(1, objectChannel.receive().list.size)

                    // Resuming the session would trigger the client reset
                    resume()
                }

                // Validate that the client reset was triggered successfully
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())

                // TODO We must not need this. Force updating the instance pointer.
                realm.write { }

                // Validate Realm instance has been correctly updated
                assertEquals(0, objectChannel.receive().list.size)

                job.cancel()
            }
        }
    }

    @Test
    fun discardUnsyncedLocalChanges_failure() {
        // Validate that the discard local strategy onError callback is invoked successfully if
        // a client reset fails.

        val channel = Channel<ClientResetEvents>(1)

        val config = SyncConfiguration.Builder(
            user,
            partitionValue,
            schema = setOf(FlexParentObject::class) // Use a class that is present in the server's schema
        ).syncClientResetStrategy(object : DiscardUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                fail("Should not call onBeforeReset")
            }

            override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                fail("Should not call onAfterReset")
            }

            override fun onError(session: SyncSession, error: ClientResetRequiredError) {
                val originalFilePath = assertNotNull(error.originalFilePath)
                val recoveryFilePath = assertNotNull(error.recoveryFilePath)
                assertTrue(fileExists(originalFilePath))
                assertFalse(fileExists(recoveryFilePath))
                // Note, this error message is just the one created by ObjectStore for
                // testing the server will send a different message. This just ensures that
                // we don't accidentally modify or remove the message.
                assertEquals("Simulate Client Reset", error.detailedMessage)

                // Notify that this callback has been invoked
                channel.trySend(ClientResetEvents.ON_ERROR)
            }
        }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                val session = (realm.syncSession as io.realm.mongodb.internal.SyncSessionImpl)
                session.simulateError(
                    ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                    SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                )

                assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
            }
        }
    }

    @Test
    fun discardUnsyncedLocalChanges_success_attemptRecover() {
        // Attempts to recover data if a client reset is triggered.

        val channel = Channel<ClientResetEvents>(2)

        val config = SyncConfiguration.Builder(
            user,
            partitionValue,
            schema = setOf(FlexParentObject::class) // Use a class that is present in the server's schema
        ).syncClientResetStrategy(
            object : DiscardUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    // Do nothing

                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                }

                override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                    // The before-Realm contains the object we wrote while the session was paused
                    assertEquals(1, before.query<FlexParentObject>().count().find())

                    val obj = before.query<FlexParentObject>().first().find()!!
                    after.copyToRealm(obj)

                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_AFTER_RESET)
                }

                override fun onError(session: SyncSession, error: ClientResetRequiredError) {
                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_ERROR)
                }
            }
        ).build()

        Realm.open(config).use { realm ->
            runBlocking {
                // This channel helps to validate that the Realm gets updated
                val objectChannel = Channel<ResultsChange<FlexParentObject>>(1)

                val job = async {
                    realm.query<FlexParentObject>().asFlow()
                        .collect { it: ResultsChange<FlexParentObject> ->
                            objectChannel.trySend(it)
                        }
                }

                // No initial data
                assertEquals(0, objectChannel.receive().list.size)

                with(realm.syncSession) {
                    downloadAllServerChanges()

                    // Pause the session to avoid receiving any network interrupted error
                    pause()

                    app.triggerClientReset(user.identity) // Removes the client file triggering a Client reset

                    // Write something while the session is paused to make sure the before realm contains something
                    realm.writeBlocking {
                        copyToRealm(FlexParentObject())
                    }
                    assertEquals(1, objectChannel.receive().list.size)

                    // Resuming the session would trigger the client reset
                    resume()
                }

                // Validate that the client reset was triggered successfuly
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())

                // TODO We must not need this. Force updating the instance pointer.
                realm.write { }

                // Validate Realm instance has been correctly updated
                assertEquals(1, objectChannel.receive().list.size)

                job.cancel()
            }
        }
    }

    @Test
    fun defaultDiscardUnsyncedLocalChanges_validateLogNotifications() {
        // Validate that the default strategy notifies the client reset through the logs.
        // TODO WARN level?
    }

    // Check that we can execute the Client Reset in a discard local strategy.
    @Test
    fun errorHandler_discardLocalExecuteClientReset() = runBlocking {
        val channel = Channel<ClientResetEvents>(1)

        val config = SyncConfiguration.Builder(
            user,
            partitionValue,
            schema = setOf(FlexParentObject::class) // Use a class that is present in the server's schema
        ).syncClientResetStrategy(object : DiscardUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                fail("Should not call onBeforeReset")
            }

            override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                fail("Should not call onAfterReset")
            }

            override fun onError(session: SyncSession, error: ClientResetRequiredError) {
                val originalFilePath = assertNotNull(error.originalFilePath)
                val recoveryFilePath = assertNotNull(error.recoveryFilePath)
                assertTrue(fileExists(originalFilePath))
                assertFalse(fileExists(recoveryFilePath))

                error.executeClientReset()

                // Validate that files have been moved after explicit reset
                assertFalse(fileExists(originalFilePath))
                assertTrue(fileExists(recoveryFilePath))

                channel.trySend(ClientResetEvents.ON_ERROR)
            }
        }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                val session = (realm.syncSession as io.realm.mongodb.internal.SyncSessionImpl)
                session.simulateError(
                    ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                    SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                )

                assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
            }
        }
    }

    // Check that a Client Reset is correctly reported.
    @Test
    fun errorHandler_manualClientResetReported() = runBlocking {
        val channel = Channel<ClientResetEvents>(1)

        val config = SyncConfiguration.Builder(
            user,
            partitionValue,
            schema = setOf(FlexParentObject::class) // Use a class that is present in the server's schema
        ).syncClientResetStrategy(object : ManuallyRecoverUnsyncedChangesStrategy {
            override fun onClientReset(session: SyncSession, error: ClientResetRequiredError) {
                val originalFilePath = assertNotNull(error.originalFilePath)
                val recoveryFilePath = assertNotNull(error.recoveryFilePath)
                assertTrue(fileExists(originalFilePath))
                assertFalse(fileExists(recoveryFilePath))
                // Note, this error message is just the one created by ObjectStore for
                // testing the server will send a different message. This just ensures that
                // we don't accidentally modify or remove the message.
                assertEquals("Simulate Client Reset", error.detailedMessage)

                // Notify that this callback has been invoked
                channel.trySend(ClientResetEvents.ON_ERROR)
            }
        }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                val session = (realm.syncSession as io.realm.mongodb.internal.SyncSessionImpl)
                session.simulateError(
                    ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                    SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                )

                assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
            }
        }
    }

    // Check that we can execute the Client Reset in a manual strategy.
    @Test
    fun errorHandler_manuallyRecoverExecuteClientReset() = runBlocking {
        val channel = Channel<ClientResetEvents>(1)

        val config = SyncConfiguration.Builder(
            user,
            partitionValue,
            schema = setOf(FlexParentObject::class) // Use a class that is present in the server's schema
        ).syncClientResetStrategy(object : ManuallyRecoverUnsyncedChangesStrategy {
            override fun onClientReset(session: SyncSession, error: ClientResetRequiredError) {
                val originalFilePath = assertNotNull(error.originalFilePath)
                val recoveryFilePath = assertNotNull(error.recoveryFilePath)
                assertTrue(fileExists(originalFilePath))
                assertFalse(fileExists(recoveryFilePath))

                error.executeClientReset()

                // Validate that files have been moved after explicit reset
                assertFalse(fileExists(originalFilePath))
                assertTrue(fileExists(recoveryFilePath))

                channel.trySend(ClientResetEvents.ON_ERROR)
            }
        }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                val session = (realm.syncSession as io.realm.mongodb.internal.SyncSessionImpl)
                session.simulateError(
                    ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                    SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                )

                assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
            }
        }
    }
}
