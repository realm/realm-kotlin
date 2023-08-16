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
import io.realm.kotlin.ext.realmAnyDictionaryOf
import io.realm.kotlin.ext.realmAnyListOf
import io.realm.kotlin.ext.realmAnyOf
import io.realm.kotlin.ext.realmAnySetOf
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.notifications.DeletedList
import io.realm.kotlin.notifications.DeletedMap
import io.realm.kotlin.notifications.InitialMap
import io.realm.kotlin.notifications.MapChange
import io.realm.kotlin.notifications.UpdatedMap
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

class RealmAnyNestedDictionaryNotificationTest : RealmEntityNotificationTests {

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
        val channel = Channel<MapChange<String, RealmAny?>>()

        val o: JsonStyleRealmObject = realm.write {
            copyToRealm(JsonStyleRealmObject().apply {
                _id = "DICTIONARY"
                value = realmAnyDictionaryOf("root" to realmAnyDictionaryOf("key1" to 1, "key2" to  2, "key3" to 3))
            })
        }

        val dict = o.value!!.asDictionary()["root"]!!.asDictionary()
        assertEquals(3, dict.size)
        val listener = async {
            dict.asFlow().collect {
                channel.send(it)
            }
        }

        channel.receiveOrFail(1.seconds).run {
            assertIs<InitialMap<String, RealmAny?>>(this)
            assertEquals(mapOf("key1" to 1,"key2" to 2,"key3" to 3), this.map.mapValues{ it.value!!.asInt()})
        }

        realm.write {
            val liveList = findLatest(o)!!.value!!.asDictionary()["root"]!!.asDictionary()
            liveList.put("key4", RealmAny.create(4))
        }

        channel.receiveOrFail(1.seconds).run {
            assertIs<UpdatedMap<String,RealmAny?>>(this)
            assertEquals(mapOf("key1" to 1,"key2" to 2,"key3" to 3, "key4" to 4), this.map.mapValues{ it.value!!.asInt()})
        }

        realm.write {
            findLatest(o)!!.value = realmAnyOf(5)
        }

        // Fails due to missing deletion events
        channel.receiveOrFail(1.seconds).run {
            assertIs<DeletedMap<String, RealmAny?>>(this)
        }
        listener.cancel()
        channel.close()
    }

    @Test
    override fun cancelAsFlow() {
        kotlinx.coroutines.runBlocking {
            val container = realm.write {
                copyToRealm(JsonStyleRealmObject().apply { value = realmAnyDictionaryOf("root" to
                    realmAnyDictionaryOf()
                )
                })
            }
            val channel1 = Channel<MapChange<String,*>>(1)
            val channel2 = Channel<MapChange<String,*>>(1)
            val observedDict = container.value!!.asDictionary()["root"]!!.asDictionary()
            val observer1 = async {
                observedDict.asFlow()
                    .collect { change ->
                        channel1.trySend(change)
                    }
            }
            val observer2 = async {
                observedDict.asFlow()
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
                // FIXME Suspendable notifier listens to root list instead of nested list
//                queriedContainer!!.value!!.asList()[0]!!.asDictionary().put("key1", RealmAny.create(1))
                queriedContainer!!.value!!.asDictionary()["root"]!!.asDictionary().put("key1", RealmAny.create(1))
            }
            assertEquals(2, channel1.receiveOrFail().map.size)
            assertEquals(2, channel2.receiveOrFail().map.size)

            // Cancel observer 1
            observer1.cancel()

            // Trigger another update
            realm.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.value!!.asDictionary()["root"]!!.asDictionary().put("key2", RealmAny.create(2))
            }

            // Check channel 1 didn't receive the update
            assertTrue(channel1.isEmpty)
            // But channel 2 did
            assertEquals(3, channel2.receiveOrFail().map.size)

            observer2.cancel()
            channel1.close()
            channel2.close()
        }
    }

    @Test
    override fun deleteEntity() = runBlocking<Unit> {
        val container =
            realm.write { copyToRealm(JsonStyleRealmObject().apply { value = realmAnyDictionaryOf("root" to
                realmAnyDictionaryOf()
            ) }) }
        val mutex = Mutex(true)
        val flow = async {
            container.value!!.asDictionary()["root"]!!.asDictionary().asFlow().first {
                mutex.unlock()
                it is DeletedMap<String, *>
            }
        }

        // Await that flow is actually running
        mutex.lock()
        // Update mixed value to overwrite and delete set
        realm.write {
            // FIMXE Overwriting with similar container type doesn't emit a deletion event
            //  https://github.com/realm/realm-core/issues/6895
//            findLatest(container)!!.value = realmAnyListOf()
            findLatest(container)!!.value = realmAnySetOf()
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
            realm.write { copyToRealm(JsonStyleRealmObject().apply { value = realmAnyDictionaryOf("root" to
                realmAnyDictionaryOf()
            ) }) }
        val mutex = Mutex(true)
        val flow = async {
            container.value!!.asDictionary()["root"]!!.asDictionary().asFlow().first {
                mutex.unlock()
                it is DeletedMap<String, *>
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
            container.value!!.asDictionary()["root"]!!.asDictionary().asFlow().collect {
                assertIs<DeletedMap<String, *>>(it)
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
