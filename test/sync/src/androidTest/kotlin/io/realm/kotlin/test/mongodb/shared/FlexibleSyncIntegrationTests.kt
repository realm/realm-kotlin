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

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.entities.sync.flx.FlexChildObject
import io.realm.kotlin.entities.sync.flx.FlexEmbeddedObject
import io.realm.kotlin.entities.sync.flx.FlexParentObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.exceptions.ClientResetRequiredException
import io.realm.kotlin.mongodb.exceptions.DownloadingRealmTimeOutException
import io.realm.kotlin.mongodb.subscriptions
import io.realm.kotlin.mongodb.sync.DiscardUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncSession
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.use
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.channels.Channel
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Integration smoke tests for Flexible Sync. This is not intended to cover all cases, but just
 * test common scenarios.
 */
class FlexibleSyncIntegrationTests {

    private enum class ClientResetEvents {
        ON_BEFORE_RESET,
        ON_AFTER_RESET,
        ON_AFTER_RECOVERY,
        ON_AFTER_DISCARD,
        ON_ERROR
    }

    private lateinit var app: TestApp
    private val defaultSchema = setOf(
        FlexParentObject::class,
        FlexChildObject::class,
        FlexEmbeddedObject::class
    )

    @BeforeTest
    fun setup() {
        app = TestApp(appName = TEST_APP_FLEX)
        val (email, password) = TestHelper.randomEmail() to "password1234"
        runBlocking {
            app.createUserAndLogIn(email, password)
        }
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun downloadInitialData() = runBlocking {
        val randomSection = Random.nextInt() // Generate random name to allow replays of unit tests

        // Upload data from user 1
        val user1 = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        val config1 = SyncConfiguration.create(user1, defaultSchema)
        Realm.open(config1).use { realm1 ->
            val subs = realm1.subscriptions.update {
                add(realm1.query<FlexParentObject>("section = $0", randomSection))
            }
            assertTrue(subs.waitForSynchronization())
            realm1.write {
                copyToRealm(FlexParentObject(randomSection).apply { name = "red" })
                copyToRealm(FlexParentObject(randomSection).apply { name = "blue" })
            }
            realm1.syncSession.uploadAllLocalChanges()
        }

        // Download data from user 2
        val user2 = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        val config2 = SyncConfiguration.Builder(user2, defaultSchema)
            .initialSubscriptions { realm ->
                add(
                    realm.query<FlexParentObject>(
                        "section = $0 AND name = $1",
                        randomSection,
                        "blue"
                    )
                )
            }
            .waitForInitialRemoteData(timeout = 1.minutes)
            .build()

        Realm.open(config2).use { realm2 ->
            assertEquals(1, realm2.query<FlexParentObject>().count().find())
        }
    }

    @Test
    fun writeFailsIfNoSubscription() = runBlocking {
        val user = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        val config = SyncConfiguration.Builder(user, defaultSchema)
            .build()

        Realm.open(config).use { realm ->
            realm.writeBlocking {
                assertFailsWith<IllegalArgumentException> {
                    // This doesn't trigger a client reset event, it is caught by Core instead
                    copyToRealm(FlexParentObject().apply { name = "red" })
                }
            }
        }
    }

    @Test
    fun dataIsDeletedWhenSubscriptionIsRemoved() = runBlocking {
        val randomSection =
            Random.nextInt() // Generate random section to allow replays of unit tests

        val user = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        val config = SyncConfiguration.Builder(user, defaultSchema).build()
        Realm.open(config).use { realm ->
            realm.subscriptions.update {
                val query = realm.query<FlexParentObject>()
                    .query("section = $0", randomSection)
                    .query("(name = 'red' OR name = 'blue')")
                add(query, "sub")
            }
            assertTrue(realm.subscriptions.waitForSynchronization(60.seconds))
            realm.write {
                copyToRealm(FlexParentObject(randomSection).apply { name = "red" })
                copyToRealm(FlexParentObject(randomSection).apply { name = "blue" })
            }
            assertEquals(2, realm.query<FlexParentObject>().count().find())
            realm.subscriptions.update {
                val query =
                    realm.query<FlexParentObject>("section = $0 AND name = 'red'", randomSection)
                add(query, "sub", updateExisting = true)
            }
            assertTrue(realm.subscriptions.waitForSynchronization(60.seconds))
            assertEquals(1, realm.query<FlexParentObject>().count().find())
        }
    }

    @Test
    fun initialSubscriptions_timeOut() {
        val config = SyncConfiguration.Builder(app.currentUser!!, defaultSchema)
            .initialSubscriptions { realm ->
                repeat(10) {
                    add(realm.query<FlexParentObject>("section = $0", it))
                }
            }.waitForInitialRemoteData(1.nanoseconds)
            .build()

        assertFailsWith<DownloadingRealmTimeOutException> {
            Realm.open(config).use {
                fail("Realm should not have opened in time.")
            }
        }
    }

    // Make sure that if `rerunOnOpen` and `waitForInitialRemoteData` is set, we don't
    // open the Realm until all new subscription data is downloaded.
    @Test
    fun rerunningInitialSubscriptionsAndWaitForInitialRemoteData() = runBlocking {
        val randomSection = Random.nextInt() // Generate random name to allow replays of unit tests

        // Prepare some user data
        val user1 = app.createUserAndLogin()
        val config1 = SyncConfiguration.create(user1, defaultSchema)
        Realm.open(config1).use { realm ->
            realm.subscriptions.update {
                add(realm.query<FlexParentObject>("section = $0", randomSection))
            }.waitForSynchronization(30.seconds)

            realm.write {
                repeat(10) { counter ->
                    copyToRealm(
                        FlexParentObject().apply {
                            section = randomSection
                            name = "Name-$counter"
                        }
                    )
                }
            }
            realm.syncSession.uploadAllLocalChanges(30.seconds)
        }

        // User 2 opens a Realm twice
        val counter = atomic(0)
        val user2 = app.createUserAndLogin()
        val config2 = SyncConfiguration.Builder(user2, defaultSchema)
            .initialSubscriptions(rerunOnOpen = true) { realm ->
                add(
                    realm.query<FlexParentObject>(
                        "section = $0 AND name = $1",
                        randomSection,
                        "Name-${counter.getAndIncrement()}"
                    )
                )
            }
            .waitForInitialRemoteData(30.seconds)
            .build()

        Realm.open(config2).use { realm ->
            assertEquals(1, realm.query<FlexParentObject>().count().find())
        }
        Realm.open(config2).use { realm ->
            assertEquals(2, realm.query<FlexParentObject>().count().find())
        }
    }

    // ---------------------------------------------------------------------------------------
    // DiscardUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun discardUnsyncedChangesStrategy_reported() = runBlocking {
        val channel = Channel<ClientResetEvents>(2)
        val section = Random.nextInt()
        val user = app.createUserAndLogin()

        val config = SyncConfiguration.Builder(app.currentUser!!, defaultSchema)
            .initialSubscriptions { realm ->
                realm.query<FlexParentObject>(
                    "section = $0 AND name = $1",
                    section,
                    "blue"
                ).also { add(it) }
            }.waitForInitialRemoteData(timeout = 1.minutes)
            .syncClientResetStrategy(object : DiscardUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    assertEquals(1, realm.query<FlexParentObject>().count().find())
                    channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                }

                override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                    assertEquals(1, before.query<FlexParentObject>().count().find())
                    assertEquals(0, after.query<FlexParentObject>().count().find())

                    // Validate we can move data to the reset Realm
                    after.copyToRealm(
                        FlexParentObject().apply {
                            assertNotNull(
                                before.query<FlexParentObject>().first().find()
                            ).let {
                                // Perform manual copy
                                // see https://github.com/realm/realm-kotlin/issues/868
                                this._id = it._id
                                this.section = it.section
                                this.name = it.name
                                this.age = it.age
                            }
                        }
                    )
                    assertEquals(1, after.query<FlexParentObject>().count().find())
                    channel.trySend(ClientResetEvents.ON_AFTER_RESET)
                }

