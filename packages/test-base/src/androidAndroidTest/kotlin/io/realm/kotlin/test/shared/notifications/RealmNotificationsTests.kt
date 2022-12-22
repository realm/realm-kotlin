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

package io.realm.kotlin.test.shared.notifications

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.VersionId
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.asFlow
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.notifications.InitialRealm
import io.realm.kotlin.notifications.RealmChange
import io.realm.kotlin.notifications.UpdatedRealm
import io.realm.kotlin.test.NotificationTests
import io.realm.kotlin.test.platform.PlatformUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

    @Test
    fun notification_cancelsOnInsufficientBuffers() {
        val sample = realm.writeBlocking { copyToRealm(Sample()) }
        val flow = sample.asFlow()

        runBlocking {
            val listener = async {
                withTimeout(10.seconds) {
                    assertFailsWith<CancellationException> {
                        flow.collect {
                            delay(1000.milliseconds)
                        }
                    }.message!!.let { message ->
                        assertEquals(
                            "Cannot deliver object notifications. Increase dispatcher processing resources or buffer the flow with buffer(...)",
                            message
                        )
                    }
                }
            }
            (1..100).forEach {
                realm.writeBlocking {
                    findLatest(sample)!!.apply {
                        stringField = it.toString()
                    }
                }
            }
            listener.await()
        }
    }

    // This test shows that our internal logic still works (by closing the flow on deletion events)
    // even though the public consumer is dropping elements
    @Test
    fun notification_backpressureStrategyDoesNotRuinInternalLogic() {
        val sample = realm.writeBlocking { copyToRealm(Sample()) }
        val flow = sample.asFlow()
            .buffer(0, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        runBlocking {
            val listener = async {
                withTimeout(10.seconds) {
                    flow.collect {
                        delay(100.milliseconds)
                    }
                }
            }
            (1..100).forEach {
                realm.writeBlocking {
                    findLatest(sample)!!.apply {
                        stringField = it.toString()
                    }
                }
            }
            realm.write { delete(findLatest(sample)!!) }
            listener.await()
        }
    }
}
