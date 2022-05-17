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

package io.realm.test.shared

import io.realm.Realm
import io.realm.internal.platform.runBlocking
import io.realm.mongodb.User
import io.realm.mongodb.exceptions.SyncException
import io.realm.mongodb.sync.ManuallyRecoverUnsyncedChangesStrategy
import io.realm.mongodb.sync.SyncConfiguration
import io.realm.mongodb.sync.SyncSession
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.asTestApp
import io.realm.test.mongodb.createUserAndLogIn
import io.realm.test.util.TestHelper
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SyncConfigurationTests {

    private lateinit var partitionValue: String
    private lateinit var app: TestApp
    private lateinit var user: User

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
            app.asTestApp.close()
        }
    }

    @Test
    fun user() {
        val config = SyncConfiguration.with(user, partitionValue, setOf())
        assertEquals(user, config.user)
    }

    @Test
    fun errorHandler() {
        val errorHandler: SyncSession.ErrorHandler = object : SyncSession.ErrorHandler {
            override fun onError(session: SyncSession, error: SyncException) {
                // no-op
            }
        }
        val config = SyncConfiguration.Builder(user, partitionValue, setOf())
            .errorHandler(errorHandler)
            .build()
        assertEquals(errorHandler, config.errorHandler)
    }

    @Test
    fun syncClientResetStrategy() {
        val resetHandler = object : ManuallyRecoverUnsyncedChangesStrategy {
            override fun onClientReset(session: SyncSession) {
                fail("Should not be called")
            }
        }
        val config = SyncConfiguration.Builder(user, partitionValue, setOf())
            .syncClientResetStrategy(resetHandler)
            .build()
        assertEquals(resetHandler, config.syncClientResetStrategy)
    }

    @Test
    fun syncClientResetStrategy_fromAppConfiguration() {
        val config = SyncConfiguration.Builder(user, partitionValue, setOf())
            .build()
        assertEquals(
            app.configuration.defaultSyncClientResetStrategy,
            config.syncClientResetStrategy
        )
    }

    @Test
    fun openRealm() {
        val config = SyncConfiguration.Builder(user, partitionValue, setOf())
            .build()
        val realm = Realm.open(config)
        val kajshd = 0
    }
}
