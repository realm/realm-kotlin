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

import io.realm.kotlin.Configuration
import io.realm.kotlin.Realm
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.sync.InitialSubscriptionsCallback
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncMode
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import kotlinx.atomicfu.atomic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlexibleSyncConfigurationTests {

    private lateinit var app: App

    @BeforeTest
    fun setup() {
        app = TestApp(appName = TEST_APP_FLEX)
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.asTestApp.close()
        }
    }

    @Test
    fun with() {
        val user: User = app.asTestApp.createUserAndLogin()
        val config = SyncConfiguration.create(user, setOf())
        assertEquals(SyncMode.FLEXIBLE, config.syncMode)
        assertNull(config.initialRemoteData)
        assertNull(config.initialSubscriptions)
    }

    @Test
    fun equals() {
        val user: User = app.asTestApp.createUserAndLogin()
        val config: SyncConfiguration = SyncConfiguration.create(user, setOf())
        assertEquals(config, config)
    }

    @Test
    fun equals_same() {
        val user: User = app.asTestApp.createUserAndLogin()
        val config1: SyncConfiguration = SyncConfiguration.Builder(user, setOf()).build()
        val config2: SyncConfiguration = SyncConfiguration.Builder(user, setOf()).build()
        // TODO Currently we use default implementation for equals. This is different
        //  compared to Realm Java, but it is unclear if this is something that is worth
        //  implementing?
        assertNotEquals(config1, config2)
    }

    @Test
    fun equals_not() {
        val user1: User = app.asTestApp.createUserAndLogin()
        val user2: User = app.asTestApp.createUserAndLogin()
        val config1: SyncConfiguration = SyncConfiguration.Builder(user1, setOf()).build()
        val config2: SyncConfiguration = SyncConfiguration.Builder(user2, setOf()).build()
        assertNotEquals(config1, config2)
    }

    @Test
    fun hashCode_equal() {
        val user: User = app.asTestApp.createUserAndLogin()
        val config: SyncConfiguration = SyncConfiguration.create(user, setOf())
        assertEquals(config.hashCode(), config.hashCode())
    }

    @Test
    fun hashCode_notEquals() {
        val user1: User = app.asTestApp.createUserAndLogin()
        val user2: User = app.asTestApp.createUserAndLogin()
        val config1: SyncConfiguration = SyncConfiguration.create(user1, setOf())
        val config2: SyncConfiguration = SyncConfiguration.create(user2, setOf())
        assertNotEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun toString_nonEmpty() {
        val user: User = app.asTestApp.createUserAndLogin()
        val config: SyncConfiguration = SyncConfiguration.create(user, setOf())
        // TODO Currently we use default implementation for `toString()`. This is
        //  different compared to Realm Java, but it is unclear if this is something
        //  that is worth implementing?
        assertTrue(config.toString().startsWith("io.realm.kotlin.mongodb.internal.SyncConfigurationImpl@"), config.toString())
    }

    // @Test
    // fun getPartitionValueThrows() {
    //     val user: User = createTestUser(app)
    //     val config: SyncConfiguration = SyncConfiguration.defaultConfig(user)
    //     assertFailsWith<IllegalStateException> { config.partitionValue }
    // }

    @Test
    fun defaultPath() {
        val user: User = app.asTestApp.createUserAndLogin()
        val config: SyncConfiguration = SyncConfiguration.create(user, setOf())
        assertTrue(config.path.endsWith("/default.realm"), "Path is: ${config.path}")
    }

    @Test
    fun initialSubscriptions_throwsOnPartitionBasedConfig() {
        val user: User = app.asTestApp.createUserAndLogin()
        val partitionValue = TestHelper.randomPartitionValue()
        val configBuilder = SyncConfiguration.Builder(user, partitionValue, setOf())
        assertFailsWith<IllegalStateException> {
            configBuilder.initialSubscriptions { /* Do nothing */ }
        }
    }

    @Test
    fun initialSubscriptions() {
        val user: User = app.asTestApp.createUserAndLogin()
        val handler = InitialSubscriptionsCallback { /* Do nothing */ }
        val config: SyncConfiguration = SyncConfiguration.Builder(user, setOf())
            .initialSubscriptions(rerunOnOpen = true, handler)
            .build()
        assertEquals(handler, config.initialSubscriptions!!.callback)
        assertTrue(config.initialSubscriptions!!.rerunOnOpen)
    }

    @Test
    fun durability() {
        val user: User = app.asTestApp.createUserAndLogin()
        val config = SyncConfiguration.Builder(user, schema = setOf())
            .build()
        val inMemoryConfig = SyncConfiguration.Builder(user, schema = setOf())
            .inMemory()
            .build()
        assertEquals(Configuration.Durability.FULL, config.durability)
        assertEquals(Configuration.Durability.IN_MEMORY, inMemoryConfig.durability)
    }

    @Test
    fun initialSubscriptions_rerunOnOpen_false() {
        val user: User = app.asTestApp.createUserAndLogin()
        val counter = atomic(0)
        val config: SyncConfiguration = SyncConfiguration.Builder(user, setOf())
            .initialSubscriptions(rerunOnOpen = false) {
                counter.incrementAndGet()
            }
            .build()

        Realm.open(config).close()
        assertEquals(1, counter.value)
        Realm.open(config).close()
        assertEquals(1, counter.value)
    }

    @Test
    fun initialSubscriptions_rerunOnOpen_true() {
        val user: User = app.asTestApp.createUserAndLogin()
        val counter = atomic(0)
        val config: SyncConfiguration = SyncConfiguration.Builder(user, setOf())
            .initialSubscriptions(rerunOnOpen = true) {
                counter.incrementAndGet()
            }
            .build()

        Realm.open(config).close()
        assertEquals(1, counter.value)
        Realm.open(config).close()
        assertEquals(2, counter.value)
    }

    @Test
    @Ignore
    fun initialSubscriptions_failures_shouldDeleteRealm() {
        // See https://github.com/realm/realm-core/issues/5364
        // See https://github.com/realm/realm-kotlin/issues/851
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
        val user: User = app.asTestApp.createUserAndLogin()
        val config: SyncConfiguration = SyncConfiguration.Builder(user, setOf())
            .name("custom.realm")
            .build()
        assertTrue(config.path.endsWith("${app.configuration.appId}/${user.identity}/custom.realm"), "Path is: ${config.path}")
    }
}
