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

package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.internal.ObjectIdImpl
import io.realm.kotlin.internal.platform.singleThreadDispatcher
import io.realm.kotlin.internal.query.AggregatorQueryType
import io.realm.kotlin.notifications.DeletedObject
import io.realm.kotlin.notifications.InitialObject
import io.realm.kotlin.notifications.InitialResults
import io.realm.kotlin.notifications.PendingObject
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.notifications.UpdatedObject
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import io.realm.kotlin.query.find
import io.realm.kotlin.query.max
import io.realm.kotlin.query.min
import io.realm.kotlin.query.sum
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.mongodb.kbson.BsonObjectId
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@Suppress("LargeClass")
class QueryTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(QuerySample::class))
            .directory(tmpDir)
            .build()
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
    fun query_missingArgumentThrows() {
        assertFailsWithMessage<IllegalArgumentException>("Request for argument at index 0 but no arguments are provided") {
            realm.query<QuerySample>("stringField = $0")
        }
    }

    @Test
    fun query_wrongArgumentTypeThrows() {
        assertFailsWithMessage<IllegalArgumentException>(" Unsupported comparison between type 'string' and type 'bool'") {
            realm.query<QuerySample>("stringField = $0", true)
        }
    }

    @Test
    fun description() {
        val desc = realm.query<QuerySample>("stringField = $0", "search-term").description()
        assertEquals("stringField == \"search-term\"", desc)
    }

    // Ensure that parameter types are carried on into RQL and not converted,
    // e.g. `true` Boolean is not turned into `"true"` (String) or `1` (Integer).
    @Test
    @Suppress("LongMethod", "ComplexMethod")
    fun query_typesAreConvertedCorrectly() {
        realm.writeBlocking {
            val objectWithDefaults = copyToRealm(QuerySample().apply { stringField = "DEFAULTS" })
            copyToRealm(
                QuerySample().apply {
                    stringField = "NONDEFAULTS"
                    byteField = 1
                    charField = 'b'
                    shortField = 1
                    intField = 1
                    longField = 1
                    booleanField = false
                    floatField = 1F
                    doubleField = 1.0
                    timestampField = RealmInstant.from(100, 1001)
                    objectIdField = ObjectId.from("507f191e810c19729de860eb")
                    bsonObjectIdField = BsonObjectId("507f191e810c19729de860eb")
                    uuidField = RealmUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d77")
                    binaryField = byteArrayOf(43)
                    realmAnyField = RealmAny.create(43)
                    nullableRealmObject = objectWithDefaults
                }
            )
        }
        assertEquals(2, realm.query<QuerySample>().find().size)

        fun <T> checkQuery(property: KMutableProperty1<QuerySample, T>, value: T) {
            realm.query<QuerySample>("${property.name} = $0", value)
                .find()
                .single()
                .run {
                    assertEquals(value, property.getValue(this, property))
                }
        }

        for (type: RealmStorageType in RealmStorageType.values()) {
            when (type) {
                RealmStorageType.BOOL -> {
                    checkQuery(QuerySample::booleanField, false)
                }
                RealmStorageType.INT -> {
                    checkQuery(QuerySample::byteField, 1.toByte())
                    checkQuery(QuerySample::charField, 'b')
                    checkQuery(QuerySample::shortField, 1)
                    checkQuery(QuerySample::intField, 1)
                    checkQuery(QuerySample::longField, 1)
                }
                RealmStorageType.STRING -> {
                    checkQuery(QuerySample::stringField, "NONDEFAULTS")
                }
                RealmStorageType.OBJECT -> {
                    val child = realm.query<QuerySample>("stringField = 'DEFAULTS'").find().first()
                    assertEquals(
                        "NONDEFAULTS",
                        realm.query<QuerySample>("nullableRealmObject = $0", child).find()
                            .single().stringField
                    )
                }
                RealmStorageType.FLOAT -> {
                    checkQuery(QuerySample::floatField, 1f)
                }
                RealmStorageType.DOUBLE -> {
                    checkQuery(QuerySample::doubleField, 1.0)
                }
                RealmStorageType.TIMESTAMP -> {
                    checkQuery(QuerySample::timestampField, RealmInstant.from(100, 1001))
                }
                RealmStorageType.OBJECT_ID -> {
                    val realmObjectId = ObjectId.from("507f191e810c19729de860eb")
                    val bsonObjectId = BsonObjectId("507f191e810c19729de860eb")

                    // Check matching types first
                    checkQuery(QuerySample::objectIdField, realmObjectId)
                    checkQuery(QuerySample::bsonObjectIdField, bsonObjectId)

                    // Check against equivalent types now - do it manually since the convenience
                    // function forces the fields to have the same type as the query parameter
                    realm.query<QuerySample>("bsonObjectIdField = $0", realmObjectId)
                        .find()
                        .single()
                        .run {
                            assertContentEquals(
                                (realmObjectId as ObjectIdImpl).bytes,
                                this.bsonObjectIdField.toByteArray()
                            )
                        }

                    realm.query<QuerySample>("objectIdField = $0", bsonObjectId)
                        .find()
                        .single()
                        .run {
                            assertContentEquals(
                                bsonObjectId.toByteArray(),
                                (this.objectIdField as ObjectIdImpl).bytes
                            )
                        }
                }
                RealmStorageType.UUID -> {
                    checkQuery(
                        QuerySample::uuidField,
                        RealmUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d77")
                    )
                }
                RealmStorageType.BINARY -> {
                    val value = byteArrayOf(43)
                    realm.query<QuerySample>("binaryField = $0", value)
                        .find()
                        .single()
                        .run {
                            assertContentEquals(value, binaryField)
                        }
                }
                RealmStorageType.REALM_ANY -> {
                    checkQuery(QuerySample::realmAnyField, RealmAny.create((42)))
                }
                else -> fail("Unknown type: $type")
            }
        }
    }

    @Test
    fun find_realm() {
        realm.query<QuerySample>()
            .find { results -> assertEquals(0, results.size) }

        val stringValue = "some string"
        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val query = query<QuerySample>()

            assertEquals(0, query.find().size)
            copyToRealm(QuerySample(stringField = stringValue))
            assertEquals(1, query.find().size)
        }

        // No filter
        realm.query<QuerySample>()
            .find { results -> assertEquals(1, results.size) }

        // Filter by string
        realm.query<QuerySample>("stringField = $0", stringValue)
            .find { results -> assertEquals(1, results.size) }

        // Filter by string that doesn't match
        realm.query<QuerySample>("stringField = $0", "invalid string")
            .find { results -> assertEquals(0, results.size) }
    }

    @Test
    fun find_mutableRealm() {
        val stringValue = "some string"

        realm.writeBlocking {
            // Queries inside a write transaction are live and can be reused within the closure
            val query = query<QuerySample>()

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
            realm.query<QuerySample>("stringField = $0")
        }.let {
            assertTrue(it.message!!.contains("Have you specified all parameters"))
        }

        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>("stringField = 42")
        }.let {
            assertTrue(it.message!!.contains("Wrong query field"))
        }

        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>("nonExistingField = 13")
        }.let {
            assertTrue(it.message!!.contains("Wrong query field"))
        }
    }

    @Test
    fun find_mutableRealm_malformedQueryThrows() {
        realm.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                query<QuerySample>("stringField = $0")
            }.let {
                assertTrue(it.message!!.contains("Have you specified all parameters"))
            }

            assertFailsWith<IllegalArgumentException> {
                query<QuerySample>("stringField = 42")
            }.let {
                assertTrue(it.message!!.contains("Wrong query field"))
            }

            assertFailsWith<IllegalArgumentException> {
                query<QuerySample>("nonExistingField = 13")
            }.let {
                assertTrue(it.message!!.contains("Wrong query field"))
            }
        }
    }

    @Test
    fun asFlow_initialResults() {
        val channel = Channel<ResultsChange<QuerySample>>(1)

        runBlocking {
            val observer = async {
                realm.query<QuerySample>()
                    .asFlow()
                    .collect { results ->
                        assertNotNull(results)
                        channel.send(results)
                    }
            }

            channel.receive().let { resultsChange ->
                assertIs<InitialResults<*>>(resultsChange)
                assertTrue(resultsChange.list.isEmpty())
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun asFlow() {
        val channel = Channel<ResultsChange<QuerySample>>(1)

        runBlocking {
            val observer = async {
                realm.query<QuerySample>()
                    .asFlow()
                    .collect { results ->
                        assertNotNull(results)
                        channel.send(results)
                    }
            }

            channel.receive().let { resultsChange ->
                assertIs<InitialResults<*>>(resultsChange)
                assertTrue(resultsChange.list.isEmpty())
            }

            realm.writeBlocking {
                copyToRealm(QuerySample())
            }

            channel.receive().let { resultsChange ->
                assertIs<UpdatedResults<*>>(resultsChange)
                assertEquals(1, resultsChange.list.size)
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun asFlow_deleteObservable() {
        val channel = Channel<ResultsChange<QuerySample>>(1)

        runBlocking {
            realm.writeBlocking {
                copyToRealm(QuerySample())
            }

            val observer = async {
                realm.query<QuerySample>()
                    .asFlow()
                    .collect { results ->
                        assertNotNull(results)
                        channel.send(results)
                    }
            }

            channel.receive().let { resultsChange ->
                assertIs<InitialResults<*>>(resultsChange)
                assertEquals(1, resultsChange.list.size)
            }

            realm.writeBlocking {
                delete(query<QuerySample>())
            }

            channel.receive().let { resultsChange ->
                assertIs<UpdatedResults<*>>(resultsChange)
                assertTrue(resultsChange.list.isEmpty())
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun asFlow_throwsInsideWrite() {
        realm.writeBlocking {
            val query = query<QuerySample>()
            assertFailsWith<UnsupportedOperationException> {
                query.asFlow()
            }
        }
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
        realm.query<QuerySample>("intField > 1")
            .query("intField > 2")
            .sort(QuerySample::intField.name, Sort.DESCENDING)
            .find { results ->
                assertEquals(3, results.size)
                assertEquals(5, results[0].intField)
                assertEquals(4, results[1].intField)
                assertEquals(3, results[2].intField)
            }

        // Sylvia, Stacy
        realm.query<QuerySample>("intField > 1")
            .query("stringField BEGINSWITH[c] $0", "S")
            .sort(QuerySample::intField.name, Sort.ASCENDING)
            .find { results ->
                assertEquals(2, results.size)
                assertEquals(sylvia, results[0].stringField)
                assertEquals(stacy, results[1].stringField)
            }

        // Ruth
        realm.query<QuerySample>()
            .query("stringField BEGINSWITH[c] $0 OR stringField BEGINSWITH[c] $1", "J", "R")
            .query("intField > 1")
            .find { results ->
                assertEquals(1, results.size)
                assertEquals(ruth, results[0].stringField)
            }

        realm.query<QuerySample>()
            .sort(QuerySample::intField.name, Sort.ASCENDING)
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals(joe, first.stringField)
            }

        realm.query<QuerySample>()
            .sort(QuerySample::intField.name, Sort.DESCENDING)
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals(bob, first.stringField)
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
        realm.query<QuerySample>()
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
        realm.query<QuerySample>()
            .query("intField > 2")
            .sort(QuerySample::intField.name, Sort.DESCENDING)
            .limit(1)
            .find()
            .let { results ->
                assertEquals(1, results.size)
                assertEquals(value5, results[0].intField)
            }

        // Descriptor in query string - Bob, Ruth, Stacy
        realm.query<QuerySample>()
            .query("intField > 2 SORT(stringField ASCENDING)")
            .find()
            .let { results ->
                assertEquals(3, results.size)
                assertEquals(bob, results[0].stringField)
                assertEquals(ruth, results[1].stringField)
                assertEquals(stacy, results[2].stringField)
            }

        // Descriptor as a function - Bob, Ruth, Stacy
        realm.query<QuerySample>()
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
        realm.query<QuerySample>()
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
        realm.query<QuerySample>()
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
            realm.query<QuerySample>()
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
        realm.query<QuerySample>()
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
        realm.query<QuerySample>()
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
        realm.query<QuerySample>()
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

    // -------------------
    // Descriptors - sort
    // -------------------

    @Test
    fun sort_emptyTable() {
        realm.query<QuerySample>()
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
        realm.query<QuerySample>()
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
        realm.query<QuerySample>()
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
        realm.query<QuerySample>()
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
            realm.query<QuerySample>()
                .sort("invalid")
        }
    }

    // -----------------------
    // Descriptors - distinct
    // -----------------------

    @Test
    fun distinct() {
        val value1 = 1
        val value2 = 2
        realm.writeBlocking {
            copyToRealm(QuerySample(intField = value1))
            copyToRealm(QuerySample(intField = value1)) // repeated intentionally
            copyToRealm(QuerySample(intField = value2))
        }

        realm.query<QuerySample>()
            .distinct(QuerySample::intField.name)
            .find { results ->
                assertEquals(2, results.size)
            }

        realm.query<QuerySample>()
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

        realm.query<QuerySample>()
            .distinct(QuerySample::intField.name, QuerySample::longField.name)
            .find { results ->
                assertEquals(3, results.size)
            }
    }

    @Test
    fun distinct_throwsIfInvalidProperty() {
        val query = realm.query<QuerySample>()
        assertFailsWith<IllegalArgumentException> {
            query.distinct("invalid")
        }
    }

    // --------------------
    // Descriptors - limit
    // --------------------

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

        realm.query<QuerySample>()
            .sort(QuerySample::intField.name, Sort.DESCENDING)
            .limit(2)
            .find { results ->
                assertEquals(2, results.size)
                assertEquals(value3, results[0].intField)
            }

        realm.query<QuerySample>()
            .sort(QuerySample::intField.name, Sort.DESCENDING)
            .limit(2)
            .limit(1)
            .find { results ->
                assertEquals(1, results.size)
                assertEquals(value3, results[0].intField)
            }
    }

    @Test
    fun limit_throwsIfInvalidValue() {
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .limit(-42)
        }
    }

    // --------------------------------------------------------------------------------
    // TODO - Aggregators - average - https://github.com/realm/realm-kotlin/issues/646
    // --------------------------------------------------------------------------------

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

    // --------------------
    // Aggregators - count
    // --------------------

    @Test
    fun count_find_emptyTable() {
        realm.query<QuerySample>()
            .count()
            .find { countValue -> assertEquals(0, countValue) }
    }

    @Test
    fun count_find() {
        realm.writeBlocking {
            // Queries inside a write transaction produce live results which means they can be
            // reused within the closure
            val countQuery = query<QuerySample>().count()

            assertEquals(0, countQuery.find())
            copyToRealm(QuerySample())
            assertEquals(1, countQuery.find())
        }

        realm.query<QuerySample>()
            .count()
            .find { countValue -> assertEquals(1, countValue) }
    }

    @Test
    fun count_asFlow_initialValue() {
        val channel = Channel<Long>(1)

        runBlocking {
            val observer = async {
                realm.query<QuerySample>()
                    .count()
                    .asFlow()
                    .collect { countValue ->
                        assertNotNull(countValue)
                        channel.send(countValue)
                    }
            }

            assertEquals(0, channel.receive())

            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun count_asFlow() {
        val channel = Channel<Long>(1)

        runBlocking {
            val observer = async {
                realm.query<QuerySample>()
                    .count()
                    .asFlow()
                    .collect { countValue ->
                        assertNotNull(countValue)
                        channel.send(countValue)
                    }
            }

            assertEquals(0, channel.receive())

            realm.writeBlocking {
                copyToRealm(QuerySample())
            }

            assertEquals(1, channel.receive())
            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun count_asFlow_deleteObservable() {
        val channel = Channel<Long>(1)

        runBlocking {
            realm.write {
                copyToRealm(QuerySample())
            }

            val observer = async {
                realm.query<QuerySample>()
                    .count()
                    .asFlow()
                    .collect { countValue ->
                        assertNotNull(countValue)
                        channel.send(countValue)
                    }
            }

            assertEquals(1, channel.receive())

            realm.write {
                delete(query<QuerySample>())
            }

            assertEquals(0, channel.receive())

            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun count_asFlow_cancel() {
        runBlocking {
            val channel1 = Channel<Long?>(1)
            val channel2 = Channel<Long?>(1)

            val observer1 = async {
                realm.query<QuerySample>()
                    .count()
                    .asFlow()
                    .collect {
                        channel1.send(it)
                    }
            }
            val observer2 = async {
                realm.query<QuerySample>()
                    .count()
                    .asFlow()
                    .collect {
                        channel2.send(it)
                    }
            }

            assertEquals(0, channel1.receive())
            assertEquals(0, channel2.receive())

            // Write one object
            realm.write {
                copyToRealm(QuerySample().apply { stringField = "Bar" })
            }

            // Assert emission and cancel first subscription
            assertEquals(1, channel1.receive())
            assertEquals(1, channel2.receive())
            observer1.cancel()

            // Write another object
            realm.write {
                copyToRealm(QuerySample().apply { stringField = "Baz" })
            }

            // Assert emission and that the original channel hasn't been received
            assertEquals(2, channel2.receive())
            assertTrue(channel1.isEmpty)

            observer2.cancel()
            channel1.close()
            channel2.close()
        }
    }

    @Test
    fun count_asFlow_throwsInsideWrite() {
        realm.writeBlocking {
            // Check we throw when observing flows inside write transactions
            runBlocking {
                assertFailsWith<UnsupportedOperationException> {
                    query<QuerySample>()
                        .count()
                        .asFlow()
                }
            }
        }
    }

    // ------------------
    // Aggregators - sum
    // ------------------

    @Test
    fun sum_find_emptyTable() {
        val assertions = { propertyDescriptor: PropertyDescriptor ->
            realm.query<QuerySample>()
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
                val sumQuery = query<QuerySample>()
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

            realm.query<QuerySample>()
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
                val sumQuery = query<QuerySample>()
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

            realm.query<QuerySample>()
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
            realm.query<QuerySample>()
                .sum(timestampDescriptor.property.name, timestampDescriptor.clazz)
                .find() // The sum is only evaluated after obtaining results!
        }

        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
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
        val iterations = 10
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
        val iterations = 10
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
    fun sum_asFlow() {
        for (propertyDescriptor in allPropertyDescriptorsForSum) {
            asFlowAggregatorAssertions(AggregatorQueryType.SUM, propertyDescriptor)
            asFlowDeleteObservableAssertions(AggregatorQueryType.SUM, propertyDescriptor)
            asFlowCancel(AggregatorQueryType.SUM, propertyDescriptor)
        }
    }

    @Test
    fun sum_asFlow_throwsIfInvalidType() {
        val value1 = 2
        val value2 = 7

        realm.writeBlocking {
            copyToRealm(QuerySample(intField = value1))
            copyToRealm(QuerySample(intField = value2))
        }

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query<QuerySample>()
                    .sum<String>(QuerySample::intField.name)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid property type"))
            }
        }
    }

    @Test
    fun sum_asFlow_throwsIfInvalidProperty() {
        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query<QuerySample>()
                    .sum<String>(QuerySample::stringField.name)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid query formulation"))
            }
        }
    }

    @Test
    fun sum_asFlow_throwsInsideWrite() {
        realm.writeBlocking {
            val sumQuery = query<QuerySample>()
                .sum<Int>(QuerySample::intField.name)
            assertFailsWith<UnsupportedOperationException> {
                sumQuery.asFlow()
            }
        }
    }

    // ------------------
    // Aggregators - max
    // ------------------

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
            copyToRealm(QuerySample(intField = 1))
            copyToRealm(QuerySample(intField = 2))
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
    fun max_asFlow() {
        for (propertyDescriptor in allPropertyDescriptors) {
            asFlowAggregatorAssertions(AggregatorQueryType.MAX, propertyDescriptor)
            asFlowDeleteObservableAssertions(AggregatorQueryType.MAX, propertyDescriptor)
            asFlowCancel(AggregatorQueryType.MAX, propertyDescriptor)
        }
    }

    @Test
    fun max_asFlow_throwsIfInvalidType() {
        val value1 = 2
        val value2 = 7

        realm.writeBlocking {
            copyToRealm(QuerySample(intField = value1))
            copyToRealm(QuerySample(intField = value2))
        }

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query<QuerySample>()
                    .max<String>(QuerySample::intField.name)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid property type"))
            }
        }
    }

    @Test
    fun max_asFlow_throwsIfInvalidProperty() {
        val value1 = 2
        val value2 = 7

        realm.writeBlocking {
            copyToRealm(QuerySample(intField = value1))
            copyToRealm(QuerySample(intField = value2))
        }

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query<QuerySample>()
                    .max<String>(QuerySample::stringField.name)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid query formulation"))
            }
        }
    }

    @Test
    fun max_asFlow_throwsInsideWrite() {
        realm.writeBlocking {
            assertFailsWith<UnsupportedOperationException> {
                query<QuerySample>()
                    .max<Int>(QuerySample::intField.name)
                    .asFlow()
            }
        }
    }

    // ------------------
    // Aggregators - min
    // ------------------

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
    fun min_asFlow() {
        for (propertyDescriptor in allPropertyDescriptors) {
            asFlowAggregatorAssertions(AggregatorQueryType.MIN, propertyDescriptor)
            asFlowDeleteObservableAssertions(AggregatorQueryType.MIN, propertyDescriptor)
            asFlowCancel(AggregatorQueryType.MIN, propertyDescriptor)
        }
    }

    @Test
    fun min_asFlow_throwsIfInvalidType() {
        val value1 = 2
        val value2 = 7

        realm.writeBlocking {
            copyToRealm(QuerySample(intField = value1))
            copyToRealm(QuerySample(intField = value2))
        }

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query<QuerySample>()
                    .min<String>(QuerySample::intField.name)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid property type"))
            }
        }
    }

    @Test
    fun min_asFlow_throwsIfInvalidProperty() {
        val value1 = 2
        val value2 = 7

        realm.writeBlocking {
            copyToRealm(QuerySample(intField = value1))
            copyToRealm(QuerySample(intField = value2))
        }

        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                realm.query<QuerySample>()
                    .min<String>(QuerySample::stringField.name)
                    .asFlow()
                    .collect { /* No-op */ }
            }.let {
                assertTrue(it.message!!.contains("Invalid query formulation"))
            }
        }
    }

    @Test
    fun min_asFlow_throwsInsideWrite() {
        realm.writeBlocking {
            assertFailsWith<UnsupportedOperationException> {
                query<QuerySample>()
                    .min<Int>(QuerySample::intField.name)
                    .asFlow()
            }
        }
    }

    // --------------
    // First element
    // --------------

    @Test
    fun first_find_emptyTable() {
        realm.query<QuerySample>()
            .first()
            .find { first -> assertNull(first) }
    }

    @Test
    fun first_find() {
        val value1 = 1
        val value2 = 2

        realm.writeBlocking {
            // Queries inside a write transaction produce live results which means they can be
            // reused within the closure
            val firstQuery = query<QuerySample>("intField > $0", value1)
                .first()

            assertNull(firstQuery.find())
            copyToRealm(QuerySample(intField = value1))
            copyToRealm(QuerySample(intField = value2))
            val first = firstQuery.find()
            assertNotNull(first)
            assertEquals(value2, first.intField)
        }

        realm.query<QuerySample>("intField > $0", value1)
            .first()
            .find { first ->
                assertNotNull(first)
                assertEquals(value2, first.intField)
            }
    }

    @Test
    @Suppress("LongMethod")
    fun first_asFlow() {
        val channel = Channel<SingleQueryChange<QuerySample>>(2)

        val dataset = arrayOf(
            QuerySample(intField = 1),
            QuerySample(intField = 2),
            QuerySample(intField = 3),
            QuerySample(intField = 4),
            QuerySample(intField = 5)
        )

        runBlocking {
            val observer = async {
                realm.query<QuerySample>()
                    .sort(QuerySample::intField.name, Sort.DESCENDING)
                    .first()
                    .asFlow()
                    .collect { first ->
                        channel.send(first)
                    }
            }

            channel.receive().let { objectChange ->
                assertTrue(channel.isEmpty) // Validates that this is the first event and only event
                assertIs<PendingObject<QuerySample>>(objectChange)
            }

            // Insert initial data set
            // [5, 4, 3, 2, 1]
            realm.writeBlocking {
                dataset.forEach { querySample ->
                    copyToRealm(querySample)
                }
            }

            channel.receive().let { objectChange ->
                assertTrue(channel.isEmpty) // Validates that this is the first event and only event

                assertIs<InitialObject<QuerySample>>(objectChange)
                assertEquals(5, objectChange.obj.intField)
            }

            // Update the head element from value 5 to 6
            // [6, 4, 3, 2, 1]
            realm.writeBlocking {
                query<QuerySample>("intField = $0", 5).first().find { querySample ->
                    querySample!!.intField = 6
                }
            }

            channel.receive().let { objectChange ->
                assertIs<UpdatedObject<QuerySample>>(objectChange)
                assertEquals(6, objectChange.obj.intField)
            }

            // Update the head element 6 to value 7
            // [7, 4, 3, 2, 1]
            realm.writeBlocking {
                query<QuerySample>("intField = $0", 6).first().find { querySample ->
                    querySample!!.intField = 7
                }
            }

            channel.receive().let { objectChange ->
                assertIs<UpdatedObject<QuerySample>>(objectChange)
                assertEquals(7, objectChange.obj.intField)
            }

            // Delete the head element 6
            // [4, 3, 2, 1]
            realm.writeBlocking {
                delete(query<QuerySample>("intField = $0", 7).first().find()!!)
            }

            assertIs<DeletedObject<QuerySample>>(channel.receive())

            channel.receive().let { objectChange ->
                assertIs<InitialObject<QuerySample>>(objectChange)
                assertEquals(4, objectChange.obj.intField)
            }

            // Replace the head value with the second one
            // [<7>, 4, 2, 1]
            realm.writeBlocking {
                query<QuerySample>("intField = $0", 3).first().find { querySample ->
                    querySample!!.intField = 7
                }
            }

            channel.receive().let { objectChange ->
                assertIs<InitialObject<QuerySample>>(objectChange)
                assertEquals(7, objectChange.obj.intField)
            }

            // Delete head element and update the new head
            // [10, 2, 1]
            realm.writeBlocking {
                delete(query<QuerySample>("intField = $0", 7))
                query<QuerySample>("intField = $0", 4).first().find { querySample ->
                    querySample!!.intField = 10
                }
            }

            assertIs<DeletedObject<QuerySample>>(channel.receive())

            channel.receive().let { objectChange ->
                assertIs<InitialObject<QuerySample>>(objectChange)
                assertEquals(10, objectChange.obj.intField)
            }

            // Empty the list
            // []
            realm.writeBlocking {
                delete(query<QuerySample>())
            }

            assertIs<DeletedObject<QuerySample>>(channel.receive())

            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun first_asFlow_cancel() {
        runBlocking {
            val channel1 = Channel<SingleQueryChange<QuerySample>>(2)
            val channel2 = Channel<SingleQueryChange<QuerySample>>(2)

            val observer1 = async {
                realm.query<QuerySample>()
                    .first()
                    .asFlow()
                    .collect {
                        channel1.send(it)
                    }
            }
            val observer2 = async {
                realm.query<QuerySample>()
                    .first()
                    .asFlow()
                    .collect {
                        channel2.send(it)
                    }
            }

            assertIs<PendingObject<*>>(channel1.receive())
            assertIs<PendingObject<*>>(channel2.receive())

            // Write one object
            realm.write {
                copyToRealm(QuerySample().apply { stringField = "Bar" })
            }

            // Assert emission and cancel first subscription
            assertIs<InitialObject<*>>(channel1.receive())
            assertIs<InitialObject<*>>(channel2.receive())
            observer1.cancel()

            // Update object
            realm.write {
                query<QuerySample>("stringField = $0", "Bar").first().find {
                    it!!.stringField = "Baz"
                }
            }

            // Assert emission and that the original channel hasn't been received
            assertIs<UpdatedObject<*>>(channel2.receive())
            assertTrue(channel1.isEmpty)

            observer2.cancel()
            channel1.close()
            channel2.close()
        }
    }

    // -------------------------------------------------------
    // TODO - add tests for queries on collections when ready
    // -------------------------------------------------------

    // ----------------------------------
    // Multithreading with query objects
    // ----------------------------------

    @Test
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
        expectedAggregate(clazz, AggregatorQueryType.SUM)

    private fun expectedMax(clazz: KClass<*>): Any =
        expectedAggregate(clazz, AggregatorQueryType.MAX)

    private fun expectedMin(clazz: KClass<*>): Any =
        expectedAggregate(clazz, AggregatorQueryType.MIN)

    /**
     * Computes the corresponding aggregator [type] for a specific [clazz]. Null values are never
     * computed when calculating aggregators, thus te use of "X_VALUES" which are all non-nullable.
     *
     * Calling the aggregator will never return null here, so force non-nullability with "!!" since
     * Kotlin's aggregators return null in case the given collection is empty
     */
    @Suppress("LongMethod", "ComplexMethod")
    private fun expectedAggregate(clazz: KClass<*>, type: AggregatorQueryType): Any =
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

    /**
     * Asserts calls to `asFlow` for all aggregate operations. The assertions follow this pattern:
     * - subscribe to the flow and receive initial aggregated value (0 or null)
     * - update the realm
     * - receive emission and assert
     * - attempt to trick `distinctUntilChanged` by deleting the previously stored objects and save
     *   the same ones, resulting into an unchanged aggregate
     * - ensure we receive no emissions
     * - delete stored values to trigger an emission
     */
    @Suppress("ComplexMethod", "LongMethod")
    private fun asFlowAggregatorAssertions(
        type: AggregatorQueryType,
        propertyDescriptor: PropertyDescriptor
    ) {
        val channel = Channel<Any?>(1)
        val expectedAggregate = when (type) {
            AggregatorQueryType.MIN -> expectedMin(propertyDescriptor.clazz)
            AggregatorQueryType.MAX -> expectedMax(propertyDescriptor.clazz)
            AggregatorQueryType.SUM -> expectedSum(propertyDescriptor.clazz)
        }

        runBlocking {
            val observer = async {
                realm.query<QuerySample>()
                    .let { query ->
                        when (type) {
                            AggregatorQueryType.MIN -> query.min(
                                propertyDescriptor.property.name,
                                propertyDescriptor.clazz
                            )
                            AggregatorQueryType.MAX -> query.max(
                                propertyDescriptor.property.name,
                                propertyDescriptor.clazz
                            )
                            AggregatorQueryType.SUM -> query.sum(
                                propertyDescriptor.property.name,
                                propertyDescriptor.clazz
                            )
                        }
                    }.asFlow()
                    .collect { aggregatedValue ->
                        channel.send(aggregatedValue)
                    }
            }

            val aggregatedValue = channel.receive()
            when (type) {
                AggregatorQueryType.SUM -> when (aggregatedValue) {
                    is Number -> assertEquals(0, aggregatedValue.toInt())
                    is Char -> assertEquals(0, aggregatedValue.code)
                    else -> throw IllegalStateException("Expected a Number or a Char but got $aggregatedValue.")
                }
                else -> assertNull(aggregatedValue)
            }

            // Trigger an emission
            realm.writeBlocking {
                copyToRealm(getInstance(propertyDescriptor, QuerySample(), 0))
                copyToRealm(getInstance(propertyDescriptor, QuerySample(), 1))
            }

            val receivedAggregate = channel.receive()
            when (type) {
                AggregatorQueryType.SUM -> when (receivedAggregate) {
                    is Number -> assertEquals(expectedAggregate, receivedAggregate)
                    is Char -> assertEquals(expectedAggregate, receivedAggregate.code)
                }
                else -> assertEquals(expectedAggregate, receivedAggregate)
            }

            // Attempt to fool the flow by not changing the aggregations
            // This should NOT trigger an emission
            realm.writeBlocking {
                // First delete the existing data within the transaction
                delete(query<QuerySample>())

                // Then insert the same data - which should result in the same aggregated values
                // Therefore not emitting anything
                copyToRealm(getInstance(propertyDescriptor, QuerySample(), 0))
                copyToRealm(getInstance(propertyDescriptor, QuerySample(), 1))
            }

            // Should not receive anything and should time out
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(100) {
                    channel.receive()
                }
            }

            // Now change the values again to trigger an update
            realm.writeBlocking {
                delete(query<QuerySample>())
            }

            val finalAggregatedValue = channel.receive()
            when (type) {
                AggregatorQueryType.SUM -> when (finalAggregatedValue) {
                    is Number -> assertEquals(0, finalAggregatedValue.toInt())
                    is Char -> assertEquals(0, finalAggregatedValue.code)
                    else -> throw IllegalStateException("Expected a Number or a Char but got $finalAggregatedValue.")
                }
                else -> assertNull(finalAggregatedValue)
            }

            observer.cancel()
            channel.close()

            // Make sure to delete all objects after assertions as aggregators to clean state and
            // avoid "null vs 0" results when testing
            cleanUpBetweenProperties()
        }
    }

    @Suppress("ComplexMethod", "LongMethod")
    private fun asFlowDeleteObservableAssertions(
        type: AggregatorQueryType,
        propertyDescriptor: PropertyDescriptor
    ) {
        val channel = Channel<Any?>(1)
        val expectedAggregate = when (type) {
            AggregatorQueryType.MIN -> expectedMin(propertyDescriptor.clazz)
            AggregatorQueryType.MAX -> expectedMax(propertyDescriptor.clazz)
            AggregatorQueryType.SUM -> expectedSum(propertyDescriptor.clazz)
        }

        runBlocking {
            realm.writeBlocking {
                copyToRealm(getInstance(propertyDescriptor, QuerySample(), 0))
                copyToRealm(getInstance(propertyDescriptor, QuerySample(), 1))
            }

            val observer = async {
                realm.query<QuerySample>()
                    .let { query ->
                        when (type) {
                            AggregatorQueryType.MIN -> query.min(
                                propertyDescriptor.property.name,
                                propertyDescriptor.clazz
                            )
                            AggregatorQueryType.MAX -> query.max(
                                propertyDescriptor.property.name,
                                propertyDescriptor.clazz
                            )
                            AggregatorQueryType.SUM -> query.sum(
                                propertyDescriptor.property.name,
                                propertyDescriptor.clazz
                            )
                        }
                    }.asFlow()
                    .collect { aggregatedValue ->
                        channel.send(aggregatedValue)
                    }
            }

            val receivedAggregate = channel.receive()
            when (type) {
                AggregatorQueryType.SUM -> when (receivedAggregate) {
                    is Number -> assertEquals(expectedAggregate, receivedAggregate)
                    is Char -> assertEquals(expectedAggregate, receivedAggregate.code)
                }
                else -> assertEquals(expectedAggregate, receivedAggregate)
            }

            // Now delete objects to trigger a new emission
            realm.write {
                delete(query<QuerySample>())
            }

            val aggregatedValue = channel.receive()
            when (type) {
                AggregatorQueryType.SUM -> when (aggregatedValue) {
                    is Number -> assertEquals(0, aggregatedValue.toInt())
                    is Char -> assertEquals(0, aggregatedValue.code)
                    else -> throw IllegalStateException("Expected a Number or a Char but got $aggregatedValue.")
                }
                else -> assertNull(aggregatedValue)
            }

            observer.cancel()
            channel.close()

            // Make sure to delete all objects after assertions as aggregators to clean state and
            // avoid "null vs 0" results when testing
            cleanUpBetweenProperties()
        }
    }

    @Suppress("ComplexMethod", "LongMethod")
    private fun asFlowCancel(
        type: AggregatorQueryType,
        propertyDescriptor: PropertyDescriptor
    ) {
        val channel1 = Channel<Any?>(1)
        val channel2 = Channel<Any?>(1)

        runBlocking {
            // Subscribe to flow 1
            val observer1 = async {
                realm.query<QuerySample>()
                    .let { query ->
                        when (type) {
                            AggregatorQueryType.MIN -> query.min(
                                propertyDescriptor.property.name,
                                propertyDescriptor.clazz
                            )
                            AggregatorQueryType.MAX -> query.max(
                                propertyDescriptor.property.name,
                                propertyDescriptor.clazz
                            )
                            AggregatorQueryType.SUM -> query.sum(
                                propertyDescriptor.property.name,
                                propertyDescriptor.clazz
                            )
                        }
                    }.asFlow()
                    .collect { aggregatedValue ->
                        channel1.send(aggregatedValue)
                    }
            }

            // Subscribe to flow 2
            val observer2 = async {
                realm.query<QuerySample>()
                    .let { query ->
                        when (type) {
                            AggregatorQueryType.MIN -> query.min(
                                propertyDescriptor.property.name,
                                propertyDescriptor.clazz
                            )
                            AggregatorQueryType.MAX -> query.max(
                                propertyDescriptor.property.name,
                                propertyDescriptor.clazz
                            )
                            AggregatorQueryType.SUM -> query.sum(
                                propertyDescriptor.property.name,
                                propertyDescriptor.clazz
                            )
                        }
                    }.asFlow()
                    .collect { aggregatedValue ->
                        channel2.send(aggregatedValue)
                    }
            }

            val initialAggregate1 = channel1.receive()
            val initialAggregate2 = channel2.receive()
            when (type) {
                AggregatorQueryType.SUM -> when (initialAggregate1) {
                    is Number -> {
                        assertEquals(0, initialAggregate1.toInt())
                        assertEquals(0, (initialAggregate2 as Number).toInt())
                    }
                    is Char -> {
                        assertEquals(0, initialAggregate1.code)
                        assertEquals(0, (initialAggregate2 as Char).code)
                    }
                }
                else -> {
                    assertNull(initialAggregate1)
                    assertNull(initialAggregate2)
                }
            }

            // Cancel flow 1
            observer1.cancel()

            // Write one object
            realm.writeBlocking {
                copyToRealm(getInstance(propertyDescriptor, QuerySample(), 0))
            }

            // Assert emission and that the original channel hasn't been received
            val receivedAggregate = channel2.receive()
            val expectedAggregate = propertyDescriptor.values[0]
            assertEquals(expectedAggregate, receivedAggregate)
            assertTrue(channel1.isEmpty)

            observer2.cancel()
            channel1.close()
            channel2.close()

            // Make sure to delete all objects after assertions as aggregators to clean state and
            // avoid "null vs 0" results when testing
            cleanUpBetweenProperties()
        }
    }

    // ------------------------
    // General-purpose helpers
    // ------------------------

    // Deletes all objects after assertions to avoid "null vs 0" results when testing aggregators
    private fun cleanUpBetweenProperties() = realm.writeBlocking {
        delete(query(QuerySample::class))
    }

    // -------------------------------------------------------
    // Descriptors used for exhaustive testing on aggregators
    // -------------------------------------------------------

    @Suppress("ComplexMethod")
    private fun getDescriptor(
        classifier: KClassifier,
        isNullable: Boolean = false
    ): PropertyDescriptor = when (classifier) {
        Int::class -> PropertyDescriptor(
            if (isNullable) QuerySample::nullableIntField else QuerySample::intField,
            Int::class,
            if (isNullable) NULLABLE_INT_VALUES else INT_VALUES
        )
        Short::class -> PropertyDescriptor(
            if (isNullable) QuerySample::nullableShortField else QuerySample::shortField,
            Short::class,
            if (isNullable) NULLABLE_SHORT_VALUES else SHORT_VALUES
        )
        Long::class -> PropertyDescriptor(
            if (isNullable) QuerySample::nullableLongField else QuerySample::longField,
            Long::class,
            if (isNullable) NULLABLE_LONG_VALUES else LONG_VALUES
        )
        Float::class -> PropertyDescriptor(
            if (isNullable) QuerySample::nullableFloatField else QuerySample::floatField,
            Float::class,
            if (isNullable) NULLABLE_FLOAT_VALUES else FLOAT_VALUES
        )
        Double::class -> PropertyDescriptor(
            if (isNullable) QuerySample::nullableDoubleField else QuerySample::doubleField,
            Double::class,
            if (isNullable) NULLABLE_DOUBLE_VALUES else DOUBLE_VALUES
        )
        Byte::class -> PropertyDescriptor(
            if (isNullable) QuerySample::nullableByteField else QuerySample::byteField,
            Byte::class,
            if (isNullable) NULLABLE_BYTE_VALUES else BYTE_VALUES
        )
        Char::class -> PropertyDescriptor(
            if (isNullable) QuerySample::nullableCharField else QuerySample::charField,
            Char::class,
            if (isNullable) NULLABLE_CHAR_VALUES else CHAR_VALUES
        )
        else -> throw IllegalArgumentException("Invalid type descriptor: $classifier")
    }

    private val basePropertyDescriptors =
        TypeDescriptor.aggregateClassifiers.keys.map { getDescriptor(it) }

    private val timestampDescriptor = PropertyDescriptor(
        QuerySample::timestampField,
        RealmInstant::class,
        TIMESTAMP_VALUES
    )

    private val propertyDescriptors = basePropertyDescriptors + timestampDescriptor

    private val propertyDescriptorsForSum = basePropertyDescriptors

    private val nullableBasePropertyDescriptors =
        TypeDescriptor.aggregateClassifiers.keys.map { getDescriptor(it, true) }

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

    // TODO figure out whether we need to test aggregators on RealmList<Number> fields
    //  see https://github.com/realm/realm-core/issues/5137
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
    val values: List<Any?>
)

/**
 * Use this and not [io.realm.kotlin.entities.Sample] as that class has default initializers that make
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
    var timestampField: RealmInstant = RealmInstant.from(100, 1000)
    var objectIdField: ObjectId = ObjectId.from("507f191e810c19729de860ea")
    var bsonObjectIdField: BsonObjectId = BsonObjectId("507f191e810c19729de860ea")
    var uuidField: RealmUUID = RealmUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d76")
    var binaryField: ByteArray = byteArrayOf(42)
    var realmAnyField: RealmAny? = RealmAny.create(42)

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
    var nullableObjectIdField: ObjectId? = null
    var nullableBsonObjectIdField: BsonObjectId? = null
    var nullableBinaryField: ByteArray? = null
    var nullableRealmObject: QuerySample? = null

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
    var objectIdListField: RealmList<ObjectId> = realmListOf()
    var bsonObjectIdListField: RealmList<BsonObjectId> = realmListOf()
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
    var nullableObjectIdListField: RealmList<ObjectId?> = realmListOf()
    var nullableBsonObjectIdListField: RealmList<BsonObjectId?> = realmListOf()

    var child: QuerySample? = null
}