                override fun onError(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    fail("This test case was not supposed to trigger DiscardUnsyncedChangesStrategy::onError()")
                }
            }).build()

        Realm.open(config).use { realm ->
            app.triggerClientReset(user.identity, realm.syncSession) {
                realm.writeBlocking {
                    copyToRealm(FlexParentObject(section).apply { name = "blue" })
                }
                assertEquals(1, realm.query<FlexParentObject>().count().find())
            }

            // Validate that the client reset was triggered successfully
            assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
            assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())
        }
    }

    // @Test
    // fun discardUnsyncedChangesStrategy_discards_attemptRecover() {
    //     // Attempts to recover data if a client reset is triggered.
    //     val channel = Channel<ClientResetEvents>(2)
    //     val config = SyncConfiguration.Builder(
    //         user,
    //         partitionValue,
    //         schema = setOf(SyncPerson::class)
    //     ).syncClientResetStrategy(
    //         object : DiscardUnsyncedChangesStrategy {
    //             override fun onBeforeReset(realm: TypedRealm) {
    //                 // Notify that this callback has been invoked
    //                 channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
    //             }
    //
    //             override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
    //                 // The before-Realm contains the object we wrote while the session was paused
    //                 assertEquals(1, before.query<SyncPerson>().count().find())
    //
    //                 // Perform manual copy
    //                 // see https://github.com/realm/realm-kotlin/issues/868
    //                 val obj = before.query<SyncPerson>().first().find()!!
    //                 after.copyToRealm(
    //                     SyncPerson().apply {
    //                         this._id = obj._id
    //                         this.age = obj.age
    //                         this.firstName = obj.firstName
    //                         this.lastName = obj.lastName
    //                     }
    //                 )
    //
    //                 // Notify that this callback has been invoked
    //                 channel.trySend(ClientResetEvents.ON_AFTER_RESET)
    //             }
    //
    //             override fun onError(
    //                 session: SyncSession,
    //                 exception: ClientResetRequiredException
    //             ) {
    //                 // Notify that this callback has been invoked
    //                 channel.trySend(ClientResetEvents.ON_ERROR)
    //             }
    //         }
    //     ).build()
    //
    //     Realm.open(config).use { realm ->
    //         runBlocking {
    //             // This channel helps to validate that the Realm gets updated
    //             val objectChannel = Channel<ResultsChange<SyncPerson>>(1)
    //
    //             val job = async {
    //                 realm.query<SyncPerson>().asFlow()
    //                     .collect { it: ResultsChange<SyncPerson> ->
    //                         objectChannel.trySend(it)
    //                     }
    //             }
    //
    //             // No initial data
    //             assertEquals(0, objectChannel.receive().list.size)
    //
    //             app.triggerClientReset(user.identity, realm.syncSession) {
    //                 // Write something while the session is paused to make sure the before realm contains something
    //                 realm.writeBlocking {
    //                     copyToRealm(SyncPerson())
    //                 }
    //                 assertEquals(1, objectChannel.receive().list.size)
    //             }
    //
    //             // Validate that the client reset was triggered successfuly
    //             assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
    //             assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())
    //
    //             // TODO We must not need this. Force updating the instance pointer.
    //             realm.write { }
    //
    //             // Validate Realm instance has been correctly updated
    //             assertEquals(1, objectChannel.receive().list.size)
    //
    //             job.cancel()
    //         }
    //     }
    // }
    //
    // @Test
    // fun discardUnsyncedChangesStrategy_failure() {
    //     // Validate that the discard local strategy onError callback is invoked successfully if
    //     // a client reset fails.
    //     val channel = Channel<ClientResetEvents>(1)
    //     val config = SyncConfiguration.Builder(
    //         user,
    //         partitionValue,
    //         schema = setOf(SyncPerson::class)
    //     ).syncClientResetStrategy(object : DiscardUnsyncedChangesStrategy {
    //         override fun onBeforeReset(realm: TypedRealm) {
    //             fail("Should not call onBeforeReset")
    //         }
    //
    //         override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
    //             fail("Should not call onAfterReset")
    //         }
    //
    //         override fun onError(session: SyncSession, exception: ClientResetRequiredException) {
    //             val originalFilePath = assertNotNull(exception.originalFilePath)
    //             val recoveryFilePath = assertNotNull(exception.recoveryFilePath)
    //             assertTrue(fileExists(originalFilePath))
    //             assertFalse(fileExists(recoveryFilePath))
    //             // Note, this error message is just the one created by ObjectStore for
    //             // testing the server will send a different message. This just ensures that
    //             // we don't accidentally modify or remove the message.
    //             assertEquals(
    //                 "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed",
    //                 exception.message
    //             )
    //
    //             // Notify that this callback has been invoked
    //             channel.trySend(ClientResetEvents.ON_ERROR)
    //         }
    //     }).build()
    //
    //     Realm.open(config).use { realm ->
    //         runBlocking {
    //             with(realm.syncSession as SyncSessionImpl) {
    //                 simulateError(
    //                     ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
    //                     SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
    //                 )
    //
    //                 assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
    //             }
    //         }
    //     }
    // }
    //
    // @Test
    // fun discardUnsyncedChangesStrategy_executeClientReset() = runBlocking {
    //     val channel = Channel<ClientResetEvents>(1)
    //     val config = SyncConfiguration.Builder(
    //         user,
    //         partitionValue,
    //         schema = setOf(SyncPerson::class)
    //     ).syncClientResetStrategy(object : DiscardUnsyncedChangesStrategy {
    //         override fun onBeforeReset(realm: TypedRealm) {
    //             fail("Should not call onBeforeReset")
    //         }
    //
    //         override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
    //             fail("Should not call onAfterReset")
    //         }
    //
    //         override fun onError(session: SyncSession, exception: ClientResetRequiredException) {
    //             val originalFilePath = assertNotNull(exception.originalFilePath)
    //             val recoveryFilePath = assertNotNull(exception.recoveryFilePath)
    //             assertTrue(fileExists(originalFilePath))
    //             assertFalse(fileExists(recoveryFilePath))
    //
    //             exception.executeClientReset()
    //
    //             // Validate that files have been moved after explicit reset
    //             assertFalse(fileExists(originalFilePath))
    //             assertTrue(fileExists(recoveryFilePath))
    //
    //             channel.trySend(ClientResetEvents.ON_ERROR)
    //         }
    //     }).build()
    //
    //     Realm.open(config).use { realm ->
    //         runBlocking {
    //             with(realm.syncSession as SyncSessionImpl) {
    //                 simulateError(
    //                     ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
    //                     SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
    //                 )
    //
    //                 assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
    //             }
    //         }
    //     }
    // }
    //
    // @Test
    // fun discardUnsyncedChangesStrategy_userExceptionCaptured_onBeforeReset() {
    //     // Validates that any user exception during the automatic client reset is properly captured.
    //     val channel = Channel<ClientResetEvents>(3)
    //     val config = SyncConfiguration.Builder(
    //         user,
    //         partitionValue,
    //         schema = setOf(SyncPerson::class)
    //     ).syncClientResetStrategy(
    //         object : DiscardUnsyncedChangesStrategy {
    //             override fun onBeforeReset(realm: TypedRealm) {
    //                 // Notify that this callback has been invoked
    //                 channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
    //                 throw IllegalStateException("User exception")
    //             }
    //
    //             override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
    //                 // Send event anyways so that the asserts outside would fail
    //                 channel.trySend(ClientResetEvents.ON_AFTER_RESET)
    //             }
    //
    //             override fun onError(
    //                 session: SyncSession,
    //                 exception: ClientResetRequiredException
    //             ) {
    //                 // Notify that this callback has been invoked
    //                 assertEquals(
    //                     "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed",
    //                     exception.message
    //                 )
    //                 channel.trySend(ClientResetEvents.ON_ERROR)
    //             }
    //         }
    //     ).build()
    //
    //     Realm.open(config).use { realm ->
    //         runBlocking {
    //             app.triggerClientReset(user.identity, realm.syncSession)
    //
    //             // Validate that the client reset was triggered successfully
    //             assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
    //             assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
    //         }
    //     }
    // }
    //
    // @Test
    // fun discardUnsyncedChangesStrategy_userExceptionCaptured_onAfterReset() {
    //     // Validates that any user exception during the automatic client reset is properly captured.
    //     val channel = Channel<ClientResetEvents>(3)
    //     val config = SyncConfiguration.Builder(
    //         user,
    //         partitionValue,
    //         schema = setOf(SyncPerson::class)
    //     ).syncClientResetStrategy(
    //         object : DiscardUnsyncedChangesStrategy {
    //             override fun onBeforeReset(realm: TypedRealm) {
    //                 // Notify that this callback has been invoked
    //                 channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
    //             }
    //
    //             override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
    //                 // Notify that this callback has been invoked
    //                 channel.trySend(ClientResetEvents.ON_AFTER_RESET)
    //                 throw IllegalStateException("User exception")
    //             }
    //
    //             override fun onError(
    //                 session: SyncSession,
    //                 exception: ClientResetRequiredException
    //             ) {
    //                 // Notify that this callback has been invoked
    //                 assertEquals(
    //                     "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed",
    //                     exception.message
    //                 )
    //                 channel.trySend(ClientResetEvents.ON_ERROR)
    //             }
    //         }
    //     ).build()
    //
    //     Realm.open(config).use { realm ->
    //         runBlocking {
    //             app.triggerClientReset(user.identity, realm.syncSession)
    //
    //             // Validate that the client reset was triggered successfully
    //             assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
    //             assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())
    //             assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
    //         }
    //     }
    // }
}
