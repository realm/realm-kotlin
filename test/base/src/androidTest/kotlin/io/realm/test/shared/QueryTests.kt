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

@file:Suppress("invisible_reference", "invisible_member")

package io.realm.test.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmInstant
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.internal.interop.CollectionType
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.internal.query.AggregatorQueryType
import io.realm.query
import io.realm.query.RealmQuery
import io.realm.query.Sort
import io.realm.query.find
import io.realm.query.max
import io.realm.query.min
import io.realm.query.sum
import io.realm.realmListOf
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
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
        val configuration = RealmConfiguration.Builder(schema = setOf(QuerySample::class))
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
        realm.query(QuerySample::class)
            .find { results -> assertEquals(0, results.size) }

        val stringValue = "some string"
        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val query = query(QuerySample::class)

            assertEquals(0, query.find().size)
            copyToRealm(QuerySample(stringField = stringValue))
            assertEquals(1, query.find().size)
        }

        // No filter
        realm.query(QuerySample::class)
            .find { results -> assertEquals(1, results.size) }

        // Filter by string
        realm.query(QuerySample::class, "stringField = $0", stringValue)
            .find { results -> assertEquals(1, results.size) }

        // Filter by string that doesn't match
        realm.query(QuerySample::class, "stringField = $0", "invalid string")
            .find { results -> assertEquals(0, results.size) }
    }

    @Test
    fun find_mutableRealm() {
        val stringValue = "some string"

        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val query = query(QuerySample::class)

            query.find { results -> assertEquals(0, results.size) }

            assertEquals(0, query.find().size)
            copyToRealm(QuerySample(stringField = stringValue))
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
            realm.query(QuerySample::class, "stringField = $0")
        }.let {
            assertTrue(it.message!!.contains("Have you specified all parameters"))
        }

        assertFailsWith<IllegalArgumentException> {
            realm.query(QuerySample::class, "stringField = 42")
        }.let {
            assertTrue(it.message!!.contains("Wrong query field"))
        }

        assertFailsWith<IllegalArgumentException> {
            realm.query(QuerySample::class, "nonExistingField = 13")
        }.let {
            assertTrue(it.message!!.contains("Wrong query field"))
        }
    }

    @Test
    fun find_mutableRealm_malformedQueryThrows() {
        realm.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                query(QuerySample::class, "stringField = $0")
            }.let {
                assertTrue(it.message!!.contains("Have you specified all parameters"))
            }

            assertFailsWith<IllegalArgumentException> {
                query(QuerySample::class, "stringField = 42")
            }.let {
                assertTrue(it.message!!.contains("Wrong query field"))
            }

            assertFailsWith<IllegalArgumentException> {
                query(QuerySample::class, "nonExistingField = 13")
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
            copyToRealm(QuerySample(intField = 1, stringField = joe))
            copyToRealm(QuerySample(intField = 2, stringField = sylvia))
            copyToRealm(QuerySample(intField = 3, stringField = stacy))
            copyToRealm(QuerySample(intField = 4, stringField = ruth))
            copyToRealm(QuerySample(intField = 5, stringField = bob))
        }

        // 5, 4, 3
        realm.query(QuerySample::class, "intField > 1")
            .query("intField > 2")
            .sort(QuerySample::intField.name, Sort.DESCENDING)
            .find { results ->
                assertEquals(3, results.size)
                assertEquals(5, results[0].intField)
                assertEquals(4, results[1].intField)
                assertEquals(3, results[2].intField)
            }

        // Sylvia, Stacy
        realm.query(QuerySample::class, "intField > 1")
            .query("stringField BEGINSWITH[c] $0", "S")
            .sort(QuerySample::intField.name, Sort.ASCENDING)
            .find { results ->
                assertEquals(2, results.size)
                assertEquals(sylvia, results[0].stringField)
                assertEquals(stacy, results[1].stringField)
            }

        // Ruth
        realm.query(QuerySample::class)
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
            copyToRealm(QuerySample(intField = value1, stringField = joe))
            copyToRealm(QuerySample(intField = value2, stringField = sylvia))
            copyToRealm(QuerySample(intField = value3, stringField = stacy))
            copyToRealm(QuerySample(intField = value4, stringField = ruth))
            copyToRealm(QuerySample(intField = value5, stringField = bob))
        }

        // Explicit descriptor function
        realm.query(QuerySample::class)
            .query("intField > 2")
            .sort(QuerySample::intField.name, Sort.DESCENDING)
            .find()
            .let { results ->
                assertEquals(3, results.size)
                assertEquals(value5, results[0].intField)
                assertEquals(value4, results[1].intField)
                assertEquals(value3, results[2].intField)
            }

        // Limit
        realm.query(QuerySample::class)
            .query("intField > 2")
            .sort(QuerySample::intField.name, Sort.DESCENDING)
            .limit(1)
            .find()
            .let { results ->
                assertEquals(1, results.size)
                assertEquals(value5, results[0].intField)
            }

        // Descriptor in query string - Bob, Ruth, Stacy
        realm.query(QuerySample::class)
            .query("intField > 2 SORT(stringField ASCENDING)")
            .find()
            .let { results ->
                assertEquals(3, results.size)
                assertEquals(bob, results[0].stringField)
                assertEquals(ruth, results[1].stringField)
                assertEquals(stacy, results[2].stringField)
            }

        // Descriptor as a function - Bob, Ruth, Stacy
        realm.query(QuerySample::class)
            .query("intField > 2")
            .sort(QuerySample::stringField.name, Sort.ASCENDING)
            .find()
            .let { results ->
                assertEquals(3, results.size)
                assertEquals(bob, results[0].stringField)
                assertEquals(ruth, results[1].stringField)
                assertEquals(stacy, results[2].stringField)
            }

        // Descriptors in both query string and function - Bob, Ruth, Stacy
        realm.query(QuerySample::class)
            .query("intField > 2 LIMIT(4)")
            .sort(QuerySample::stringField.name, Sort.DESCENDING)
            .limit(3)
            .find()
            .let { results ->
                assertEquals(3, results.size)
                assertEquals(bob, results[2].stringField)
                assertEquals(ruth, results[1].stringField)
                assertEquals(stacy, results[0].stringField)
            }

        // Conflicting descriptors, query string vs function - Bob, Ruth, Stacy
        realm.query(QuerySample::class)
            .query("intField > 2 SORT(stringField ASCENDING)")
            .sort(QuerySample::stringField.name, Sort.DESCENDING)
            .find()
            .let { results ->
                assertEquals(3, results.size)
                assertEquals(bob, results[2].stringField)
                assertEquals(ruth, results[1].stringField)
                assertEquals(stacy, results[0].stringField)
            }

        // Invalid descriptor in query string
        assertFailsWith<IllegalArgumentException> {
            realm.query(QuerySample::class)
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
            copyToRealm(QuerySample(intField = value1, stringField = sylvia))
            // intentionally repeated
            copyToRealm(QuerySample(intField = value2, stringField = sylvia))
            copyToRealm(QuerySample(intField = value3, stringField = stacy))
            copyToRealm(QuerySample(intField = value4, stringField = ruth))
        }

        // Ruth 4, Stacy 3
        realm.query(QuerySample::class)
            .distinct(QuerySample::stringField.name) // Sylvia, Stacy, Ruth
            .sort(QuerySample::stringField.name, Sort.ASCENDING) // Ruth, Stacy, Sylvia
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
            copyToRealm(QuerySample(intField = value1, stringField = sylvia))
            // intentionally repeated
            copyToRealm(QuerySample(intField = value2, stringField = sylvia))
            copyToRealm(QuerySample(intField = value3, stringField = stacy))
            copyToRealm(QuerySample(intField = value4, stringField = ruth))
        }

        // Ruth 4, Stacy 3
        realm.query(QuerySample::class)
            .distinct(QuerySample::stringField.name) // Sylvia, Stacy, Ruth
            .sort(QuerySample::stringField.name, Sort.ASCENDING) // Ruth, Stacy, Sylvia
            .limit(2) // Ruth 4, Stacy 3
            .find { results ->
                assertEquals(2, results.size)
                assertEquals(ruth, results[0].stringField)
                assertEquals(stacy, results[1].stringField)
            }

        // Stacy 3, Sylvia 1
        // One would expect this to be Stacy 3 but notice the last 'query', it moves the descriptors
        // to the end - this is how it is also implemented in Java
        realm.query(QuerySample::class)
            .distinct(QuerySample::stringField.name) // normal execution: Sylvia 1, Stacy 3, Ruth 4
            .sort(
                QuerySample::stringField.name,
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
    fun sort_emptyTable() {
        realm.query(QuerySample::class)
            .sort(QuerySample::intField.name)
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
                copyToRealm(QuerySample(intField = intValue, stringField = stringValue))
            }
        }

        // No filter, default ascending sorting
        realm.query(QuerySample::class)
            .sort(QuerySample::intField.name)
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
        realm.query(QuerySample::class)
            .sort(QuerySample::intField.name to Sort.DESCENDING)
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
                copyToRealm(QuerySample(intField = intValue, stringField = stringValue))
            }
        }

        // No filter, multiple sortings
        realm.query(QuerySample::class)
            .sort(
                QuerySample::stringField.name to Sort.DESCENDING,
                QuerySample::intField.name to Sort.ASCENDING
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
            realm.query(QuerySample::class)
                .sort("invalid")
        }
    }

    @Test
    fun distinct() {
        val value1 = 1
        val value2 = 2
        realm.writeBlocking {
            copyToRealm(QuerySample(intField = value1))
            copyToRealm(QuerySample(intField = value1)) // repeated intentionally
            copyToRealm(QuerySample(intField = value2))
        }

        realm.query(QuerySample::class)
            .distinct(QuerySample::intField.name)
            .find { results ->
                assertEquals(2, results.size)
            }

        realm.query(QuerySample::class)
            .distinct(QuerySample::intField.name)
            .sort(QuerySample::intField.name, Sort.ASCENDING)
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
            copyToRealm(QuerySample(intField = value1, longField = value1.toLong()))
            // Mixing values for different fields in this object
            copyToRealm(QuerySample(intField = value1, longField = value2.toLong()))
            // Intentionally inserting the same values as specified in the previous object
            copyToRealm(QuerySample(intField = value1, longField = value2.toLong()))
            copyToRealm(QuerySample(intField = value2, longField = value2.toLong()))
        }

        realm.query(QuerySample::class)
            .distinct(QuerySample::intField.name, QuerySample::longField.name)
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
            copyToRealm(QuerySample(intField = value1))
            copyToRealm(QuerySample(intField = value2))
            copyToRealm(QuerySample(intField = value3))
        }

        realm.query(QuerySample::class)
            .sort(QuerySample::intField.name, Sort.DESCENDING)
            .limit(2)
            .find { results ->
                assertEquals(2, results.size)
                assertEquals(value3, results[0].intField)
            }

        realm.query(QuerySample::class)
            .sort(QuerySample::intField.name, Sort.DESCENDING)
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
            realm.query(QuerySample::class)
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
    fun count_find_emptyTable() {
        realm.query(QuerySample::class)
            .count()
            .find { countValue -> assertEquals(0, countValue) }
    }

    @Test
    fun count_find() {
        realm.writeBlocking {
            // Queries inside a write transaction produce live results which means they can be
            // reused within the closure
            val countQuery = query(QuerySample::class).count()

            assertEquals(0, countQuery.find())
            copyToRealm(QuerySample())
            assertEquals(1, countQuery.find())
        }

        realm.query(QuerySample::class)
            .count()
            .find { countValue -> assertEquals(1, countValue) }
    }

    @Test
    @Ignore
    fun count_asFlow() {
        // TODO
    }

    @Test
    @Ignore
    fun count_asFlow_throwsInsideWrite() {
        // TODO
    }

    @Test
    fun sum_find_emptyTable() {
        val assertions = { propertyDescriptor: PropertyDescriptor ->
            realm.query(QuerySample::class)
                .sum(propertyDescriptor.property.name, propertyDescriptor.clazz)
                .find { sumValue: Any? ->
                    if (sumValue is Number) {
                        assertEquals(0, sumValue.toInt())
                    }
                }
        }

        // Iterate over all properties - exclude RealmInstant
        for (propertyDescriptor in allPropertyDescriptorsForSum) {
            assertions(propertyDescriptor)
        }
    }

    @Test
    fun sum_find_allNullValues() {
        val assertions = { propertyDescriptor: PropertyDescriptor ->
            realm.writeBlocking {
                // Queries inside a write transaction produce live results which means they can be
                // reused within the closure
                val sumQuery = query(QuerySample::class)
                    .sum(propertyDescriptor.property.name, propertyDescriptor.clazz)

                // The sum of all null values is 0
                sumQuery.find { sumValue ->
                    if (sumValue is Number) {
                        assertEquals(0, sumValue.toInt())
                    }
                }
                copyToRealm(getInstance(propertyDescriptor, QuerySample()))
                copyToRealm(getInstance(propertyDescriptor, QuerySample()))
                sumQuery.find { sumValue ->
                    if (sumValue is Number) {
                        assertEquals(0, sumValue.toInt())
                    }
                }
            }

            realm.query(QuerySample::class)
                .sum(propertyDescriptor.property.name, propertyDescriptor.clazz)
                .find { sumValue ->
                    if (sumValue is Number) {
                        assertEquals(0, sumValue.toInt())
                    }
                }

            // Make sure to delete all objects after assertions as aggregators to clean state and
            // avoid "null vs 0" results when testing
            cleanUpBetweenProperties()
        }

        // Iterate over nullable properties containing both null and non-null values - exclude RealmInstant
        for (nullablePropertyDescriptor in nullablePropertyDescriptorsForSum) {
            assertions(nullablePropertyDescriptor)
        }
    }

    @Test
    fun sum_find() {
        val assertions = { propertyDescriptor: PropertyDescriptor ->
            val expectedSum = expectedSum(propertyDescriptor.clazz)
            realm.writeBlocking {
                val sumQuery = query(QuerySample::class)
                    .sum(propertyDescriptor.property.name, propertyDescriptor.clazz)

                val sumValueBefore = sumQuery.find()
                if (sumValueBefore is Number) {
                    assertEquals(0, sumValueBefore.toInt())
                }

                copyToRealm(getInstance(propertyDescriptor, QuerySample(), 0))
                copyToRealm(getInstance(propertyDescriptor, QuerySample(), 1))

                val sumValueAfter = sumQuery.find()
                if (sumValueAfter is Number) {
                    assertEquals(expectedSum, sumValueAfter)
                }
            }

            realm.query(QuerySample::class)
                .sum(propertyDescriptor.property.name, propertyDescriptor.clazz)
                .find { sumValue ->
                    if (sumValue is Number) {
                        assertEquals(expectedSum, sumValue)
                    }
                }

            // Make sure to delete all objects after assertions as aggregators to clean state and
            // avoid "null vs 0" results when testing
            cleanUpBetweenProperties()
        }

        // Iterate over all properties - exclude RealmInstant
        for (propertyDescriptor in allPropertyDescriptorsForSum) {
            assertions(propertyDescriptor)
        }
    }

    @Test
    fun sum_find_throwsIfRealmInstant() {
        assertFailsWith<IllegalArgumentException> {
            realm.query(QuerySample::class)
                .sum(timestampDescriptor.property.name, timestampDescriptor.clazz)
                .find() // The sum is only evaluated after obtaining results!
        }

        assertFailsWith<IllegalArgumentException> {
            realm.query(QuerySample::class)
                .sum(nullableTimestampDescriptor.property.name, nullableTimestampDescriptor.clazz)
                .find() // The sum is only evaluated after obtaining results!
        }
    }

    @Test
    fun sum_find_throwsIfInvalidProperty() {
        // Sum of an invalid primitive
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .sum<Int>(QuerySample::stringField.name)
                .find()
        }

        // Sum of a non-numerical RealmList
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .sum<Int>(QuerySample::stringListField.name)
                .find()
        }

        // Sum of an object
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .sum<Int>(QuerySample::child.name)
                .find()
        }
    }

    @Test
    fun sum_find_shortOverflow() {
        val iterations = 10000
        val invalidShortSum = (Short.MAX_VALUE * iterations).toShort()
        val validShortSum = Short.MAX_VALUE * iterations

        realm.writeBlocking {
            for (i in 1..iterations) {
                copyToRealm(QuerySample().apply { shortField = Short.MAX_VALUE })
            }
        }

        // We get an inaccurate short sum if we want the result as a Short
        realm.query(QuerySample::class)
            .sum(QuerySample::shortField.name, Short::class)
            .find { sumValue ->
                assertNotNull(sumValue)
                assertEquals(invalidShortSum, sumValue)
            }

        // Now we get the expected sum if we specify the result to be an Int
        realm.query(QuerySample::class)
            .sum(QuerySample::shortField.name, Int::class)
            .find { sumValue ->
                assertNotNull(sumValue)
                assertEquals(validShortSum, sumValue.toInt())
            }
    }

    @Test
    fun sum_find_byteOverflow() {
        val iterations = 10000
        val invalidByteSum = (Byte.MAX_VALUE * iterations).toByte()
        val validByteSum = Byte.MAX_VALUE * iterations

        realm.writeBlocking {
            for (i in 1..iterations) {
                copyToRealm(QuerySample().apply { byteField = Byte.MAX_VALUE })
            }
        }

        // We get an inaccurate byte sum if we want the result as a Byte
        realm.query(QuerySample::class)
            .sum(QuerySample::byteField.name, Byte::class)
            .find { sumValue ->
                assertNotNull(sumValue)
                assertEquals(invalidByteSum, sumValue)
            }

        // Now we get the expected sum if we specify the result to be an Int
        realm.query(QuerySample::class)
            .sum(QuerySample::byteField.name, Int::class)
            .find { sumValue ->
                assertNotNull(sumValue)
                assertEquals(validByteSum, sumValue.toInt())
            }
    }

    @Test
    fun sum_find_throwsIfInvalidType() {
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .sum<String>(QuerySample::intField.name)
                .find()
        }
    }

    @Test
    @Ignore
    fun sum_asFlow() {
        // TODO
    }

    @Test
    @Ignore
    fun sum_asFlow_throwsIfInvalidType() {
        // TODO
    }

    @Test
    @Ignore
    fun sum_asFlow_throwsIfInvalidProperty() {
        // TODO
    }

    @Test
    @Ignore
    fun sum_asFlow_throwsIfRealmInstant() {
        // TODO
    }

    @Test
    fun max_find_emptyTable() {
        val assertions = { propertyDescriptor: PropertyDescriptor ->
            realm.query<QuerySample>()
                .max(propertyDescriptor.property.name, propertyDescriptor.clazz)
                .find { maxValue -> assertNull(maxValue) }
        }

        // Iterate over all properties
        for (propertyDescriptor in allPropertyDescriptors) {
            assertions(propertyDescriptor)
        }
    }

    @Test
    fun max_find_allNullValues() {
        val assertions = { propertyDescriptor: PropertyDescriptor ->
            realm.writeBlocking {
                // Queries inside a write transaction produce live results which means they can be
                // reused within the closure
                val maxQuery = query(QuerySample::class)
                    .max(propertyDescriptor.property.name, propertyDescriptor.clazz)

                assertNull(maxQuery.find())
                copyToRealm(getInstance(propertyDescriptor, QuerySample()))
                copyToRealm(getInstance(propertyDescriptor, QuerySample()))
                assertNull(maxQuery.find())
            }

            realm.query(QuerySample::class)
                .max(propertyDescriptor.property.name, propertyDescriptor.clazz)
                .find { maxValue -> assertNull(maxValue) }

            // Make sure to delete all objects after assertions as aggregators to clean state and
            // avoid "null vs 0" results when testing
            cleanUpBetweenProperties()
        }

        // Iterate only over nullable properties and insert only null values in said properties
        for (nullablePropertyDescriptor in nullablePropertyDescriptors) {
            assertions(nullablePropertyDescriptor)
        }
    }

    @Test
    fun max_find() {
        val assertions = { propertyDescriptor: PropertyDescriptor ->
            val expectedMax = expectedMax(propertyDescriptor.clazz)

            realm.writeBlocking {
                // Queries inside a write transaction produce live results which means they can be
                // reused within the closure
                val maxQuery = query(QuerySample::class)
                    .max(propertyDescriptor.property.name, propertyDescriptor.clazz)

                assertNull(maxQuery.find())
                copyToRealm(getInstance(propertyDescriptor, QuerySample(), 0))
                copyToRealm(getInstance(propertyDescriptor, QuerySample(), 1))
                assertEquals(expectedMax, maxQuery.find())
            }

            realm.query(QuerySample::class)
                .max(propertyDescriptor.property.name, propertyDescriptor.clazz)
                .find { maxValue -> assertEquals(expectedMax, maxValue) }

            // Make sure to delete all objects after assertions as aggregators to clean state and
            // avoid "null vs 0" results when testing
            cleanUpBetweenProperties()
        }

        // Iterate over all properties
        for (propertyDescriptor in allPropertyDescriptors) {
            assertions(propertyDescriptor)
        }
    }

    @Test
    fun max_find_throwsIfInvalidProperty() {
        // Max of an invalid primitive
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .max<Int>(QuerySample::stringField.name)
                .find()
        }

        // Max of a non-numerical RealmList
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .max<Int>(QuerySample::stringListField.name)
                .find()
        }

        // Max of an object
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .max<Int>(QuerySample::child.name)
                .find()
        }
    }

    @Test
    fun max_find_throwsIfInvalidType() {
        realm.writeBlocking {
            copyToRealm(QuerySample().apply { intField = 1 })
            copyToRealm(QuerySample().apply { intField = 2 })
        }

        // TODO this won't fail unless we have previously stored something, otherwise it returns
        //  null and nothing will trigger an exception!
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .max<String>(QuerySample::intField.name)
                .find()
        }
    }

    @Test
    @Ignore
    fun max_asFlow() {
        // TODO
    }

    @Test
    @Ignore
    fun max_asFlow_throwsIfInvalidType() {
        // TODO
    }

    @Test
    @Ignore
    fun max_asFlow_throwsIfInvalidProperty() {
        // TODO
    }

    @Test
    fun min_find_emptyTable() {
        val assertions = { propertyDescriptor: PropertyDescriptor ->
            realm.query<QuerySample>()
                .min(propertyDescriptor.property.name, propertyDescriptor.clazz)
                .find { minValue -> assertNull(minValue) }
        }

        // Iterate over all properties
        for (propertyDescriptor in allPropertyDescriptors) {
            assertions(propertyDescriptor)
        }
    }

    @Test
    fun min_find_allNullValues() {
        val assertions = { propertyDescriptor: PropertyDescriptor ->
            realm.writeBlocking {
                // Queries inside a write transaction produce live results which means they can be
                // reused within the closure
                val minQuery = query(QuerySample::class)
                    .min(propertyDescriptor.property.name, propertyDescriptor.clazz)

                assertNull(minQuery.find())
                copyToRealm(getInstance(propertyDescriptor, QuerySample()))
                copyToRealm(getInstance(propertyDescriptor, QuerySample()))
                assertNull(minQuery.find())
            }

            realm.query(QuerySample::class)
                .min(propertyDescriptor.property.name, propertyDescriptor.clazz)
                .find { minValue -> assertNull(minValue) }

            // Make sure to delete all objects after assertions as aggregators to clean state and
            // avoid "null vs 0" results when testing
            cleanUpBetweenProperties()
        }

        // Iterate only over nullable properties and insert only null values in said properties
        for (nullablePropertyDescriptor in nullablePropertyDescriptors) {
            assertions(nullablePropertyDescriptor)
        }
    }

    @Test
    fun min_find() {
        val assertions = { propertyDescriptor: PropertyDescriptor ->
            val expectedMin = expectedMin(propertyDescriptor.clazz)

            realm.writeBlocking {
                // Queries inside a write transaction produce live results which means they can be
                // reused within the closure
                val minQuery = query(QuerySample::class)
                    .min(propertyDescriptor.property.name, propertyDescriptor.clazz)

                assertNull(minQuery.find())
                copyToRealm(getInstance(propertyDescriptor, QuerySample(), 0))
                copyToRealm(getInstance(propertyDescriptor, QuerySample(), 1))
                assertEquals(expectedMin, minQuery.find())
            }

            realm.query(QuerySample::class)
                .min(propertyDescriptor.property.name, propertyDescriptor.clazz)
                .find { minValue -> assertEquals(expectedMin, minValue) }

            // Make sure to delete all objects after assertions as aggregators to clean state and
            // avoid "null vs 0" results when testing
            cleanUpBetweenProperties()
        }

        // Iterate over all properties
        for (propertyDescriptor in allPropertyDescriptors) {
            assertions(propertyDescriptor)
        }
    }

    @Test
    fun min_find_throwsIfInvalidProperty() {
        // Min of an invalid primitive
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .min<Int>(QuerySample::stringField.name)
                .find()
        }

        // Min of a non-numerical RealmList
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .min<Int>(QuerySample::stringListField.name)
                .find()
        }

        // Min of an object
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .min<Int>(QuerySample::child.name)
                .find()
        }
    }

    @Test
    fun min_find_throwsIfInvalidType() {
        realm.writeBlocking {
            copyToRealm(QuerySample().apply { intField = 1 })
            copyToRealm(QuerySample().apply { intField = 2 })
        }

        // TODO this won't fail unless we have previously stored something, otherwise it returns
        //  null and nothing will trigger an exception!
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .min<String>(QuerySample::intField.name)
                .find()
        }
    }

    @Test
    @Ignore
    fun min_asFlow() {
        // TODO
    }

    @Test
    @Ignore
    fun min_asFlow_throwsIfInvalidType() {
        // TODO
    }

    @Test
    @Ignore
    fun min_asFlow_throwsIfInvalidProperty() {
        // TODO
    }

    @Test
    @Ignore
    fun first_find() {
        // TODO
    }

    @Test
    @Ignore
    fun first_find_emptyTable() {
        // TODO
    }

    @Test
    @Ignore
    fun first_asFlow() {
        // TODO
    }

    @Test
    // TODO remove this
    fun playground_multiThreadScenario() {
        val channel = Channel<RealmResults<QuerySample>>(1)
        var query: RealmQuery<QuerySample>? = null
        val scope = singleThreadDispatcher("1")
        val intValue = 666

        runBlocking {
            realm.writeBlocking {
                copyToRealm(QuerySample(intField = intValue))
            }

            // Create a query - the query itself is parsed but nothing else is done
            query = realm.query(QuerySample::class, "intField == $0", intValue)

            val obs = async(scope) {
                // Having built the query before, we reuse the query object from a different thread
                // so that the results pointer is evaluated now
                val results = query!!.find()
                channel.send(results)
            }
            val results = channel.receive()
            assertEquals(1, results.size)

            query!!.find { res ->
                assertEquals(1, res.size)
            }

            channel.close()
            obs.cancel()
        }
    }

    // --------------------------------------------------
    // Class instantiation with property setting helpers
    // --------------------------------------------------

    /**
     * Creates a [QuerySample] object and sets a value in the property specified by
     * [PropertyDescriptor.property]. The [index] parameter specifies which value from the dataset
     * used to populate the realm instance should be used.
     */
    private fun <C> getInstance(
        propertyDescriptor: PropertyDescriptor,
        instance: C,
        index: Int? = null
    ): C = with(propertyDescriptor) {
        when (property.returnType.classifier as KClass<*>) {
            Int::class -> setProperty(
                instance,
                property as KMutableProperty1<C, Int?>,
                values as List<Int?>,
                index
            )
            Long::class -> setProperty(
                instance,
                property as KMutableProperty1<C, Long?>,
                values as List<Long?>,
                index
            )
            Short::class -> setProperty(
                instance,
                property as KMutableProperty1<C, Short?>,
                values as List<Short?>,
                index
            )
            Double::class -> setProperty(
                instance,
                property as KMutableProperty1<C, Double?>,
                values as List<Double?>,
                index
            )
            Float::class -> setProperty(
                instance,
                property as KMutableProperty1<C, Float?>,
                values as List<Float?>,
                index
            )
            Byte::class -> setProperty(
                instance,
                property as KMutableProperty1<C, Byte?>,
                values as List<Byte?>,
                index
            )
            Char::class -> setProperty(
                instance,
                property as KMutableProperty1<C, Char?>,
                values as List<Char?>,
                index
            )
            RealmInstant::class -> setProperty(
                instance,
                property as KMutableProperty1<C, RealmInstant?>,
                values as List<RealmInstant?>,
                index
            )
            else -> throw IllegalArgumentException("Only numerical properties and timestamps are allowed.")
        }
    }

    private fun <C, T> setProperty(
        instance: C,
        property: KMutableProperty1<C, T?>,
        data: List<T?>,
        index: Int? = null
    ): C = instance.apply {
        when (index) {
            null -> property.set(instance, null)
            else -> property.set(instance, data[index])
        }
    }

    // -------------------------------------------------
    // Aggregator helpers used to initialize structures
    // -------------------------------------------------

    private fun expectedSum(clazz: KClass<*>): Any =
        expectedAggregator(clazz, AggregatorQueryType.SUM)

    private fun expectedMax(clazz: KClass<*>): Any =
        expectedAggregator(clazz, AggregatorQueryType.MAX)

    private fun expectedMin(clazz: KClass<*>): Any =
        expectedAggregator(clazz, AggregatorQueryType.MIN)

    /**
     * Computes the corresponding aggregator [type] for a specific [clazz]. Null values are never
     * computed when calculating aggregators, thus te use of "X_VALUES" which are all non-nullable.
     *
     * Calling the aggregator will never return null here, so force non-nullability with "!!" since
     * Kotlin's aggregators return null in case the given collection is empty
     */
    @Suppress("LongMethod", "ComplexMethod")
    private fun expectedAggregator(clazz: KClass<*>, type: AggregatorQueryType): Any =
        when (clazz) {
            Int::class -> when (type) {
                AggregatorQueryType.MIN -> INT_VALUES.minOrNull()!!
                AggregatorQueryType.MAX -> INT_VALUES.maxOrNull()!!
                AggregatorQueryType.SUM -> INT_VALUES.sum()
            }
            Long::class -> when (type) {
                AggregatorQueryType.MIN -> LONG_VALUES.minOrNull()!!
                AggregatorQueryType.MAX -> LONG_VALUES.maxOrNull()!!
                AggregatorQueryType.SUM -> LONG_VALUES.sum()
            }
            Short::class -> when (type) {
                AggregatorQueryType.MIN -> SHORT_VALUES.minOrNull()!!
                AggregatorQueryType.MAX -> SHORT_VALUES.maxOrNull()!!
                AggregatorQueryType.SUM -> SHORT_VALUES.sum()
                    .toShort() // Typecast manually or the sum will be an Int
            }
            Double::class -> when (type) {
                AggregatorQueryType.MIN -> DOUBLE_VALUES.minOrNull()!!
                AggregatorQueryType.MAX -> DOUBLE_VALUES.maxOrNull()!!
                AggregatorQueryType.SUM -> DOUBLE_VALUES.sum()
            }
            Float::class -> when (type) {
                AggregatorQueryType.MIN -> FLOAT_VALUES.minOrNull()!!
                AggregatorQueryType.MAX -> FLOAT_VALUES.maxOrNull()!!
                AggregatorQueryType.SUM -> FLOAT_VALUES.sum()
            }
            Byte::class -> when (type) {
                AggregatorQueryType.MIN -> BYTE_VALUES.minOrNull()!!
                AggregatorQueryType.MAX -> BYTE_VALUES.maxOrNull()!!
                AggregatorQueryType.SUM -> BYTE_VALUES.sum()
                    .toByte() // Typecast manually or the sum will be an Int
            }
            Char::class -> when (type) {
                AggregatorQueryType.MIN -> CHAR_VALUES.minOrNull()!!
                AggregatorQueryType.MAX -> CHAR_VALUES.maxOrNull()!!
                AggregatorQueryType.SUM -> CHAR_VALUES.sumOf { it.code }
            }
            RealmInstant::class -> when (type) {
                AggregatorQueryType.MIN -> TIMESTAMP_VALUES.minOrNull()!!
                AggregatorQueryType.MAX -> TIMESTAMP_VALUES.maxOrNull()!!
                AggregatorQueryType.SUM -> throw IllegalArgumentException("SUM is not allowed on timestamp fields.")
            }
            else -> throw IllegalArgumentException("Only numerical properties and timestamps are allowed.")
        }

    // ------------------------
    // General-purpose helpers
    // ------------------------

    // Deletes all objects after assertions to avoid "null vs 0" results when testing aggregators
    private fun cleanUpBetweenProperties() = realm.writeBlocking {
        query(QuerySample::class)
            .find { results -> results.delete() }
    }

    // ----------------------------------------
    // Descriptors used for exhaustive testing
    // ----------------------------------------

    private val basePropertyDescriptors = listOf(
        PropertyDescriptor(QuerySample::intField, Int::class, INT_VALUES),
        PropertyDescriptor(QuerySample::shortField, Short::class, SHORT_VALUES),
        PropertyDescriptor(QuerySample::longField, Long::class, LONG_VALUES),
        PropertyDescriptor(QuerySample::floatField, Float::class, FLOAT_VALUES),
        PropertyDescriptor(QuerySample::doubleField, Double::class, DOUBLE_VALUES),
        PropertyDescriptor(QuerySample::byteField, Byte::class, BYTE_VALUES),
        PropertyDescriptor(QuerySample::charField, Char::class, CHAR_VALUES)
    )

    private val timestampDescriptor = PropertyDescriptor(
        QuerySample::timestampField,
        RealmInstant::class,
        TIMESTAMP_VALUES
    )

    private val propertyDescriptors = basePropertyDescriptors + timestampDescriptor

    private val propertyDescriptorsForSum = basePropertyDescriptors

    private val nullableBasePropertyDescriptors = listOf(
        PropertyDescriptor(QuerySample::nullableIntField, Int::class, INT_VALUES),
        PropertyDescriptor(QuerySample::nullableShortField, Short::class, SHORT_VALUES),
        PropertyDescriptor(QuerySample::nullableLongField, Long::class, LONG_VALUES),
        PropertyDescriptor(QuerySample::nullableFloatField, Float::class, FLOAT_VALUES),
        PropertyDescriptor(QuerySample::nullableDoubleField, Double::class, DOUBLE_VALUES),
        PropertyDescriptor(QuerySample::nullableByteField, Byte::class, BYTE_VALUES),
        PropertyDescriptor(QuerySample::nullableCharField, Char::class, CHAR_VALUES)
    )

    private val nullableTimestampDescriptor = PropertyDescriptor(
        QuerySample::nullableTimestampField,
        RealmInstant::class,
        TIMESTAMP_VALUES
    )

    private val nullablePropertyDescriptors =
        nullableBasePropertyDescriptors + nullableTimestampDescriptor

    private val nullablePropertyDescriptorsForSum = nullableBasePropertyDescriptors

    private val allPropertyDescriptorsForSum =
        propertyDescriptorsForSum + nullablePropertyDescriptorsForSum

    private val allPropertyDescriptors = propertyDescriptors + nullablePropertyDescriptors

    // TODO figure out how to test aggregators on RealmList<Number> fields
