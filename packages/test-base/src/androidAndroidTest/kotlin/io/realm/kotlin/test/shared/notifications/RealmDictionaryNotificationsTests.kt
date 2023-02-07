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

package io.realm.kotlin.test.shared.notifications

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.dictionary.RealmDictionaryContainer
import io.realm.kotlin.internal.platform.freeze
import io.realm.kotlin.notifications.DeletedDictionary
import io.realm.kotlin.notifications.DictionaryChange
import io.realm.kotlin.notifications.InitialDictionary
import io.realm.kotlin.notifications.UpdatedDictionary
import io.realm.kotlin.test.NotificationTests
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.shared.DICTIONARY_KEYS_FOR_NULLABLE
import io.realm.kotlin.test.shared.NULLABLE_DICTIONARY_OBJECT_VALUES
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class RealmDictionaryNotificationsTests : NotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    private val keys = listOf("11", "22", "33")

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(schema = setOf(RealmDictionaryContainer::class))
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
        val dataSet = NULLABLE_DICTIONARY_OBJECT_VALUES.mapIndexed { i, value ->
            Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value)
        }

        val container = realm.writeBlocking {
            copyToRealm(RealmDictionaryContainer()).also {
                it.nullableObjectDictionaryField.putAll(dataSet)
            }
        }

        runBlocking {
            val channel = Channel<DictionaryChange<*>>(capacity = 1)
            val observer = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { flowDictionary ->
                        channel.send(flowDictionary)
                    }
            }

            // Assertion after empty dictionary is emitted
            channel.receive().let { dictionaryChange ->
                assertIs<InitialDictionary<*>>(dictionaryChange)

                assertNotNull(dictionaryChange.map)
                assertEquals(dataSet.size, dictionaryChange.map.size)
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
            val channel = Channel<DictionaryChange<*>>(capacity = 1)
            val observer = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { flowDictionary ->
                        if (flowDictionary !is InitialDictionary<*>) {
                            channel.send(flowDictionary)
                        }
                    }
            }

            // Assert a single insertion is reported
            realm.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedDictionary = queriedContainer!!.nullableObjectDictionaryField
                queriedDictionary[dataSet[0].first] = dataSet[0].second
            }

            channel.receive().let { dictionaryChange ->
                assertIs<UpdatedDictionary<*>>(dictionaryChange)

                assertNotNull(dictionaryChange.map)
                assertEquals(1, dictionaryChange.map.size)
                dictionaryChange.insertions.let { insertions ->
                    assertEquals(1, insertions.size)
                    assertEquals(dataSet[0].first, insertions[0])
                }
                assertEquals(0, dictionaryChange.deletions.size)
            }

            // Assert a change to a key is reported
            realm.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedDictionary = queriedContainer!!.nullableObjectDictionaryField
                queriedDictionary[dataSet[0].first] = dataSet[1].second
            }

            channel.receive().let { dictionaryChange ->
                assertIs<UpdatedDictionary<*>>(dictionaryChange)

                assertNotNull(dictionaryChange.map)
                assertEquals(1, dictionaryChange.map.size)
                dictionaryChange.changes.let { changes ->
                    assertEquals(1, changes.size)
                    assertEquals(dataSet[0].first, changes[0])
                }
                assertEquals(0, dictionaryChange.deletions.size)
            }

            // Assert multiple insertions at once are reported
            realm.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedDictionary = queriedContainer!!.nullableObjectDictionaryField
                queriedDictionary.putAll(dataSet.subList(1, dataSet.size))
            }

            channel.receive().let { dictionaryChange ->
                assertIs<UpdatedDictionary<*>>(dictionaryChange)

                assertNotNull(dictionaryChange.map)
                assertEquals(dataSet.size, dictionaryChange.map.size)
                dictionaryChange.insertions.let { insertions ->
                    assertEquals(dataSet.size - 1, insertions.size)
                    dataSet.map { it.first }
                        .also { keys ->
                            keys.containsAll(insertions.toList())
                        }
                }
                assertEquals(0, dictionaryChange.deletions.size)
            }

            // Assert notification on removal of elements
            realm.writeBlocking {
                val queriedContainer = findLatest(container)!!
                val queriedDictionary = queriedContainer.nullableObjectDictionaryField
                queriedDictionary.remove(dataSet[0].first)
            }

            channel.receive().let { dictionaryChange ->
                assertIs<UpdatedDictionary<*>>(dictionaryChange)

                assertNotNull(dictionaryChange.map)
                assertEquals(dataSet.size - 1, dictionaryChange.map.size)
                assertEquals(1, dictionaryChange.deletions.size)
                assertEquals(0, dictionaryChange.insertions.size)
            }

            // Assert notification on removal of elements via values iterator
            realm.writeBlocking {
                val queriedContainer = findLatest(container)!!
                val queriedDictionary = queriedContainer.nullableObjectDictionaryField
                val iterator = queriedDictionary.values.iterator()
                iterator.next()
                iterator.remove()
            }

            channel.receive().let { dictionaryChange ->
                assertIs<UpdatedDictionary<*>>(dictionaryChange)

                assertNotNull(dictionaryChange.map)
                assertEquals(dataSet.size - 2, dictionaryChange.map.size)
                assertEquals(1, dictionaryChange.deletions.size)
                assertEquals(0, dictionaryChange.insertions.size)
            }

            // Assert notification on removal of elements via entry set iterator
            realm.writeBlocking {
                val queriedContainer = findLatest(container)!!
                val queriedDictionary = queriedContainer.nullableObjectDictionaryField
                val iterator = queriedDictionary.entries.iterator()
                iterator.next()
                iterator.remove()
            }

            channel.receive().let { dictionaryChange ->
                assertIs<UpdatedDictionary<*>>(dictionaryChange)

                assertNotNull(dictionaryChange.map)
                assertTrue(dictionaryChange.map.isEmpty())
                assertEquals(1, dictionaryChange.deletions.size)
                assertEquals(0, dictionaryChange.insertions.size)
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    override fun cancelAsFlow() {
        runBlocking {
            // Freeze values since native complains if we reference a package-level defined variable
            // inside a write block
            val values = NULLABLE_DICTIONARY_OBJECT_VALUES.mapIndexed { i, value ->
                Pair(keys[i], value)
            }.freeze()
            val container = realm.write {
                copyToRealm(RealmDictionaryContainer())
            }
            val channel1 = Channel<DictionaryChange<*>>(1)
            val channel2 = Channel<DictionaryChange<*>>(1)
            val observer1 = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { dictionaryChange ->
                        channel1.trySend(dictionaryChange)
                    }
            }
            val observer2 = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { dictionaryChange ->
                        channel2.trySend(dictionaryChange)
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
    override fun deleteObservable() {
        runBlocking {
            // Freeze values since native complains if we reference a package-level defined variable
            // inside a write block
            val values = NULLABLE_DICTIONARY_OBJECT_VALUES.mapIndexed { i, value ->
                Pair(keys[i], value)
            }.freeze()
            val channel1 = Channel<DictionaryChange<*>>(capacity = 1)
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
                    }.collect { dictionaryChange ->
                        channel1.send(dictionaryChange)
                    }
            }

            // Assert container got populated correctly
            channel1.receive().let { dictionaryChange ->
                assertIs<InitialDictionary<*>>(dictionaryChange)

                assertNotNull(dictionaryChange.map)
                assertEquals(NULLABLE_DICTIONARY_OBJECT_VALUES.size, dictionaryChange.map.size)
            }

            // Now delete owner
            realm.write {
                delete(findLatest(container)!!)
            }

            channel1.receive().let { dictionaryChange ->
                assertIs<DeletedDictionary<*>>(dictionaryChange)
                assertTrue(dictionaryChange.map.isEmpty())
            }
            // Wait for flow completion
            assertTrue(channel2.receive())

            observer.cancel()
            channel1.close()
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
            val channel = Channel<DictionaryChange<*>>(capacity = 1)
            val container = realm.write {
                copyToRealm(RealmDictionaryContainer())
            }
            val observer = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { dictionaryChange ->
                        channel.trySend(dictionaryChange)
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
