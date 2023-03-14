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

package io.realm.kotlin.test.shared.notifications

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.dictionary.DictionaryEmbeddedLevel1
import io.realm.kotlin.entities.dictionary.RealmDictionaryContainer
import io.realm.kotlin.notifications.DeletedMap
import io.realm.kotlin.notifications.InitialMap
import io.realm.kotlin.notifications.MapChange
import io.realm.kotlin.notifications.UpdatedMap
import io.realm.kotlin.test.RealmEntityNotificationTests
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.shared.DICTIONARY_KEYS_FOR_NULLABLE
import io.realm.kotlin.test.shared.NULLABLE_DICTIONARY_OBJECT_VALUES
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class RealmDictionaryNotificationsTests : RealmEntityNotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    private val keys = listOf("11", "22", "33")

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(
            schema = setOf(RealmDictionaryContainer::class, DictionaryEmbeddedLevel1::class)
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

    override fun deleteEntity() {
        runBlocking {
            // Freeze values since native complains if we reference a package-level defined variable
            // inside a write block
            val values = NULLABLE_DICTIONARY_OBJECT_VALUES.mapIndexed { i, value ->
                Pair(keys[i], value)
            }
            val channel1 = Channel<MapChange<String, *>>(capacity = 1)
            val channel2 = Channel<Boolean>(capacity = 1)
            val container = realm.write {
                copyToRealm(
                    RealmDictionaryContainer().apply {
                        nullableObjectDictionaryField.putAll(values)
                    }
                )
            }
            val observer = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .onCompletion {
                        // Signal completion
                        channel2.send(true)
                    }.collect { mapChange ->
                        channel1.send(mapChange)
                    }
            }

            // Assert container got populated correctly
            channel1.receive().let { mapChange ->
                assertIs<InitialMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertEquals(NULLABLE_DICTIONARY_OBJECT_VALUES.size, mapChange.map.size)
            }

            // Now delete owner
            realm.write {
                delete(findLatest(container)!!)
            }

            channel1.receive().let { mapChange ->
                assertIs<DeletedMap<String, *>>(mapChange)
                assertTrue(mapChange.map.isEmpty())
            }
            // Wait for flow completion
            assertTrue(channel2.receive())

            observer.cancel()
            channel1.close()
        }
    }

    override fun asFlowOnDeleteEntity() {
        runBlocking {
            val container = realm.write { copyToRealm(RealmDictionaryContainer()) }
            val mutex = Mutex(true)
            val flow = async {
                container.stringDictionaryField.asFlow().first {
                    mutex.unlock()
                    it is DeletedMap<*, *>
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
                container.stringDictionaryField.asFlow().collect {
                    assertIs<DeletedMap<*, *>>(it)
                }
            }
        }
    }

    @Test
    override fun initialElement() {
        val dataSet = NULLABLE_DICTIONARY_OBJECT_VALUES.mapIndexed { i, value ->
            Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value)
        }

        val container = realm.writeBlocking {
            copyToRealm(RealmDictionaryContainer()).also {
                it.nullableObjectDictionaryField.putAll(dataSet)
            }
        }

        runBlocking {
            val channel = Channel<MapChange<String, *>>(capacity = 1)
            val observer = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { mapChange ->
                        channel.send(mapChange)
                    }
            }

            // Assertion after empty dictionary is emitted
            channel.receive().let { mapChange ->
                assertIs<InitialMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertEquals(dataSet.size, mapChange.map.size)
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    @Suppress("LongMethod")
    override fun asFlow() {
        val dataSet = NULLABLE_DICTIONARY_OBJECT_VALUES.mapIndexed { i, value ->
            Pair(keys[i], value)
        }
        val container = realm.writeBlocking {
            // Create an empty container with empty dictionaries
            copyToRealm(RealmDictionaryContainer())
        }

        runBlocking {
            val channel = Channel<MapChange<String, *>>(capacity = 1)
            val observer = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { mapChange ->
                        if (mapChange !is InitialMap<String, *>) {
                            channel.send(mapChange)
                        }
                    }
            }

            // Assert a single insertion is reported
            realm.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedDictionary = queriedContainer!!.nullableObjectDictionaryField
                queriedDictionary[dataSet[0].first] = dataSet[0].second
            }

            channel.receive().let { mapChange ->
                assertIs<UpdatedMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertEquals(1, mapChange.map.size)
                mapChange.insertions.let { insertions ->
                    assertEquals(1, insertions.size)
                    assertEquals(dataSet[0].first, insertions[0])
                }
                assertEquals(0, mapChange.deletions.size)
                assertEquals(0, mapChange.changes.size)
            }

            // Assert a change to a key is reported
            realm.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedDictionary = queriedContainer!!.nullableObjectDictionaryField
                queriedDictionary[dataSet[0].first] = dataSet[1].second
            }

            channel.receive().let { mapChange ->
                assertIs<UpdatedMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertEquals(1, mapChange.map.size)
                mapChange.changes.let { changes ->
                    assertEquals(1, changes.size)
                    assertEquals(dataSet[0].first, changes[0])
                }
                assertEquals(0, mapChange.deletions.size)
                assertEquals(0, mapChange.insertions.size)
            }

            // Assert multiple insertions at once are reported
            realm.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedDictionary = queriedContainer!!.nullableObjectDictionaryField
                queriedDictionary.putAll(dataSet.subList(1, dataSet.size))
            }

            channel.receive().let { mapChange ->
                assertIs<UpdatedMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertEquals(dataSet.size, mapChange.map.size)
                mapChange.insertions.let { insertions ->
                    assertEquals(dataSet.size - 1, insertions.size)
                    dataSet.map { it.first }
                        .also { keys ->
                            keys.containsAll(insertions.toList())
                        }
                }
                assertEquals(0, mapChange.deletions.size)
                assertEquals(0, mapChange.changes.size)
            }

            // Assert notification on removal of elements
            realm.writeBlocking {
                val queriedContainer = findLatest(container)!!
                val queriedDictionary = queriedContainer.nullableObjectDictionaryField
                queriedDictionary.remove(dataSet[0].first)
            }

            channel.receive().let { mapChange ->
                assertIs<UpdatedMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertEquals(dataSet.size - 1, mapChange.map.size)
                assertEquals(1, mapChange.deletions.size)
                assertEquals(0, mapChange.insertions.size)
                assertEquals(0, mapChange.changes.size)
            }

            // Assert notification on removal of elements via values iterator
            realm.writeBlocking {
                val queriedContainer = findLatest(container)!!
                val queriedDictionary = queriedContainer.nullableObjectDictionaryField
                val iterator = queriedDictionary.values.iterator()
                iterator.next()
                iterator.remove()
            }

            channel.receive().let { mapChange ->
                assertIs<UpdatedMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertEquals(dataSet.size - 2, mapChange.map.size)
                assertEquals(1, mapChange.deletions.size)
                assertEquals(0, mapChange.insertions.size)
                assertEquals(0, mapChange.changes.size)
            }

            // Assert notification on removal of elements via entry set iterator
            realm.writeBlocking {
                val queriedContainer = findLatest(container)!!
                val queriedDictionary = queriedContainer.nullableObjectDictionaryField
                val iterator = queriedDictionary.entries.iterator()
                iterator.next()
                iterator.remove()
            }

            channel.receive().let { mapChange ->
                assertIs<UpdatedMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertTrue(mapChange.map.isEmpty())
                assertEquals(1, mapChange.deletions.size)
                assertEquals(0, mapChange.insertions.size)
                assertEquals(0, mapChange.changes.size)
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    override fun cancelAsFlow() {
        runBlocking {
            val values = NULLABLE_DICTIONARY_OBJECT_VALUES.mapIndexed { i, value ->
                Pair(keys[i], value)
            }
            val container = realm.write {
                copyToRealm(RealmDictionaryContainer())
            }
            val channel1 = Channel<MapChange<String, *>>(1)
            val channel2 = Channel<MapChange<String, *>>(1)
            val observer1 = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { mapChange ->
                        channel1.trySend(mapChange)
                    }
            }
            val observer2 = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { mapChange ->
                        channel2.trySend(mapChange)
                    }
            }

            // Ignore first emission with empty dictionaries
            channel1.receive()
            channel2.receive()

            // Trigger an update
            realm.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.nullableObjectDictionaryField.putAll(values)
            }
            assertEquals(NULLABLE_DICTIONARY_OBJECT_VALUES.size, channel1.receive().map.size)
            assertEquals(NULLABLE_DICTIONARY_OBJECT_VALUES.size, channel2.receive().map.size)

            // Cancel observer 1
            observer1.cancel()

            // Trigger another update
            realm.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.nullableObjectDictionaryField
                    .put(
                        "SOMETHING",
                        copyToRealm(RealmDictionaryContainer().apply { stringField = "C" })
                    )
            }

            // Check channel 1 didn't receive the update
            assertEquals(NULLABLE_DICTIONARY_OBJECT_VALUES.size + 1, channel2.receive().map.size)
            assertTrue(channel1.isEmpty)

            observer2.cancel()
            channel1.close()
            channel2.close()
        }
    }

    @Test
    @Ignore // FIXME Wait for https://github.com/realm/realm-kotlin/pull/300 to be merged before fleshing this out
    override fun closeRealmInsideFlowThrows() {
        TODO("Waiting for RealmDictionary support")
    }

    @Test
    override fun closingRealmDoesNotCancelFlows() {
        runBlocking {
            val channel = Channel<MapChange<String, *>>(capacity = 1)
            val container = realm.write {
                copyToRealm(RealmDictionaryContainer())
            }
            val observer = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { mapChange ->
                        channel.trySend(mapChange)
                    }
                fail("Flow should not be canceled.")
            }

            assertTrue(channel.receive().map.isEmpty())

            realm.close()
            observer.cancel()
            channel.close()
        }
    }
}
