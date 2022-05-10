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
package io.realm.mongodb.sync

import io.realm.Realm
import io.realm.internal.platform.runBlocking
import io.realm.mongodb.User
import io.realm.test.mongodb.TEST_APP_3
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.createUserAndLogIn
import io.realm.test.util.TestHelper
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FlexibleSyncConfigurationTests {

    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        app = TestApp(appName = TEST_APP_3)
        // ServerAdmin(app).enableFlexibleSync() // Currrently required because importing doesn't work
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user = runBlocking {
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
    fun with() {
        val user: User = createTestUser()
        val config = SyncConfiguration.with(user, setOf())
        assertTrue(config.isFlexibleSyncConfiguration())
        assertFalse(config.isPartitionBasedSyncConfiguration())
    }

    @Test
    fun equals() {
        val user: User = createTestUser()
        val config: SyncConfiguration = SyncConfiguration.with(user, setOf())
        assertEquals(config, config)
    }

    @Test
    fun equals_same() {
        val user: User = createTestUser()
        val config1: SyncConfiguration = SyncConfiguration.Builder(user, setOf()).build()
        val config2: SyncConfiguration = SyncConfiguration.Builder(user, setOf()).build()
        assertEquals(config1, config2)
    }

    @Test
    fun equals_not() {
        val user1: User = createTestUser()
        val user2: User = createTestUser()
        val config1: SyncConfiguration = SyncConfiguration.Builder(user1, setOf()).build()
        val config2: SyncConfiguration = SyncConfiguration.Builder(user2, setOf()).build()
        assertNotEquals(config1, config2)
    }

    @Test
    fun hashCode_equal() {
        val user: User = createTestUser()
        val config: SyncConfiguration = SyncConfiguration.with(user, setOf())
        assertEquals(config.hashCode(), config.hashCode())
    }

    @Test
    fun hashCode_notEquals() {
        val user1: User = createTestUser()
        val user2: User = createTestUser()
        val config1: SyncConfiguration = SyncConfiguration.with(user1, setOf())
        val config2: SyncConfiguration = SyncConfiguration.with(user2, setOf())
        assertNotEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun toString_nonEmpty() {
        val user: User = createTestUser()
        val config: SyncConfiguration = SyncConfiguration.with(user, setOf())
        TODO("Add something here")
    }

    // @Test
    // fun getPartitionValueThrows() {
    //     val user: User = createTestUser(app)
    //     val config: SyncConfiguration = SyncConfiguration.defaultConfig(user)
    //     assertFailsWith<IllegalStateException> { config.partitionValue }
    // }

    @Test
    fun defaultPath() {
        val user: User = createTestUser()
        val config: SyncConfiguration = SyncConfiguration.with(user, setOf())
        assertTrue(config.path.endsWith("/default.realm"), "Path is: ${config.path}")
    }

    @Test
    fun initialSubscriptions() {
        val user: User = createTestUser()
        val handler: MutableSubscriptionSet.(realm: Realm) -> Unit = { /* Do nothing */ }
        val config: SyncConfiguration = SyncConfiguration.Builder(user, setOf())
            .initialSubscriptions(rerunOnOpen = true, handler)
            .build()

        assertEquals<InitialSubscriptionsCallback>(handler, config.initialSubscriptionsCallback!!)
        assertTrue(config.rerunInitialSubscriptions)
    }

    @Test
    fun rerunInitialSubscriptions() {
        TODO()
    }

    // @Test
    // fun defaultClientResetStrategy() {
    //     val user: User = createTestUser(app)
    //     val handler = SyncConfiguration.InitialFlexibleSyncSubscriptions { realm, subscriptions ->
    //         // Do nothing
    //     }
    //     val config: SyncConfiguration = SyncConfiguration.defaultConfig(user)
    //     assertTrue(config.syncClientResetStrategy is ManuallyRecoverUnsyncedChangesStrategy)
    // }

    @Test
    fun overrideDefaultPath() {
        val user: User = createTestUser()
        val config: SyncConfiguration = SyncConfiguration.Builder(user, setOf())
            .name("custom.realm")
            .build()
        assertTrue(config.path.endsWith("${app.configuration.appId}/${user.identity}/custom.realm"), "Path is: ${config.path}")
    }

    private fun createTestUser(): User = runBlocking {
        val (email, password) = TestHelper.randomEmail() to "password1234"
        app.createUserAndLogIn(email, password)
    }
}
