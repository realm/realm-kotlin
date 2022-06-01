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

import io.realm.MutableRealm
import io.realm.Realm
import io.realm.TypedRealm
import io.realm.entities.sync.flx.FlexParentObject
import io.realm.internal.platform.runBlocking
import io.realm.mongodb.User
import io.realm.mongodb.sync.ClientResetRequiredError
import io.realm.mongodb.sync.DiscardUnsyncedChangesStrategy
import io.realm.mongodb.sync.SyncConfiguration
import io.realm.mongodb.sync.SyncSession
import io.realm.mongodb.syncSession
import io.realm.query
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.createUserAndLogIn
import io.realm.test.util.TestHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun discardUnsyncedLocalChanges_success() = runBlocking {
        // Validate that the discard local strategy onBeforeReset and onAfterReset callbacks
        // are invoked successfully when a client reset is triggered.

        // Test with multiple Realm instances as they need to be updated automatically.

        val channel = Channel<ClientResetEvents>(2)
        val job = async {
            val config = SyncConfiguration.Builder(
                user,
                partitionValue,
                schema = setOf(FlexParentObject::class) // Use a class that is present in the server's schema
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

            val realm = Realm.open(config)

            val session = realm.syncSession
            session.pause()

            app.triggerClientReset(user.identity)

            // Write something while the session is paused to make sure the before realm contains something
            realm.writeBlocking {
                copyToRealm(FlexParentObject())
            }

            // Trigger the error
            session.resume()
        }

        assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
        assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())

        job.cancel()
    }

    @Test
    fun discardUnsyncedLocalChanges_failure() = runBlocking {
        // Validate that the discard local strategy onError callback is invoked successfully if
        // a client reset fails.

        // Test with multiple Realm instances as they must be closed manually.
    }

    @Test
    fun discardUnsyncedLocalChanges_success_attemptRecover() = runBlocking {
        // Attempts to recover data if a client reset is triggered.
    }
}
