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

package io.realm.kotlin.test

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.entities.sync.flx.FlexParentObject
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.ClientResetRequiredException
import io.realm.kotlin.mongodb.sync.DiscardUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncSession
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.test.mongodb.TESTAPP_PARTITION
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.use
import kotlinx.coroutines.channels.Channel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// TODO move to shared SyncClientResetIntegrationTests once this is fixed
// https://github.com/realm/realm-kotlin/issues/867
class SyncClientResetIntegrationJVMTests {

    private enum class ClientResetEvents {
        ON_BEFORE_RESET,
        ON_AFTER_RESET,
        ON_ERROR
    }

    private lateinit var partitionValue: String
    private lateinit var user: User
    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        partitionValue = TestHelper.randomPartitionValue()
        app = TestApp(TESTAPP_PARTITION, logLevel = LogLevel.INFO)
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

    @Test
    fun discardUnsyncedLocalChanges_userExceptionCaptured_onBeforeReset() {
        // Validates that any user exception during the automatic client reset is properly captured.

        val channel = Channel<ClientResetEvents>(2)

        val config = SyncConfiguration.Builder(
            user,
            partitionValue,
            schema = setOf(FlexParentObject::class) // Use a class that is present in the server schema
        ).syncClientResetStrategy(
            object : DiscardUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                    throw IllegalStateException("User exception")
                }

                override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_AFTER_RESET)
                }

                override fun onError(session: SyncSession, exception: ClientResetRequiredException) {
                    // Notify that this callback has been invoked
                    assertEquals("[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed", exception.message)
                    channel.trySend(ClientResetEvents.ON_ERROR)
                }
            }
        ).build()

        Realm.open(config).use { realm ->
            runBlocking {
                with(realm.syncSession) {
                    downloadAllServerChanges()

                    // Pause the session to avoid receiving any network interrupted error
                    pause()

                    app.triggerClientReset(user.identity) // Removes the client file triggering a Client reset

                    // Resuming the session would trigger the client reset
                    resume()
                }

                // Validate that the client reset was triggered successfully
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
            }
        }
    }

    @Test
    fun discardUnsyncedLocalChanges_userExceptionCaptured_onAfterReset() {
        // Validates that any user exception during the automatic client reset is properly captured.
        val channel = Channel<ClientResetEvents>(2)

        val config = SyncConfiguration.Builder(
            user,
            partitionValue,
            schema = setOf(FlexParentObject::class) // Use a class that is present in the server schema
        ).syncClientResetStrategy(
            object : DiscardUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                }

                override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_AFTER_RESET)
                    throw IllegalStateException("User exception")
                }

                override fun onError(session: SyncSession, exception: ClientResetRequiredException) {
                    // Notify that this callback has been invoked
                    assertEquals("[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed", exception.message)
                    channel.trySend(ClientResetEvents.ON_ERROR)
                }
            }
        ).build()

        Realm.open(config).use { realm ->
            runBlocking {
                with(realm.syncSession) {
                    downloadAllServerChanges()

                    // Pause the session to avoid receiving any network interrupted error
                    pause()

                    app.triggerClientReset(user.identity) // Removes the client file triggering a Client reset

                    // Resuming the session would trigger the client reset
                    resume()
                }

                // Validate that the client reset was triggered successfully
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())
                assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
            }
        }
    }
}
