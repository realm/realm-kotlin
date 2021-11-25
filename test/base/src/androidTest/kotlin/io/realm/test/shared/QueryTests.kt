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
package io.realm.test.shared

import io.realm.QuerySort
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.entities.Sample
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueryTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .path("$tmpDir/default.realm").build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun find() {
        realm.query(Sample::class)
            .find()
            .let { results: RealmResults<Sample> -> assertEquals(0, results.size) }

        val stringValue = "some string"
        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val query = query(Sample::class)

            assertEquals(0, query.find().size)
            copyToRealm(Sample().apply { stringField = stringValue })
            assertEquals(1, query.find().size)

            // TODO investigate: extension function like this
//            query.find { this: RealmResults<Sample> ->
//
//            }
//            val results = query.find()
        }

        // No filter
        realm.query(Sample::class)
            .find()
            .let { results -> assertEquals(1, results.size) }

        // Filter by string
        realm.query(Sample::class, "stringField = $0", stringValue)
            .find()
            .let { results -> assertEquals(1, results.size) }

        // Filter by string that doesn't match
        realm.query(Sample::class, "stringField = $0", "invalid string")
            .find()
            .let { results -> assertEquals(0, results.size) }
    }

    @Test
    fun find_malformedQueryThrows() {
        // TODO investigate if these errors are correct
        assertFailsWith<Exception> {
            realm.query(Sample::class, "stringField = $0")
        }.let {
            assertTrue(it is IllegalArgumentException)
            assertTrue(it.message!!.contains("Have you specified all parameters"))
        }

        assertFailsWith<Exception> {
            realm.query(Sample::class, "stringField = 42")
        }.let {
            assertTrue(it is IllegalArgumentException)
            assertTrue(it.message!!.contains("Wrong query field"))
        }

        assertFailsWith<Exception> {
            realm.query(Sample::class, "nonExistingField = 13")
        }.let {
            assertTrue(it is IllegalArgumentException)
            assertTrue(it.message!!.contains("Wrong query field"))
        }
    }

    @Test
    fun sort() {
        val values = listOf(0 to "A", 1 to "B", 2 to "C")
        realm.writeBlocking {
            values.forEach { (intValue, stringValue) ->
                copyToRealm(Sample().apply {
                    intField = intValue
                    stringField = stringValue
                })
            }
        }

        // No filter, default sorting
        realm.query(Sample::class)
            .sort(Sample::intField.name)
            .find()
            .let { results ->
                assertEquals(3, results.size)
                values.forEachIndexed { index, (intValue, stringValue) ->
                    assertEquals(intValue, results[index].intField)
                    assertEquals(stringValue, results[index].stringField)
                }
            }

        // No filter, sort descending
        realm.query(Sample::class)
            .sort(Sample::intField.name, QuerySort.DESCENDING)
            .find()
            .let { results ->
                assertEquals(3, results.size)
                values.reversed()
                    .forEachIndexed { index, (intValue, stringValue) ->
                        assertEquals(intValue, results[index].intField)
                        assertEquals(stringValue, results[index].stringField)
                    }
            }

        // No filter, multiple sortings
        realm.query(Sample::class)
            .sort(
                Sample::intField.name to QuerySort.DESCENDING,
                Sample::stringField.name to QuerySort.ASCENDING
            ).find()
            .let { results ->
                assertEquals(3, results.size)
                values.forEachIndexed { index, (intValue, stringValue) ->
                    val intField = results[index].intField
                    val stringField = results[index].stringField
                    assertEquals(intValue, intField)
                    assertEquals(stringValue, stringField)
                }
            }
    }

    @Test
    fun asFlow() {
        val intValue = 13
        val channel = Channel<RealmResults<Sample>>(1)
        runBlocking {
            val observer = async {
                realm.query(Sample::class, "intField = $0", intValue)
                    .asFlow()
                    .collect { results ->
                        if (results.size == 1) {
                            channel.send(results)
                        }
                    }
            }
            realm.write {
                copyToRealm(Sample())
                copyToRealm(Sample().apply { intField = intValue })
            }

            val results = channel.receive()
            assertEquals(1, results.size)
            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun asFlow_throwsInsideWrite() {
        realm.writeBlocking {
            // Check we throw when observing flows inside write transactions
            runBlocking {
                assertFailsWith<IllegalStateException> {
                    query(Sample::class)
                        .asFlow()
                        .collect { /* No-op */ }
                }
            }
        }
    }

    @Test
    fun average_find() {
        realm.query(Sample::class)
            .average(Sample::intField.name, Int::class)
            .find()
            .let { averageValue: Double? -> assertNull(averageValue) }

        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val averageQuery = query(Sample::class).average(Sample::intField.name, Int::class)

            assertNull(averageQuery.find())
            copyToRealm(Sample().apply { intField = 0 })
            copyToRealm(Sample().apply { intField = 10 })
            assertEquals(5.0, averageQuery.find())
        }

        realm.query(Sample::class)
            .average(Sample::intField.name, Int::class)
            .find()
            .let { averageValue -> assertEquals(5.0, averageValue) }
    }

    @Test
    fun average_asFlow() {
        val channel = Channel<Double?>(2)

        runBlocking {
            val observer = async {
                realm.query(Sample::class)
                    .average(Sample::intField.name, Int::class)
                    .asFlow()
                    .collect { averageValue ->
                        channel.send(averageValue)
                    }
            }

            assertNull(channel.receive())

            realm.writeBlocking {
                copyToRealm(Sample().apply { intField = 0 })
                copyToRealm(Sample().apply { intField = 10 })
            }

            assertEquals(5.0, channel.receive())
            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun average_asFlow_throwsInsideWrite() {
        realm.writeBlocking {
            // Check we throw when observing flows inside write transactions
            runBlocking {
                assertFailsWith<IllegalStateException> {
                    query(Sample::class)
                        .average(Sample::intField.name, Int::class)
                        .asFlow()
                        .collect { /* No-op */ }
                }
            }
        }
    }

    @Test
    fun count_find() {
        realm.query(Sample::class)
            .count()
            .find()
            .let { countValue -> assertEquals(0, countValue) }

        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val countQuery = query(Sample::class).count()

            assertEquals(0, countQuery.find())
            copyToRealm(Sample())
            assertEquals(1, countQuery.find())
        }

        realm.query(Sample::class)
            .count()
            .find()
            .let { countValue -> assertEquals(1, countValue) }
    }

    @Test
    fun count_asFlow() {
        val channel = Channel<Long>(1)

        runBlocking {
            val observer = async {
                realm.query(Sample::class)
                    .count()
                    .asFlow()
                    .collect { countValue ->
                        assertNotNull(countValue)
                        channel.send(countValue)
                    }
            }

            assertEquals(0, channel.receive())

            realm.writeBlocking {
                copyToRealm(Sample())
            }

            assertEquals(1, channel.receive())
            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun count_asFlow_throwsInsideWrite() {
        realm.writeBlocking {
            // Check we throw when observing flows inside write transactions
            runBlocking {
                assertFailsWith<IllegalStateException> {
                    query(Sample::class)
                        .count()
                        .asFlow()
                        .collect { /* No-op */ }
                }
            }
        }
    }

    @Test
    fun playground_multiThreadScenario() {
        val channel = Channel<Pair<RealmResults<Sample>, Long?>>(1)
        var query: RealmQuery<Sample>? = null
        val scope = singleThreadDispatcher("1")

        runBlocking {
            realm.writeBlocking {
                copyToRealm(Sample().apply { intField = 666 })
            }

            // Create a non-evaluated query (everything is lazy)
            query = realm.query(Sample::class, "intField == $0", 666)

            // Core pointers are evaluated as soon as we get results!
            assertEquals(1, query!!.find().size)
            assertEquals(1, query!!.count().find())

            val obs = async(scope) {
                // Having evaluated it before, we reuse the query object from a different thread
                val results = query!!.find()
                val count = query!!.count().find()
                channel.send(results to count)
            }
            val pair = channel.receive()
            assertEquals(1, pair.first.size)
            assertEquals(1, pair.second)

            channel.close()
            obs.cancel()
        }
    }
}