//    // -----------------------
//    // List descriptors
//    // -----------------------
//
//    // Base
//    private val baseListPropertyDescriptors = listOf(
//        PropertyDescriptor(
//            QuerySample::intListField,
//            Int::class,
//            INT_VALUES,
//            CollectionType.RLM_COLLECTION_TYPE_LIST
//        ),
//        PropertyDescriptor(
//            QuerySample::shortListField,
//            Short::class,
//            SHORT_VALUES,
//            CollectionType.RLM_COLLECTION_TYPE_LIST
//        ),
//        PropertyDescriptor(
//            QuerySample::longListField,
//            Long::class,
//            LONG_VALUES,
//            CollectionType.RLM_COLLECTION_TYPE_LIST
//        ),
//        PropertyDescriptor(
//            QuerySample::floatListField,
//            Float::class,
//            FLOAT_VALUES,
//            CollectionType.RLM_COLLECTION_TYPE_LIST
//        ),
//        PropertyDescriptor(
//            QuerySample::doubleListField,
//            Double::class,
//            DOUBLE_VALUES,
//            CollectionType.RLM_COLLECTION_TYPE_LIST
//        ),
//        PropertyDescriptor(
//            QuerySample::byteListField,
//            Double::class,
//            BYTE_VALUES,
//            CollectionType.RLM_COLLECTION_TYPE_LIST
//        ),
//        PropertyDescriptor(
//            QuerySample::charListField,
//            Double::class,
//            CHAR_VALUES,
//            CollectionType.RLM_COLLECTION_TYPE_LIST
//        )
//    )
//
//    // Timestamp
//    private val timestampListPropertyDescriptor = PropertyDescriptor(
//        QuerySample::timestampListField,
//        RealmInstant::class,
//        TIMESTAMP_VALUES,
//        CollectionType.RLM_COLLECTION_TYPE_LIST
//    )
//
//    // Base + timestamp
//    private val listPropertyDescriptors = baseListPropertyDescriptors +
//        timestampListPropertyDescriptor
//
//    // Nullable
//    private val nullableBaseListPropertyDescriptors = listOf(
//        PropertyDescriptor(
//            QuerySample::nullableIntListField,
//            Int::class,
//            INT_VALUES,
//            CollectionType.RLM_COLLECTION_TYPE_LIST
//        ),
//        PropertyDescriptor(
//            QuerySample::nullableShortListField,
//            Short::class,
//            SHORT_VALUES,
//            CollectionType.RLM_COLLECTION_TYPE_LIST
//        ),
//        PropertyDescriptor(
//            QuerySample::nullableLongListField,
//            Long::class,
//            LONG_VALUES,
//            CollectionType.RLM_COLLECTION_TYPE_LIST
//        ),
//        PropertyDescriptor(
//            QuerySample::nullableFloatListField,
//            Float::class,
//            FLOAT_VALUES,
//            CollectionType.RLM_COLLECTION_TYPE_LIST
//        ),
//        PropertyDescriptor(
//            QuerySample::nullableDoubleListField,
//            Double::class,
//            DOUBLE_VALUES,
//            CollectionType.RLM_COLLECTION_TYPE_LIST
//        ),
//        PropertyDescriptor(
//            QuerySample::nullableByteListField,
//            Double::class,
//            BYTE_VALUES,
//            CollectionType.RLM_COLLECTION_TYPE_LIST
//        ),
//        PropertyDescriptor(
//            QuerySample::nullableCharListField,
//            Double::class,
//            CHAR_VALUES,
//            CollectionType.RLM_COLLECTION_TYPE_LIST
//        )
//    )
//
//    // Nullable timestamp
//    private val nullableTimestampListPropertyDescriptor = PropertyDescriptor(
//        QuerySample::nullableTimestampListField,
//        RealmInstant::class,
//        TIMESTAMP_VALUES,
//        CollectionType.RLM_COLLECTION_TYPE_LIST
//    )
//
//    // Nullable base + nullable timestamp
//    private val nullableListPropertyDescriptors = nullableBaseListPropertyDescriptors +
//        nullableTimestampListPropertyDescriptor
}

