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
import io.realm.find
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("LargeClass")
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
            .find { results: RealmResults<Sample> -> assertEquals(0, results.size) }

        val stringValue = "some string"
        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val query = query(Sample::class)

            assertEquals(0, query.find().size)
            copyToRealm(Sample().apply { stringField = stringValue })
            assertEquals(1, query.find().size)
        }

        // No filter
        realm.query(Sample::class)
            .find { results -> assertEquals(1, results.size) }

        // Filter by string
        realm.query(Sample::class, "stringField = $0", stringValue)
            .find { results -> assertEquals(1, results.size) }

        // Filter by string that doesn't match
        realm.query(Sample::class, "stringField = $0", "invalid string")
            .find { results -> assertEquals(0, results.size) }
    }

    @Test
    fun find_malformedQueryThrows() {
        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class, "stringField = $0")
        }.let {
            assertTrue(it.message!!.contains("Have you specified all parameters"))
        }

        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class, "stringField = 42")
        }.let {
            assertTrue(it.message!!.contains("Wrong query field"))
        }

        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class, "nonExistingField = 13")
        }.let {
            assertTrue(it.message!!.contains("Wrong query field"))
        }
    }

    @Test
    fun sort() {
        val john = 6 to "John"
        val mary = 10 to "Mary"
        val ruth = 13 to "Ruth"
        val values = listOf(john, mary, ruth)
        realm.writeBlocking {
            values.forEach { (intValue, stringValue) ->
                copyToRealm(
                    Sample().apply {
                        intField = intValue
                        stringField = stringValue
                    }
                )
            }
        }

        // No filter, default ascending sorting
        realm.query(Sample::class)
            .sort(Sample::intField.name)
            .find { results ->
                assertEquals(3, results.size)
                assertEquals(john.first, results[0].intField)
                assertEquals(john.second, results[0].stringField)
                assertEquals(mary.first, results[1].intField)
                assertEquals(mary.second, results[1].stringField)
                assertEquals(ruth.first, results[2].intField)
                assertEquals(ruth.second, results[2].stringField)
            }

        // No filter, sort descending
        realm.query(Sample::class)
            .sort(Sample::intField.name, QuerySort.DESCENDING)
            .find { results ->
                assertEquals(3, results.size)
                assertEquals(ruth.first, results[0].intField)
                assertEquals(ruth.second, results[0].stringField)
                assertEquals(mary.first, results[1].intField)
                assertEquals(mary.second, results[1].stringField)
                assertEquals(john.first, results[2].intField)
                assertEquals(john.second, results[2].stringField)
            }

        // No filter, multiple sortings
        realm.query(Sample::class)
            .sort(
                Sample::stringField.name to QuerySort.DESCENDING,
                Sample::intField.name to QuerySort.ASCENDING
            ).find { results ->
                assertEquals(3, results.size)
                assertEquals(ruth.first, results[0].intField)
                assertEquals(ruth.second, results[0].stringField)
                assertEquals(mary.first, results[1].intField)
                assertEquals(mary.second, results[1].stringField)
                assertEquals(john.first, results[2].intField)
                assertEquals(john.second, results[2].stringField)
            }
    }

    @Test
    fun sort_throwsWhenCalledMoreThanOnce() {
        assertFailsWith<IllegalStateException> {
            realm.query(Sample::class)
                .sort(Sample::intField.name)
                .sort(Sample::stringField.name)
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
                }
            }
        }
    }

    @Test
    fun average_generic_find() {
        val value1 = 2
        val value2 = 7
        val expectedAverage = ((value1 + value2) / 2.0).roundToInt()

        realm.query(Sample::class)
            .average(Sample::intField.name, Int::class)
            .find { averageValue: Int? -> assertNull(averageValue) }

        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val averageQuery = query(Sample::class).average(Sample::intField.name, Int::class)

            assertNull(averageQuery.find())
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
            assertEquals(expectedAverage, averageQuery.find())
        }

        realm.query(Sample::class)
            .average(Sample::intField.name, Int::class)
            .find { averageValue: Int? -> assertEquals(expectedAverage, averageValue) }
    }

    @Test
    fun average_generic_find_throwsIfInvalidType() {
        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = 1 })
            copyToRealm(Sample().apply { intField = 2 })
        }

        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class)
                .average(Sample::intField.name, String::class)
                .find()
        }.let {
            assertTrue(it.message!!.contains("Invalid numeric type"))
        }
    }

    @Test
    fun average_generic_asFlow() {
        val channel = Channel<Int?>(1)
        val value1 = 2
        val value2 = 7
        val expectedAverage = ((value1 + value2) / 2.0).roundToInt()

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
                copyToRealm(Sample().apply { intField = value1 })
                copyToRealm(Sample().apply { intField = value2 })
            }

            assertEquals(expectedAverage, channel.receive())
            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun average_generic_asFlow_throwsIfInvalidType() {
        val value1 = 2
        val value2 = 7

        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
        }

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query(Sample::class)
                    .average(Sample::intField.name, String::class)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid numeric type"))
            }
        }
    }

    @Test
    fun average_generic_asFlow_throwsIfInvalidProperty() {
        val value1 = 2
        val value2 = 7

        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
        }

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query(Sample::class)
                    .average(Sample::stringField.name, String::class)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid query formulation"))
            }
        }
    }

    @Test
    fun average_double_find() {
        val value1 = 2
        val value2 = 7
        val expectedAverage = (value1 + value2) / 2.0

        realm.query(Sample::class)
            .average(Sample::intField.name)
            .find { averageValue: Double? -> assertNull(averageValue) }

        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val averageQuery = query(Sample::class)
                .average(Sample::intField.name)

            assertNull(averageQuery.find())
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
            assertEquals(expectedAverage, averageQuery.find())
        }

        realm.query(Sample::class)
            .average(Sample::intField.name)
            .find { averageValue: Double? -> assertEquals(expectedAverage, averageValue) }
    }

    @Test
    fun average_double_find_throwsIfInvalidProperty() {
        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = 1 })
            copyToRealm(Sample().apply { intField = 2 })
        }

        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class)
                .average(Sample::stringField.name)
                .find()
        }.let {
            assertTrue(it.message!!.contains("Invalid query formulation"))
        }
    }

    @Test
    fun average_double_asFlow() {
        val channel = Channel<Double?>(1)
        val value1 = 2
        val value2 = 7
        val expectedAverage = (value1 + value2) / 2.0

        runBlocking {
            val observer = async {
                realm.query(Sample::class)
                    .average(Sample::intField.name)
                    .asFlow()
                    .collect { averageValue ->
                        channel.send(averageValue)
                    }
            }

            assertNull(channel.receive())

            realm.writeBlocking {
                copyToRealm(Sample().apply { intField = value1 })
                copyToRealm(Sample().apply { intField = value2 })
            }

            assertEquals(expectedAverage, channel.receive())
            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun average_double_asFlow_throwsIfInvalidType() {
        val value1 = 2
        val value2 = 7

        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
        }

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query(Sample::class)
                    .average(Sample::stringField.name)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid query formulation"))
            }
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
                }
            }
        }
    }

    @Test
    fun count_find() {
        realm.query(Sample::class)
            .count()
            .find { countValue -> assertEquals(0, countValue) }

        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val countQuery = query(Sample::class).count()

            assertEquals(0, countQuery.find())
            copyToRealm(Sample())
            assertEquals(1, countQuery.find())
        }

        realm.query(Sample::class)
            .count()
            .find { countValue -> assertEquals(1, countValue) }
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
                }
            }
        }
    }

    @Test
    fun sum_find() {
        val value1 = 2
        val value2 = 7
        val expectedSum = value1 + value2

        realm.query(Sample::class)
            .sum(Sample::intField.name, Int::class)
            .find { sumValue: Int? -> assertEquals(0, sumValue) }

        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val sumQuery = query(Sample::class)
                .sum(Sample::intField.name, Int::class)

            assertEquals(0, sumQuery.find())
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
            assertEquals(expectedSum, sumQuery.find())
        }

        realm.query(Sample::class)
            .sum(Sample::intField.name, Int::class)
            .find { sumValue: Int? -> assertEquals(expectedSum, sumValue) }
    }

    @Test
    fun sum_asFlow() {
        val channel = Channel<Int?>(1)
        val value1 = 2
        val value2 = 7
        val expectedSum = value1 + value2

        runBlocking {
            val observer = async {
                realm.query(Sample::class)
                    .sum(Sample::intField.name, Int::class)
                    .asFlow()
                    .collect { sumValue ->
                        channel.send(sumValue)
                    }
            }

            assertEquals(0, channel.receive())

            realm.writeBlocking {
                copyToRealm(Sample().apply { intField = value1 })
                copyToRealm(Sample().apply { intField = value2 })
            }

            assertEquals(expectedSum, channel.receive())
            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun sum_generic_asFlow_throwsIfInvalidType() {
        val value1 = 2
        val value2 = 7

        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
        }

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query(Sample::class)
                    .sum(Sample::intField.name, String::class)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid numeric type"))
            }
        }
    }

    @Test
    fun sum_generic_asFlow_throwsIfInvalidProperty() {
        val value1 = 2
        val value2 = 7

        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
        }

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query(Sample::class)
                    .sum(Sample::stringField.name, String::class)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid query formulation"))
            }
        }
    }

    @Test
    fun max_find() {
        val value1 = 2
        val value2 = 7

        realm.query(Sample::class)
            .max(Sample::intField.name, Int::class)
            .find { maxValue: Int? -> assertNull(maxValue) }

        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val maxQuery = query(Sample::class)
                .max(Sample::intField.name, Int::class)

            assertNull(maxQuery.find())
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
            assertEquals(value2, maxQuery.find())
        }

        realm.query(Sample::class)
            .max(Sample::intField.name, Int::class)
            .find { maxValue: Int? -> assertEquals(value2, maxValue) }
    }

    @Test
    fun max_asFlow() {
        val channel = Channel<Int?>(1)
        val value1 = 2
        val value2 = 7

        runBlocking {
            val observer = async {
                realm.query(Sample::class)
                    .max(Sample::intField.name, Int::class)
                    .asFlow()
                    .collect { maxValue ->
                        channel.send(maxValue)
                    }
            }

            assertNull(channel.receive())

            realm.writeBlocking {
                copyToRealm(Sample().apply { intField = value1 })
                copyToRealm(Sample().apply { intField = value2 })
            }

            assertEquals(value2, channel.receive())
            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun max_generic_asFlow_throwsIfInvalidType() {
        val value1 = 2
        val value2 = 7

        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
        }

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query(Sample::class)
                    .max(Sample::intField.name, String::class)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid numeric type"))
            }
        }
    }

    @Test
    fun max_generic_asFlow_throwsIfInvalidProperty() {
        val value1 = 2
        val value2 = 7

        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
        }

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query(Sample::class)
                    .max(Sample::stringField.name, String::class)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid query formulation"))
            }
        }
    }

    @Test
    fun min_find() {
        val value1 = 2
        val value2 = 7

        realm.query(Sample::class)
            .min(Sample::intField.name, Int::class)
            .find { minValue: Int? -> assertNull(minValue) }

        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val minQuery = query(Sample::class)
                .min(Sample::intField.name, Int::class)

            assertNull(minQuery.find())
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
            assertEquals(value1, minQuery.find())
        }

        realm.query(Sample::class)
            .min(Sample::intField.name, Int::class)
            .find { minValue: Int? -> assertEquals(value1, minValue) }
    }

    @Test
    fun min_asFlow() {
        val channel = Channel<Int?>(1)
        val value1 = 2
        val value2 = 7

        runBlocking {
            val observer = async {
                realm.query(Sample::class)
                    .min(Sample::intField.name, Int::class)
                    .asFlow()
                    .collect { minValue ->
                        channel.send(minValue)
                    }
            }

            assertNull(channel.receive())

            realm.writeBlocking {
                copyToRealm(Sample().apply { intField = value1 })
                copyToRealm(Sample().apply { intField = value2 })
            }

            assertEquals(value1, channel.receive())
            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun min_generic_asFlow_throwsIfInvalidType() {
        val value1 = 2
        val value2 = 7

        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
        }

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query(Sample::class)
                    .min(Sample::intField.name, String::class)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid numeric type"))
            }
        }
    }

    @Test
    fun min_generic_asFlow_throwsIfInvalidProperty() {
        val value1 = 2
        val value2 = 7

        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
        }

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query(Sample::class)
                    .min(Sample::stringField.name, String::class)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid query formulation"))
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
