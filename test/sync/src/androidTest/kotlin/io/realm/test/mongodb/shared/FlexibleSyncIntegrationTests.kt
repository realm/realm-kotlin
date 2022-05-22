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

package io.realm.test.mongodb.shared

import io.realm.Realm
import io.realm.entities.sync.flx.FlexChildObject
import io.realm.entities.sync.flx.FlexParentObject
import io.realm.internal.platform.runBlocking
import io.realm.mongodb.subscriptions
import io.realm.mongodb.sync.SyncConfiguration
import io.realm.mongodb.syncSession
import io.realm.query
import io.realm.test.mongodb.TEST_APP_FLEX
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.createUserAndLogIn
import io.realm.test.util.TestHelper
import io.realm.test.util.useInContext
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Integration smoke tests for Flexible Sync. This is not intended to cover all cases, but just
 * test common scenarios.
 */
class FlexibleSyncIntegrationTests {

    private val defaultSchema = setOf(FlexParentObject::class, FlexChildObject::class)
    private lateinit var app: TestApp

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
        val config1 = SyncConfiguration.with(user1, defaultSchema)
        Realm.open(config1).useInContext { realm1 ->
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
        val config2 = SyncConfiguration.Builder(user2, defaultSchema).build()
        Realm.open(config2).useInContext { realm2 ->
            realm2.subscriptions.update { realm ->
                realm.query<FlexParentObject>(
                    "section = $0 AND name = $1", randomSection, "blue"
                ).subscribe()
            }.waitForSynchronization()
            assertEquals(1, realm2.query<FlexParentObject>().count().find())
        }
    }

    // FIXME Waiting for https://github.com/realm/realm-kotlin/issues/417
    // @Test
    // fun clientResetIfNoSubscriptionWhenWriting() = runBlocking {
    //     val channel = Channel<Boolean>(1)
    //     lateinit var realm: Realm
    //     val task: Deferred<FlexParentObject> = async {
    //         val user = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
    //         val config = SyncConfiguration.Builder(user, defaultSchema)
    //             // .syncClientResetStrategy { session, error ->
    //             //     assertTrue(error.toString(), error.message!!.contains("Client attempted a write that is outside of permissions or query filters"))
    //             //     // looperThread.testComplete()
    //             // }
    //             .build()
    //         realm = Realm.open(config)
    //         realm.write {
    //             copyToRealm(FlexParentObject().apply { name = "red" })
    //         }
    //     }
    //     try {
    //         assertTrue(channel.receive())
    //     } finally {
    //         channel.close()
    //         task.cancel()
    //         realm.close()
    //     }
    // }

    @Test
    fun dataIsDeletedWhenSubscriptionIsRemoved() = runBlocking {
        val randomSection = Random.nextInt() // Generate random section to allow replays of unit tests

        val user = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        val config = SyncConfiguration.Builder(user, defaultSchema).build()
        Realm.open(config).useInContext { realm ->
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
                val query = realm.query<FlexParentObject>("section = $0 AND name = 'red'", randomSection)
                add(query, "sub", updateExisting = true)
            }
            assertTrue(realm.subscriptions.waitForSynchronization(60.seconds))
            assertEquals(1, realm.query<FlexParentObject>().count().find())
        }
    }
}