/**
 * Metadata container used for exhaustive testing.
 *
 * @param property the field to be used in a particular assertion
 * @param clazz field type
 * @param values individual values to be stored in [property]
 */
private data class PropertyDescriptor(
    val property: KMutableProperty1<QuerySample, *>,
    val clazz: KClass<*>,
    val values: List<Any?>,
    val collectionType: CollectionType = CollectionType.RLM_COLLECTION_TYPE_NONE
)

/**
 * Use this and not [io.realm.entities.Sample] as that class has default initializers that make
 * aggregating operations harder to assert.
 */
class QuerySample() : RealmObject {

    constructor(intField: Int) : this() {
        this.intField = intField
    }

    constructor(stringField: String) : this() {
        this.stringField = stringField
    }

    constructor(intField: Int, longField: Long) : this() {
        this.intField = intField
        this.longField = longField
    }

    constructor(intField: Int, stringField: String) : this() {
        this.intField = intField
        this.stringField = stringField
    }

    var stringField: String = "Realm"
    var byteField: Byte = 0
    var charField: Char = 'a'
    var shortField: Short = 0
    var intField: Int = 0
    var longField: Long = 0
    var booleanField: Boolean = true
    var floatField: Float = 0F
    var doubleField: Double = 0.0
    var timestampField: RealmInstant = RealmInstant.fromEpochSeconds(100, 1000)

