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
import io.realm.kotlin.entities.sync.flx.FlexChildObject
import io.realm.kotlin.entities.sync.flx.FlexEmbeddedObject
import io.realm.kotlin.entities.sync.flx.FlexParentObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.exceptions.DownloadingRealmTimeOutException
import io.realm.kotlin.mongodb.subscriptions
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.use
import kotlinx.atomicfu.atomic
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        // Generate random section to allow replays of unit tests
        val randomSection = Random.nextInt()

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

    @Suppress("LongMethod")
    @Test
    fun roundTripLinkedAndEmbeddedObjects() = runBlocking {
        val randomSection = Random.nextInt() // Generate random name to allow replays of unit tests

        // Upload data from user 1
        val user1 = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        val config1 = SyncConfiguration.create(user1, defaultSchema)
        Realm.open(config1).use { realm1 ->
            val subs = realm1.subscriptions.update {
                add(realm1.query<FlexParentObject>("section = $0", randomSection))
                add(realm1.query<FlexChildObject>("section = $0", randomSection))
            }
            assertTrue(subs.waitForSynchronization())
            realm1.write {
                copyToRealm(
                    FlexParentObject(randomSection).apply {
                        name = "red"
                        child = FlexChildObject().apply {
                            section = randomSection
                            name = "redChild"
                        }
                        embedded = FlexEmbeddedObject().apply {
                            embeddedName = "redEmbedded"
                        }
                    }
                )
                copyToRealm(
                    FlexParentObject(randomSection).apply {
                        name = "blue"
                        child = FlexChildObject().apply {
                            section = randomSection
                            name = "blueChild"
                        }
                        embedded = FlexEmbeddedObject().apply {
                            embeddedName = "blueEmbedded"
                        }
                    }
                )
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
                add(realm.query<FlexChildObject>("section = $0", randomSection))
            }
            .waitForInitialRemoteData(timeout = 1.minutes)
            .build()

        Realm.open(config2).use { realm2 ->
            assertEquals(1, realm2.query<FlexParentObject>().count().find())
            assertEquals(2, realm2.query<FlexChildObject>().count().find())
            // Embedded objects are pulled down as part of their parents
            assertEquals(1, realm2.query<FlexEmbeddedObject>().count().find())
            val obj = realm2.query<FlexParentObject>().first().find()!!
            assertEquals("blueChild", obj.child!!.name)
            assertEquals("blueEmbedded", obj.embedded!!.embeddedName)
        }
    }
}
