/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.test.common.notifications

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.JsonStyleRealmObject
import io.realm.kotlin.ext.realmAnyListOf
import io.realm.kotlin.ext.realmAnyOf
import io.realm.kotlin.ext.realmAnySetOf
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.notifications.DeletedSet
import io.realm.kotlin.notifications.InitialSet
import io.realm.kotlin.notifications.SetChange
import io.realm.kotlin.notifications.UpdatedSet
import io.realm.kotlin.test.common.utils.RealmEntityNotificationTests
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.types.RealmAny
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RealmAnyNestedSetNotificationTest : RealmEntityNotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(
            schema = setOf(JsonStyleRealmObject::class)
        ).directory(tmpDir)
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
    @Ignore // Initial element events are verified as part of the asFlow tests
    override fun initialElement() {}

    @Test
    override fun asFlow() = runBlocking<Unit> {
        val channel = Channel<SetChange<RealmAny?>>()

        val o: JsonStyleRealmObject = realm.write {
            copyToRealm(
                JsonStyleRealmObject().apply {
                    id = "SET"
                    value = realmAnySetOf(1, 2, 3)
                }
            )
        }

        val set = o.value!!.asSet()
        assertEquals(3, set.size)
        val listener = async {
            set.asFlow().collect {
                channel.send(it)
            }
        }

        channel.receiveOrFail(1.seconds).run {
            assertIs<InitialSet<RealmAny?>>(this)
            val expectedSet = mutableSetOf(1, 2, 3)
            this.set.forEach { expectedSet.remove(it!!.asInt()) }
            assertTrue { expectedSet.isEmpty() }
        }

        realm.write {
            val liveSet = findLatest(o)!!.value!!.asSet()
            liveSet.add(RealmAny.create(4))
        }

        channel.receiveOrFail(1.seconds).run {
            assertIs<UpdatedSet<RealmAny?>>(this)
            val expectedSet = mutableSetOf(1, 2, 3, 4)
            this.set.forEach { expectedSet.remove(it!!.asInt()) }
            assertTrue { expectedSet.isEmpty() }
        }

        realm.write {
            findLatest(o)!!.value = realmAnyOf(5)
        }

        // Fails due to missing deletion events
        channel.receiveOrFail(1.seconds).run {
            assertIs<DeletedSet<RealmAny?>>(this)
        }
        listener.cancel()
        channel.close()
    }

    @Test
    override fun cancelAsFlow() {
        kotlinx.coroutines.runBlocking {
            val container = realm.write {
                copyToRealm(JsonStyleRealmObject().apply { value = realmAnySetOf() })
            }
            val channel1 = Channel<SetChange<*>>(1)
            val channel2 = Channel<SetChange<*>>(1)
            val observedSet = container.value!!.asSet()
            val observer1 = async {
                observedSet.asFlow()
                    .collect { change ->
                        channel1.trySend(change)
                    }
            }
            val observer2 = async {
                observedSet.asFlow()
                    .collect { change ->
                        channel2.trySend(change)
                    }
            }

            // Ignore first emission with empty sets
            assertTrue { channel1.receiveOrFail(1.seconds).set.isEmpty() }
            assertTrue { channel2.receiveOrFail(1.seconds).set.isEmpty() }

            // Trigger an update
            realm.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.value!!.asSet().add(RealmAny.Companion.create(1))
            }
            assertEquals(1, channel1.receiveOrFail().set.size)
            assertEquals(1, channel2.receiveOrFail().set.size)

            // Cancel observer 1
            observer1.cancel()

            // Trigger another update
            realm.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.value!!.asSet().add(RealmAny.create(2))
            }

            // Check channel 1 didn't receive the update
            assertTrue(channel1.isEmpty)
            // But channel 2 did
            assertEquals(2, channel2.receiveOrFail().set.size)

            observer2.cancel()
            channel1.close()
            channel2.close()
        }
    }

    @Test
    override fun deleteEntity() = runBlocking<Unit> {
        val container =
            realm.write { copyToRealm(JsonStyleRealmObject().apply { value = realmAnySetOf() }) }
        val mutex = Mutex(true)
        val flow = async {
            container.value!!.asSet().asFlow().first {
                mutex.unlock()
                it is DeletedSet<*>
            }
        }

        // Await that flow is actually running
        mutex.lock()
        // Update mixed value to overwrite and delete set
        realm.write {
            findLatest(container)!!.value = realmAnyListOf()
        }

        // Await that notifier has signalled the deletion so we are certain that the entity
        // has been deleted
        withTimeout(10.seconds) {
            flow.await()
        }
    }

    @Test
    override fun asFlowOnDeletedEntity() = runBlocking<Unit> {
        val container =
            realm.write { copyToRealm(JsonStyleRealmObject().apply { value = realmAnyListOf(realmAnySetOf()) }) }
        val mutex = Mutex(true)
        val flow = async {
            container.value!!.asList()[0]!!.asSet().asFlow().first {
                mutex.unlock()
                it is DeletedSet<*>
            }
        }

        // Await that flow is actually running
        mutex.lock()
        // And delete containing entity
        realm.write { delete(findLatest(container)!!) }

        // Await that notifier has signalled the deletion so we are certain that the entity
        // has been deleted
        withTimeout(10.seconds) {
            flow.await()
        }

        // Verify that a flow on the deleted entity will signal a deletion and complete gracefully
        withTimeout(10.seconds) {
            container.value!!.asList()[0]!!.asSet().asFlow().collect {
                assertIs<DeletedSet<*>>(it)
            }
        }
    }

    @Test
    @Ignore
    override fun closingRealmDoesNotCancelFlows() {
        TODO("Not yet implemented")
    }

    @Ignore
    override fun closeRealmInsideFlowThrows() {
        TODO("Not yet implemented")
    }
}
