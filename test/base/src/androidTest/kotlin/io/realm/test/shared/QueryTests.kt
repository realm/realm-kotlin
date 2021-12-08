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

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.Sample
import io.realm.query.Sort
import io.realm.query.find
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
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
    fun find_realm() {
        realm.query(Sample::class)
            .find { results -> assertEquals(0, results.size) }

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
    fun find_mutableRealm() {
        val stringValue = "some string"

        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val query = query(Sample::class)

            query.find { results -> assertEquals(0, results.size) }

            assertEquals(0, query.find().size)
            copyToRealm(Sample().apply { stringField = stringValue })
            assertEquals(1, query.find().size)

            // No filter
            query.find { results -> assertEquals(1, results.size) }

            // Filter by string
            query.query("stringField = $0", stringValue)
                .find { results -> assertEquals(1, results.size) }

            // Filter by string that doesn't match
            query.query("stringField = $0", "invalid string")
                .find { results -> assertEquals(0, results.size) }
        }
    }

    @Test
    fun find_realm_malformedQueryThrows() {
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
    fun find_mutableRealm_malformedQueryThrows() {
        realm.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                query(Sample::class, "stringField = $0")
            }.let {
                assertTrue(it.message!!.contains("Have you specified all parameters"))
            }

            assertFailsWith<IllegalArgumentException> {
                query(Sample::class, "stringField = 42")
            }.let {
                assertTrue(it.message!!.contains("Wrong query field"))
            }

            assertFailsWith<IllegalArgumentException> {
                query(Sample::class, "nonExistingField = 13")
            }.let {
                assertTrue(it.message!!.contains("Wrong query field"))
            }
        }
    }

    @Test
    @Ignore
    fun asFlow() {
        // TODO
    }

    @Test
    @Ignore
    fun asFlow_throwsInsideWrite() {
        // TODO
    }

    @Test
    @Suppress("LongMethod")
    fun composedQuery() {
        val joe = "Joe"
        val sylvia = "Sylvia"
        val stacy = "Stacy"
        val ruth = "Ruth"
        val bob = "Bob"

        realm.writeBlocking {
            copyToRealm(
                Sample().apply {
                    intField = 1
                    stringField = joe
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = 2
                    stringField = sylvia
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = 3
                    stringField = stacy
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = 4
                    stringField = ruth
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = 5
                    stringField = bob
                }
            )
        }

        // 5, 4, 3
        realm.query(Sample::class, "intField > 1")
            .query("intField > 2")
            .sort(Sample::intField.name, Sort.DESCENDING)
            .find { results ->
                assertEquals(3, results.size)
                assertEquals(5, results[0].intField)
                assertEquals(4, results[1].intField)
                assertEquals(3, results[2].intField)
            }

        // Sylvia, Stacy
        realm.query(Sample::class, "intField > 1")
            .query("stringField BEGINSWITH[c] $0", "S")
            .sort(Sample::intField.name, Sort.ASCENDING)
            .find { results ->
                assertEquals(2, results.size)
                assertEquals(sylvia, results[0].stringField)
                assertEquals(stacy, results[1].stringField)
            }

        // Ruth
        realm.query(Sample::class)
            .query("stringField BEGINSWITH[c] $0 OR stringField BEGINSWITH[c] $1", "J", "R")
            .query("intField > 1")
            .find { results ->
                assertEquals(1, results.size)
                assertEquals(ruth, results[0].stringField)
            }
    }

    @Test
    @Suppress("LongMethod")
    fun composedQuery_withDescriptor() {
        val value1 = 1
        val value2 = 2
        val value3 = 3
        val value4 = 4
        val value5 = 5
        val joe = "Joe"
        val sylvia = "Sylvia"
        val stacy = "Stacy"
        val ruth = "Ruth"
        val bob = "Bob"

        realm.writeBlocking {
            copyToRealm(
                Sample().apply {
                    intField = value1
                    stringField = joe
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = value2
                    stringField = sylvia
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = value3
                    stringField = stacy
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = value4
                    stringField = ruth
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = value5
                    stringField = bob
                }
            )
        }

        realm.query(Sample::class)
            .query("intField > 2")
            .sort(Sample::intField.name, Sort.DESCENDING)
            .find()
            .let { results ->
                assertEquals(3, results.size)
                assertEquals(value5, results[0].intField)
                assertEquals(value4, results[1].intField)
                assertEquals(value3, results[2].intField)
            }

        realm.query(Sample::class)
            .query("intField > 2")
            .sort(Sample::intField.name, Sort.DESCENDING)
            .limit(1)
            .find()
            .let { results ->
                assertEquals(1, results.size)
                assertEquals(value5, results[0].intField)
            }

        // Descriptor in query string - Bob, Ruth, Stacy
        realm.query(Sample::class)
            .query("intField > 2 SORT(stringField ASCENDING)")
            .find()
            .let { results ->
                assertEquals(3, results.size)
                assertEquals(bob, results[0].stringField)
                assertEquals(ruth, results[1].stringField)
                assertEquals(stacy, results[2].stringField)
            }

        // Descriptor as a function - Bob, Ruth, Stacy
        realm.query(Sample::class)
            .query("intField > 2")
            .sort(Sample::stringField.name, Sort.ASCENDING)
            .find()
            .let { results ->
                assertEquals(3, results.size)
                assertEquals(bob, results[0].stringField)
                assertEquals(ruth, results[1].stringField)
                assertEquals(stacy, results[2].stringField)
            }

        // Conflicting descriptors, query string vs function - Bob, Ruth, Stacy
        realm.query(Sample::class)
            .query("intField > 2 SORT(stringField ASCENDING)")
            .sort(Sample::stringField.name, Sort.DESCENDING)
            .find()
            .let { results ->
                assertEquals(3, results.size)
                assertEquals(bob, results[2].stringField)
                assertEquals(ruth, results[1].stringField)
                assertEquals(stacy, results[0].stringField)
            }

        // Invalid descriptor in query string
        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class)
                .query("intField > 2 SORT(stringField DESCENDINGGGGGGGGGG)")
        }
    }

    @Test
    fun composedQuery_withDescriptors() {
        val value1 = 1
        val value2 = 2
        val value3 = 3
        val value4 = 4
        val sylvia = "Sylvia"
        val stacy = "Stacy"
        val ruth = "Ruth"

        realm.writeBlocking {
            copyToRealm(
                Sample().apply {
                    intField = value1
                    stringField = sylvia
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = value2
                    stringField = sylvia // intentionally repeated
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = value3
                    stringField = stacy
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = value4
                    stringField = ruth
                }
            )
        }

        // Ruth 4, Stacy 3
        realm.query(Sample::class)
            .distinct(Sample::stringField.name) // Sylvia, Stacy, Ruth
            .sort(Sample::stringField.name, Sort.ASCENDING) // Ruth, Stacy, Sylvia
            .limit(2) // Ruth 4, Stacy 3
            .find { results ->
                assertEquals(2, results.size)
                assertEquals(ruth, results[0].stringField)
                assertEquals(stacy, results[1].stringField)
            }
    }

    @Test
    fun composedQuery_withDescriptorsAndQueryAgain() {
        val value1 = 1
        val value2 = 2
        val value3 = 3
        val value4 = 4
        val sylvia = "Sylvia"
        val stacy = "Stacy"
        val ruth = "Ruth"

        realm.writeBlocking {
            copyToRealm(
                Sample().apply {
                    intField = value1
                    stringField = sylvia
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = value2
                    stringField = sylvia // intentionally repeated
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = value3
                    stringField = stacy
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = value4
                    stringField = ruth
                }
            )
        }

        // Ruth 4, Stacy 3
        realm.query(Sample::class)
            .distinct(Sample::stringField.name) // Sylvia, Stacy, Ruth
            .sort(Sample::stringField.name, Sort.ASCENDING) // Ruth, Stacy, Sylvia
            .limit(2) // Ruth 4, Stacy 3
            .find { results ->
                assertEquals(2, results.size)
                assertEquals(ruth, results[0].stringField)
                assertEquals(stacy, results[1].stringField)
            }

        // Stacy 3, Sylvia 1
        // One would expect this to be Stacy 3 but notice the last 'query', it moves the descriptors
        // to the end - this is how it is also implemented in Java
        realm.query(Sample::class)
            .distinct(Sample::stringField.name) // normal execution: Sylvia 1, Stacy 3, Ruth 4
            .sort(
                Sample::stringField.name,
                Sort.ASCENDING
            ) // normal execution: Ruth 4, Stacy 4, Sylvia 1
            .limit(2) // normal execution: Ruth 4, Stacy 3
            .query("intField < $value4") // this puts all the previous descriptors at the end of this query!
            .find() // results for: "intField < 4 DISTINCT('stringField')"
            .let { results ->
                assertEquals(2, results.size)
                assertEquals(stacy, results[0].stringField)
                assertEquals(sylvia, results[1].stringField)
            }
    }

    @Test
    fun sort_emptyResults() {
        realm.query(Sample::class)
            .sort(Sample::intField.name)
            .find { results ->
                assertTrue(results.isEmpty())
            }
    }

    @Test
    fun sort_singleField() {
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
            .sort(Sample::intField.name to Sort.DESCENDING)
            .find { results ->
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
    fun sort_multipleFields() {
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

        // No filter, multiple sortings
        realm.query(Sample::class)
            .sort(
                Sample::stringField.name to Sort.DESCENDING,
                Sample::intField.name to Sort.ASCENDING
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
    fun sort_throwsIfInvalidProperty() {
        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class)
                .sort("invalid")
        }
    }

    @Test
    fun distinct() {
        val value1 = 1
        val value2 = 2
        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value1 }) // repeated intentionally
            copyToRealm(Sample().apply { intField = value2 })
        }

        realm.query(Sample::class)
            .distinct(Sample::intField.name)
            .find { results ->
                assertEquals(2, results.size)
            }

        realm.query(Sample::class)
            .distinct(Sample::intField.name)
            .sort(Sample::intField.name, Sort.ASCENDING)
            .find { results ->
                assertEquals(2, results.size)
                assertEquals(value1, results[0].intField)
                assertEquals(value2, results[1].intField)
            }
    }

    @Test
    fun distinct_multipleFields() {
        val value1 = 1
        val value2 = 2
        realm.writeBlocking {
            copyToRealm(
                Sample().apply {
                    intField = value1
                    longField = value1.toLong()
                }
            )
            copyToRealm(
                // Mixing values for different fields in this object
                Sample().apply {
                    intField = value1
                    longField = value2.toLong()
                }
            )
            copyToRealm(
                // Intentionally inserting the same values as specified in the previous object
                Sample().apply {
                    intField = value1
                    longField = value2.toLong()
                }
            )
            copyToRealm(
                Sample().apply {
                    intField = value2
                    longField = value2.toLong()
                }
            )
        }

        realm.query(Sample::class)
            .distinct(Sample::intField.name, Sample::longField.name)
            .find { results ->
                assertEquals(3, results.size)
            }
    }

    @Test
    fun limit() {
        val value1 = 1
        val value2 = 2
        val value3 = 3
        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = value1 })
            copyToRealm(Sample().apply { intField = value2 })
            copyToRealm(Sample().apply { intField = value3 })
        }

        realm.query(Sample::class)
            .sort(Sample::intField.name, Sort.DESCENDING)
            .limit(2)
            .find { results ->
                assertEquals(2, results.size)
                assertEquals(value3, results[0].intField)
            }

        realm.query(Sample::class)
            .sort(Sample::intField.name, Sort.DESCENDING)
            .limit(2)
            .limit(1)
            .find { results ->
                assertEquals(1, results.size)
                assertEquals(value3, results[0].intField)
            }
    }

    @Test
    @Ignore
    fun limit_asSubQuery() {
        // TODO
    }

    @Test
    fun limit_throwsIfInvalidValue() {
        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class)
                .limit(-42)
        }
    }

    @Test
    @Ignore
    fun average_generic_emptyColumns() {
        // TODO
    }

    @Test
    @Ignore
    fun average_double_emptyColumns() {
        // TODO
    }

    @Test
    @Ignore
    fun average_generic_find() {
        // TODO
    }

    @Test
    @Ignore
    fun average_generic_find_throwsIfInvalidType() {
        // TODO
    }

    @Test
    @Ignore
    fun average_generic_asFlow() {
        // TODO
    }

    @Test
    @Ignore
    fun average_generic_asFlow_throwsIfInvalidType() {
        // TODO
    }

    @Test
    @Ignore
    fun average_generic_asFlow_throwsIfInvalidProperty() {
        // TODO
    }

    @Test
    @Ignore
    fun average_double_find() {
        // TODO
    }

    @Test
    @Ignore
    fun average_double_find_throwsIfInvalidProperty() {
        // TODO
    }

    @Test
    @Ignore
    fun average_double_asFlow() {
        // TODO
    }

    @Test
    @Ignore
    fun average_double_asFlow_throwsIfInvalidType() {
        // TODO
    }

    @Test
    @Ignore
    fun average_asFlow_throwsInsideWrite() {
        // TODO
    }

    @Test
    fun count_find_empty() {
        realm.query(Sample::class)
            .count()
            .find { countValue -> assertEquals(0, countValue) }
    }

    @Test
    fun count_find() {
        realm.writeBlocking {
            // Queries inside a write transaction produce live results which means they can be
            // reused within the closure
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
    fun sum_find_empty() {
        realm.query(Sample::class)
            .sum(Sample::intField.name, Int::class)
            .find { sumValue: Int? -> assertEquals(0, sumValue) }
    }

    @Test
    fun sum_find() {
        val value1 = 2
        val value2 = 7
        val expectedSum = value1 + value2

        realm.writeBlocking {
            // Queries inside a write transaction produce live results which means they can be
            // reused within the closure
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
    fun sum_find_throwsIfInvalidProperty() {
        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class)
                .sum(Sample::stringField.name, Int::class)
                .find()
        }
    }

    @Test
    fun sum_find_throwsIfInvalidType() {
        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class)
                .sum(Sample::intField.name, String::class)
                .find()
        }
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
    fun sum_asFlow_throwsIfInvalidType() {
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
    fun sum_asFlow_throwsIfInvalidProperty() {
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
    fun max_find_empty() {
        realm.query(Sample::class)
            .max(Sample::intField.name, Int::class)
            .find { maxValue: Int? -> assertNull(maxValue) }
    }

    @Test
    fun max_find() {
        val value1 = 2
        val value2 = 7

        realm.query(Sample::class)
            .max(Sample::intField.name, Int::class)
            .find { maxValue: Int? -> assertNull(maxValue) }

        realm.writeBlocking {
            // Queries inside a write transaction produce live results which means they can be
            // reused within the closure
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
    fun max_find_throwsIfInvalidProperty() {
        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class)
                .max(Sample::stringField.name, Int::class)
                .find()
        }
    }

    @Test
    fun max_find_throwsIfInvalidType() {
        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = 1 })
            copyToRealm(Sample().apply { intField = 2 })
        }

        // TODO this won't fail unless we have previously stored something, otherwise it returns
        //  null and nothing will trigger an exception!
        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class)
                .max(Sample::intField.name, String::class)
                .find()
        }
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
    fun max_asFlow_throwsIfInvalidType() {
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
    fun max_asFlow_throwsIfInvalidProperty() {
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
    fun min_find_empty() {
        realm.query(Sample::class)
            .min(Sample::intField.name, Int::class)
            .find { minValue: Int? -> assertNull(minValue) }
    }

    @Test
    fun min_find() {
        val value1 = 2
        val value2 = 7

        realm.query(Sample::class)
            .min(Sample::intField.name, Int::class)
            .find { minValue: Int? -> assertNull(minValue) }

        realm.writeBlocking {
            // Queries inside a write transaction produce live results which means they can be
            // reused within the closure
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
    fun min_find_throwsIfInvalidProperty() {
        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class)
                .min(Sample::stringField.name, Int::class)
                .find()
        }
    }

    @Test
    fun min_find_throwsIfInvalidType() {
        realm.writeBlocking {
            copyToRealm(Sample().apply { intField = 1 })
            copyToRealm(Sample().apply { intField = 2 })
        }

        // TODO this won't fail unless we have previously stored something, otherwise it returns
        //  null and nothing will trigger an exception!
        assertFailsWith<IllegalArgumentException> {
            realm.query(Sample::class)
                .min(Sample::intField.name, String::class)
                .find()
        }
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
    fun min_asFlow_throwsIfInvalidType() {
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
    fun min_asFlow_throwsIfInvalidProperty() {
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
    @Ignore
    fun first_find() {
        // TODO
    }

    @Test
    @Ignore
    fun first_find_empty() {
        // TODO
    }

    @Test
    @Ignore
    fun first_asFlow() {
        // TODO
    }

//    @Test
//    fun playground_multiThreadScenario() {
//        val channel = Channel<Pair<RealmResults<Sample>, Long?>>(1)
//        var query: RealmQuery<Sample>? = null
//        val scope = singleThreadDispatcher("1")
//
//        runBlocking {
//            realm.writeBlocking {
//                copyToRealm(Sample().apply { intField = 666 })
//            }
//
//            // Create a non-evaluated query (everything is lazy)
//            query = realm.query(Sample::class, "intField == $0", 666)
//
//            // Core pointers are evaluated as soon as we get results!
//            assertEquals(1, query!!.find().size)
//            assertEquals(1, query!!.count().find())
//
//            val obs = async(scope) {
//                // Having evaluated it before, we reuse the query object from a different thread
//                val results = query!!.find()
//                val count = query!!.count().find()
//                channel.send(results to count)
//            }
//            val pair = channel.receive()
//            assertEquals(1, pair.first.size)
//            assertEquals(1, pair.second)
//
//            channel.close()
//            obs.cancel()
//        }
//    }
}