    var nullableStringField: String? = null
    var nullableByteField: Byte? = null
    var nullableCharField: Char? = null
    var nullableShortField: Short? = null
    var nullableIntField: Int? = null
    var nullableLongField: Long? = null
    var nullableBooleanField: Boolean? = null
    var nullableFloatField: Float? = null
    var nullableDoubleField: Double? = null
    var nullableTimestampField: RealmInstant? = null

    var stringListField: RealmList<String> = realmListOf()
    var byteListField: RealmList<Byte> = realmListOf()
    var charListField: RealmList<Char> = realmListOf()
    var shortListField: RealmList<Short> = realmListOf()
    var intListField: RealmList<Int> = realmListOf()
    var longListField: RealmList<Long> = realmListOf()
    var booleanListField: RealmList<Boolean> = realmListOf()
    var floatListField: RealmList<Float> = realmListOf()
    var doubleListField: RealmList<Double> = realmListOf()
    var timestampListField: RealmList<RealmInstant> = realmListOf()
    var objectListField: RealmList<QuerySample> = realmListOf()

    var nullableStringListField: RealmList<String?> = realmListOf()
    var nullableByteListField: RealmList<Byte?> = realmListOf()
    var nullableCharListField: RealmList<Char?> = realmListOf()
    var nullableShortListField: RealmList<Short?> = realmListOf()
    var nullableIntListField: RealmList<Int?> = realmListOf()
    var nullableLongListField: RealmList<Long?> = realmListOf()
    var nullableBooleanListField: RealmList<Boolean?> = realmListOf()
    var nullableFloatListField: RealmList<Float?> = realmListOf()
    var nullableDoubleListField: RealmList<Double?> = realmListOf()
    var nullableTimestampListField: RealmList<RealmInstant?> = realmListOf()

    var child: QuerySample? = null
}
