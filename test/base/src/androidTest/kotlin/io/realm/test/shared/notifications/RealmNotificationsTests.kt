/*
 * Copyright 2021 Realm Inc.
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

package io.realm.test.shared.notifications

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.VersionId
import io.realm.entities.Sample
import io.realm.internal.platform.runBlocking
import io.realm.notifications.InitialRealm
import io.realm.notifications.RealmChange
import io.realm.notifications.UpdatedRealm
import io.realm.test.NotificationTests
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onCompletion
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RealmNotificationsTests : NotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    override fun initialElement() {
        runBlocking {
            val c = Channel<RealmChange<Realm>>(1)
            val startingVersion = realm.version()
            val observer = async {
                realm.asFlow().collect {
                    c.send(it)
                }
            }
            c.receive().let { realmChange ->
                assertIs<InitialRealm<Realm>>(realmChange)
                assertEquals(startingVersion, realmChange.realm.version())
            }

            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun asFlow() {
        runBlocking {
            val c = Channel<RealmChange<Realm>>(1)
            val startingVersion = realm.version()
            val observer = async {
                realm.asFlow().collect {
                    c.send(it)
                }
            }

            // We should first receive an initial Realm notification.
            c.receive().let { realmChange ->
                assertIs<InitialRealm<Realm>>(realmChange)
                assertEquals(startingVersion, realmChange.realm.version())
            }

            realm.write { /* Do nothing */ }

            // Now we we should receive an updated Realm change notification.
            c.receive().let { realmChange ->
                assertIs<UpdatedRealm<Realm>>(realmChange)
                assertEquals(VersionId(startingVersion.version + 1), realmChange.realm.version())
            }

            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun cancelAsFlow() {
        runBlocking {
            val c1 = Channel<RealmChange<Realm>>(1)
            val c2 = Channel<RealmChange<Realm>>(1)
            val startingVersion = realm.version()
            val observer1 = async {
                realm.asFlow().collect {
                    c1.send(it)
                }
            }
            val observer2 = async {
                realm.asFlow().collect {
                    c2.send(it)
                }
            }

            // We should first receive an initial Realm notification.
            c1.receive().let { realmChange ->
                assertIs<InitialRealm<Realm>>(realmChange)
                assertEquals(startingVersion, realmChange.realm.version())
            }

            c2.receive().let { realmChange ->
                assertIs<InitialRealm<Realm>>(realmChange)
                assertEquals(startingVersion, realmChange.realm.version())
            }

            realm.write { /* Do nothing */ }

            // Now we we should receive an updated Realm change notification.
            c1.receive().let { realmChange ->
                assertIs<UpdatedRealm<Realm>>(realmChange)
                assertEquals(VersionId(startingVersion.version + 1), realmChange.realm.version())
            }

            c2.receive().let { realmChange ->
                assertIs<UpdatedRealm<Realm>>(realmChange)
                assertEquals(VersionId(startingVersion.version + 1), realmChange.realm.version())
            }

            observer2.cancel()

            realm.write { /* Do nothing */ }

            // Closing an observer should prevent the channel on receiving further notifications
            assertTrue(c2.isEmpty)
            // But unclosed channels should receive notifications
            c1.receive().let { realmChange ->
                assertIs<UpdatedRealm<Realm>>(realmChange)
                assertEquals(VersionId(startingVersion.version + 2), realmChange.realm.version())
            }

            realm.write { /* Do nothing */ }
            observer1.cancel()
            c1.close()
            c2.close()
        }
    }

    @Test
    @Ignore
    override fun deleteObservable() {
        // Realms cannot be deleted, so Realm Flows do not need to handle this case
    }

    @Test
    @Ignore
    override fun closeRealmInsideFlowThrows() {
        TODO("Wait for a Global change listener to become available")
    }

    @Test
    @Ignore
    override fun closingRealmDoesNotCancelFlows() {
        TODO("Wait for a Global change listener to become available")
    }

    @Test
    fun closingRealmCompletesFlow() {
        runBlocking {
            val c = Channel<RealmChange<*>>(1)
            val cancelledChannel = Channel<Boolean>(1)

            val observer = async {
                realm.asFlow()
                    .onCompletion {
                        // Signal completion
                        cancelledChannel.send(true)
                    }
                    .collect {
                        c.trySend(it)
                    }
            }
            realm.close()
            cancelledChannel.receive()
            assertTrue(observer.isCompleted)
            observer.cancel()
            c.close()
        }
    }
}
