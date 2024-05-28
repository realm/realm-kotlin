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
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.realmAnyDictionaryOf
import io.realm.kotlin.ext.realmAnyListOf
import io.realm.kotlin.ext.realmAnyOf
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.notifications.DeletedList
import io.realm.kotlin.notifications.InitialList
import io.realm.kotlin.notifications.ListChange
import io.realm.kotlin.notifications.UpdatedList
import io.realm.kotlin.test.common.utils.DeletableEntityNotificationTests
import io.realm.kotlin.test.common.utils.FlowableTests
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.test.util.trySendOrFail
import io.realm.kotlin.types.RealmAny
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

class RealmAnyNestedListNotificationTest : FlowableTests, DeletableEntityNotificationTests {

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
        val channel = Channel<ListChange<RealmAny?>>()

        val o: JsonStyleRealmObject = realm.write {
            copyToRealm(
                JsonStyleRealmObject().apply {
                    id = "LIST"
                    value = realmAnyListOf(realmAnyListOf(1, 2, 3))
                }
            )
        }

        val list = o.value!!.asList()[0]!!.asList()
        assertEquals(3, list.size)
        val listener = async {
            list.asFlow().collect {
                channel.send(it)
            }
        }

        channel.receiveOrFail(1.seconds).run {
            assertIs<InitialList<RealmAny?>>(this)
            assertEquals(listOf(1, 2, 3), this.list.map { it!!.asInt() })
        }

        realm.write {
            val liveNestedList = findLatest(o)!!.value!!.asList()[0]!!.asList()
            liveNestedList.add(RealmAny.create(4))
        }

        channel.receiveOrFail(1.seconds).run {
            assertIs<UpdatedList<RealmAny?>>(this)
            assertEquals(listOf(1, 2, 3, 4), this.list.map { it!!.asInt() })
        }

        realm.write {
            findLatest(o)!!.value = realmAnyOf(5)
        }

        // Fails due to missing deletion events
        channel.receiveOrFail(1.seconds).run {
            assertIs<DeletedList<RealmAny?>>(this)
        }
        listener.cancel()
        channel.close()
    }

    @Test
    override fun cancelAsFlow() {
        kotlinx.coroutines.runBlocking {
            val container = realm.write {
                copyToRealm(JsonStyleRealmObject().apply { value = realmAnyListOf(realmAnyListOf()) })
            }
            val channel1 = Channel<ListChange<*>>(1)
            val channel2 = Channel<ListChange<*>>(1)
            val observedSet = container.value!!.asList()[0]!!.asList()
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
            channel1.receiveOrFail()
            channel2.receiveOrFail()

            // Trigger an update
            realm.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.value!!.asList()[0]!!.asList().add(RealmAny.create(1))
            }
            assertEquals(1, channel1.receiveOrFail().list.size)
            assertEquals(1, channel2.receiveOrFail().list.size)

            // Cancel observer 1
            observer1.cancel()

            // Trigger another update
            realm.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.value!!.asList()[0]!!.asList().add(RealmAny.create(2))
            }

            // Check channel 1 didn't receive the update
            @OptIn(ExperimentalCoroutinesApi::class)
            assertTrue(channel1.isEmpty)
            // But channel 2 did
            assertEquals(2, channel2.receiveOrFail().list.size)

            observer2.cancel()
            channel1.close()
            channel2.close()
        }
    }

    @Test
    override fun deleteEntity() = runBlocking<Unit> {
        val container = realm.write {
            copyToRealm(
                JsonStyleRealmObject().apply {
                    value = realmAnyListOf(
                        realmAnyListOf()
                    )
                }
            )
        }
        val mutex = Mutex(true)
        val flow = async {
            container.value!!.asList()[0]!!.asList().asFlow().first {
                mutex.unlock()
                it is DeletedList<*>
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
        withTimeout(3.seconds) {
            flow.await()
        }
    }

    @Test
    override fun asFlowOnDeletedEntity() = runBlocking<Unit> {
        val container = realm.write {
            copyToRealm(
                JsonStyleRealmObject().apply { value = realmAnyListOf(realmAnyListOf()) }
            )
        }
        val mutex = Mutex(true)
        val flow = async {
            container.value!!.asList()[0]!!.asList().asFlow().first {
                mutex.unlock()
                it is DeletedList<*>
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
            container.value!!.asList()[0]!!.asList().asFlow().collect {
                assertIs<DeletedList<*>>(it)
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

    @Test
    @Ignore // https://github.com/realm/realm-core/issues/7264
    fun eventsOnObjectChangesInRealmAnyList() {
        kotlinx.coroutines.runBlocking {
            val channel = Channel<ListChange<RealmAny?>>(10)
            val parent =
                realm.write {
                    copyToRealm(JsonStyleRealmObject().apply { value = realmAnyListOf() })
                }

            val listener = async {
                parent.value!!.asList().asFlow().collect {
                    channel.trySendOrFail(it)
                }
            }

            channel.receiveOrFail(message = "Initial event").let { assertIs<InitialList<*>>(it) }

            realm.write {
                val asList = findLatest(parent)!!.value!!.asList()
                println(asList.size)
                asList.add(
                    RealmAny.create(JsonStyleRealmObject().apply { id = "CHILD" })
                )
            }
            channel.receiveOrFail(message = "List add").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
            }

            realm.write {
                findLatest(parent)!!.value!!.asList()[0]!!.asRealmObject<JsonStyleRealmObject>().value =
                    RealmAny.create("TEST")
            }
            channel.receiveOrFail(message = "Object updated").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
                assertEquals(
                    "TEST",
                    it.list[0]!!.asRealmObject<JsonStyleRealmObject>().value!!.asString()
                )
            }

            listener.cancel()
        }
    }

    @Test
    fun eventsOnDictionaryChangesInRealmAnyList() {
        kotlinx.coroutines.runBlocking {
            val channel = Channel<ListChange<RealmAny?>>(10)
            val parent =
                realm.write {
                    copyToRealm(JsonStyleRealmObject().apply { value = realmAnyListOf() })
                }

            val listener = async {
                parent.value!!.asList().asFlow().collect {
                    channel.trySendOrFail(it)
                }
            }

            channel.receiveOrFail(message = "Initial event").let { assertIs<InitialList<*>>(it) }

            realm.write {
                val asList = findLatest(parent)!!.value!!.asList()
                println(asList.size)
                asList.add(
                    realmAnyDictionaryOf(
                        "key1" to "value1"
                    )
                )
            }
            channel.receiveOrFail(message = "List add").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
                assertEquals(RealmAny.Type.DICTIONARY, it.list[0]!!.type)
            }

            realm.write {
                findLatest(parent)!!.value!!.asList()[0]!!.asDictionary()["key1"] =
                    RealmAny.create("TEST")
            }
            channel.receiveOrFail(message = "Object updated").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
                assertEquals("TEST", it.list[0]!!.asDictionary()["key1"]!!.asString())
            }

            listener.cancel()
        }
    }
}
