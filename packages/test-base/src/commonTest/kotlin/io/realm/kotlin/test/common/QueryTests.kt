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

package io.realm.kotlin.test.common

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
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
import io.realm.kotlin.test.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestChannel
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.annotations.FullText
import io.realm.kotlin.types.annotations.PersistedName
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.mongodb.kbson.BsonDecimal128
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
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
        if (this::realm.isInitialized && !realm.isClosed()) {
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
        assertFailsWithMessage<IllegalArgumentException>("Cannot compare argument \$0 with value 'true' to a string") {
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
                    decimal128Field = Decimal128("2.8446744073709551618E-6151")
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
                RealmStorageType.DECIMAL128 -> {
                    checkQuery(QuerySample::decimal128Field, Decimal128("2.8446744073709551618E-6151"))
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
                RealmStorageType.ANY -> {
                    checkQuery(QuerySample::realmAnyField, RealmAny.create("Hello"))
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
        assertFailsWithMessage<IllegalArgumentException>("Request for argument at index 0 but no arguments are provided") {
            realm.query<QuerySample>("stringField = $0")
        }

        assertFailsWithMessage<IllegalArgumentException>("Unsupported comparison between type 'string' and type 'int'") {
            realm.query<QuerySample>("stringField = 42")
        }

        assertFailsWithMessage<IllegalArgumentException>("'QuerySample' has no property 'nonExistingField'") {
            realm.query<QuerySample>("nonExistingField = 13")
        }
    }

    @Test
    fun find_mutableRealm_malformedQueryThrows() {
        realm.writeBlocking {
            assertFailsWithMessage<IllegalArgumentException>("Request for argument at index 0 but no arguments are provided") {
                query<QuerySample>("stringField = $0")
            }

            assertFailsWithMessage<IllegalArgumentException>("Unsupported comparison between type 'string' and type 'int'") {
                query<QuerySample>("stringField = 42")
            }

            assertFailsWithMessage<IllegalArgumentException>("'QuerySample' has no property 'nonExistingField'") {
                query<QuerySample>("nonExistingField = 13")
            }
        }
    }

    @Test
    fun asFlow_initialResults() {
        val channel = TestChannel<ResultsChange<QuerySample>>()

        runBlocking {
            val observer = async {
                realm.query<QuerySample>()
                    .asFlow()
                    .collect { results ->
                        assertNotNull(results)
                        channel.send(results)
                    }
            }

            channel.receiveOrFail().let { resultsChange ->
                assertIs<InitialResults<*>>(resultsChange)
                assertTrue(resultsChange.list.isEmpty())
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun asFlow() {
        val channel = TestChannel<ResultsChange<QuerySample>>()

        runBlocking {
            val observer = async {
                realm.query<QuerySample>()
                    .asFlow()
                    .collect { results ->
                        assertNotNull(results)
                        channel.send(results)
                    }
            }

            channel.receiveOrFail().let { resultsChange ->
                assertIs<InitialResults<*>>(resultsChange)
                assertTrue(resultsChange.list.isEmpty())
            }

            realm.writeBlocking {
                copyToRealm(QuerySample())
            }

            channel.receiveOrFail().let { resultsChange ->
                assertIs<UpdatedResults<*>>(resultsChange)
                assertEquals(1, resultsChange.list.size)
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun asFlow_deleteObservable() {
        val channel = TestChannel<ResultsChange<QuerySample>>()

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

            channel.receiveOrFail().let { resultsChange ->
                assertIs<InitialResults<*>>(resultsChange)
                assertEquals(1, resultsChange.list.size)
            }

            realm.writeBlocking {
                delete(query<QuerySample>())
            }

            channel.receiveOrFail().let { resultsChange ->
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
    fun sort_realmAny() {
        realm.writeBlocking {
            QUERY_REALM_ANY_VALUES.forEach {
                copyToRealm(QuerySample().apply { realmAnyField = it })
            }
        }

        // No filter, multiple sortings
        realm.query<QuerySample>()
            .sort(QuerySample::realmAnyField.name to Sort.DESCENDING)
            .find { results ->
                val managedRealmAny = assertNotNull(results.first().realmAnyField)
                val expected = REALM_ANY_MAX.asRealmObject(QuerySample::class)
                val actual = managedRealmAny.asRealmObject(QuerySample::class)
                assertEquals(expected.stringField, actual.stringField)
                // Boolean is the "lowest" but it is not the last element since we also have null
                assertEquals(REALM_ANY_MIN, results[results.size - 2].realmAnyField)
                assertEquals(null, results.last().realmAnyField)
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
        val channel = TestChannel<Long>()

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

            assertEquals(0, channel.receiveOrFail())

            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun count_asFlow() {
        val channel = TestChannel<Long>()

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

            assertEquals(0, channel.receiveOrFail())

            realm.writeBlocking {
                copyToRealm(QuerySample())
            }

            assertEquals(1, channel.receiveOrFail())
            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun count_asFlow_deleteObservable() {
        val channel = TestChannel<Long>()

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

            assertEquals(1, channel.receiveOrFail())

            realm.write {
                delete(query<QuerySample>())
            }

            assertEquals(0, channel.receiveOrFail())

            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun count_asFlow_cancel() {
        runBlocking {
            val channel1 = TestChannel<Long?>()
            val channel2 = TestChannel<Long?>()

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

            assertEquals(0, channel1.receiveOrFail())
            assertEquals(0, channel2.receiveOrFail())

            // Write one object
            realm.write {
                copyToRealm(QuerySample().apply { stringField = "Bar" })
            }

            // Assert emission and cancel first subscription
            assertEquals(1, channel1.receiveOrFail())
            assertEquals(1, channel2.receiveOrFail())
            observer1.cancel()

            // Write another object
            realm.write {
                copyToRealm(QuerySample().apply { stringField = "Baz" })
            }

            // Assert emission and that the original channel hasn't been received
            assertEquals(2, channel2.receiveOrFail())
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
    fun verifySupportedSumAggregators() {
        assertEquals(
            setOf(
                Byte::class,
                Char::class,
                Short::class,
                Int::class,
                Long::class,
                Float::class,
                Double::class,
                BsonDecimal128::class
            ),
            TypeDescriptor.classifiers.filter { (_, coreFieldType) ->
                coreFieldType.aggregatorSupport.contains(
                    TypeDescriptor.AggregatorSupport.SUM
                )
            }.keys
        )
    }

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
                when (sumValueBefore) {
                    is Number -> assertEquals(0, sumValueBefore.toInt())
                    is Decimal128 -> assertEquals(Decimal128("0"), sumValueBefore)
                }

                saveData(propertyDescriptor)

                val sumValueAfter = sumQuery.find()
                when (sumValueAfter) {
                    is Number -> assertEquals(expectedSum, sumValueAfter)
                    is Decimal128 -> assertEquals(expectedSum, sumValueAfter)
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
    fun sum_find_intPropertyCoercedCorrectly() {
        sum_find_numericPropertiesCoercedCorrectly(QuerySample::intField)
    }

    @Test
    fun sum_find_doublePropertyCoercedCorrectly() {
        sum_find_numericPropertiesCoercedCorrectly(QuerySample::doubleField)
    }

    @Test
    fun sum_find_floatPropertyCoercedCorrectly() {
        sum_find_numericPropertiesCoercedCorrectly(QuerySample::floatField)
    }

    @Test
    fun sum_throwsIfRealmInstant() {
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .sum(QuerySample::timestampField.name, RealmInstant::class)
        }

        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>()
                .sum(QuerySample::nullableTimestampField.name, RealmInstant::class)
        }
    }

    @Test
    fun sum_throwsIfInvalidProperty() {
        // Numeric to something else, only test Int or Short or Long for RLM_PROPERTY_TYPE_INT columns
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .sum<String>(QuerySample::intField.name)
        }
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .sum<String>(QuerySample::floatField.name)
        }
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .sum<String>(QuerySample::doubleField.name)
        }

        // Max of an invalid primitive
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .sum<Int>(QuerySample::stringField.name)
        }

        // Max of a non-numerical RealmList
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .sum<Int>(QuerySample::stringListField.name)
        }

        // Max of an object
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .sum<Int>(QuerySample::child.name)
        }

        // Max of a RealmAny column
        assertFailsWithMessage<IllegalArgumentException>("RealmAny properties cannot be aggregated") {
            realm.query<QuerySample>()
                .sum<Int>(QuerySample::realmAnyField.name)
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
    fun sum_asFlow() {
        for (propertyDescriptor in allPropertyDescriptorsForSum) {
            asFlowAggregatorAssertions(AggregatorQueryType.SUM, propertyDescriptor)
            asFlowDeleteObservableAssertions(AggregatorQueryType.SUM, propertyDescriptor)
            asFlowCancel(AggregatorQueryType.SUM, propertyDescriptor)
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
    fun verifySupportedMaxAggregators() {
        assertEquals(
            setOf(
                Byte::class,
                Char::class,
                Short::class,
                Int::class,
                Long::class,
                Float::class,
                Double::class,
                BsonDecimal128::class,
                RealmInstant::class,
            ),
            TypeDescriptor.classifiers.filter { (_, coreFieldType) ->
                coreFieldType.aggregatorSupport.contains(
                    TypeDescriptor.AggregatorSupport.MAX
                )
            }.keys
        )
    }

    @Test
    fun verifyMinMaxAggragatorSupport() {
        assertEquals(supportedMinTypes, supportedMaxTypes)
    }

    @Test
    fun max_find_emptyTable() {
        val assertions = { propertyDescriptor: PropertyDescriptor ->
            realm.query<QuerySample>()
                .max(propertyDescriptor.property.name, propertyDescriptor.clazz)
                .find { maxValue -> assertNull(maxValue) }
        }

        // Iterate over all properties
        for (propertyDescriptor in allPropertyDescriptorsForMax) {
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
        for (nullablePropertyDescriptor in nullablePropertyDescriptorsForMax) {
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

                saveData(propertyDescriptor)
                val queryMax = maxQuery.find()

                if (expectedMax is RealmAny) {
                    // The MAX of a RealmAny column is RealmObject, compare by field
                    val expected = expectedMax.asRealmObject(QuerySample::class)
                    val actual = (queryMax as RealmAny).asRealmObject(QuerySample::class)
                    assertEquals(expected.stringField, actual.stringField)
                } else {
                    assertEquals(expectedMax, queryMax)
                }
            }

            realm.query(QuerySample::class)
                .max(propertyDescriptor.property.name, propertyDescriptor.clazz)
                .find { maxValue ->
                    // The MAX of a RealmAny column is RealmObject, compare by field
                    if (expectedMax is RealmAny) {
                        val expected = expectedMax.asRealmObject(QuerySample::class)
                        val actual = (maxValue as RealmAny).asRealmObject(QuerySample::class)
                        assertEquals(expected.stringField, actual.stringField)
                    } else {
                        assertEquals(expectedMax, maxValue)
                    }
                }

            // Make sure to delete all objects after assertions as aggregators to clean state and
            // avoid "null vs 0" results when testing
            cleanUpBetweenProperties()
        }

        // Iterate over all properties
        for (propertyDescriptor in allPropertyDescriptorsForMax) {
            assertions(propertyDescriptor)
        }
    }

    @Test
    fun max_find_intPropertyCoercedCorrectly() {
        max_find_numericPropertiesCoercedCorrectly(QuerySample::intField)
    }

    @Test
    fun max_find_doublePropertyCoercedCorrectly() {
        max_find_numericPropertiesCoercedCorrectly(QuerySample::doubleField)
    }

    @Test
    fun max_find_floatPropertyCoercedCorrectly() {
        max_find_numericPropertiesCoercedCorrectly(QuerySample::floatField)
    }

    @Test
    fun max_throwsIfInvalidProperty() {
        // Numeric to something else, only test Int or Short or Long for RLM_PROPERTY_TYPE_INT columns
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .max<String>(QuerySample::intField.name)
        }
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .max<String>(QuerySample::floatField.name)
        }
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .max<String>(QuerySample::doubleField.name)
        }

        // Max of an invalid primitive
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .max<Int>(QuerySample::stringField.name)
        }

        // Max of a non-numerical RealmList
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .max<Int>(QuerySample::stringListField.name)
        }

        // Max of an object
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .max<Int>(QuerySample::child.name)
        }

        // Max of a RealmAny column
        assertFailsWithMessage<IllegalArgumentException>("RealmAny properties cannot be aggregated") {
            realm.query<QuerySample>()
                .max<Int>(QuerySample::realmAnyField.name)
        }
    }

    @Test
    fun max_asFlow() {
        for (propertyDescriptor in allPropertyDescriptorsForMax) {
            asFlowAggregatorAssertions(AggregatorQueryType.MAX, propertyDescriptor)
            asFlowDeleteObservableAssertions(AggregatorQueryType.MAX, propertyDescriptor)
            asFlowCancel(AggregatorQueryType.MAX, propertyDescriptor)
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
    fun verifySupportedMinAggregators() {
        assertEquals(
            setOf(
                Byte::class,
                Char::class,
                Short::class,
                Int::class,
                Long::class,
                Float::class,
                Double::class,
                BsonDecimal128::class,
                RealmInstant::class,
            ),
            TypeDescriptor.classifiers.filter { (_, coreFieldType) ->
                coreFieldType.aggregatorSupport.contains(
                    TypeDescriptor.AggregatorSupport.MIN
                )
            }.keys
        )
    }

    @Test
    fun min_find_emptyTable() {
        val assertions = { propertyDescriptor: PropertyDescriptor ->
            realm.query<QuerySample>()
                .min(propertyDescriptor.property.name, propertyDescriptor.clazz)
                .find { minValue -> assertNull(minValue) }
        }

        // Iterate over all properties
        for (propertyDescriptor in allPropertyDescriptorsForMin) {
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
        for (nullablePropertyDescriptor in nullablePropertyDescriptorsForMin) {
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
                saveData(propertyDescriptor)
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
        for (propertyDescriptor in allPropertyDescriptorsForMin) {
            assertions(propertyDescriptor)
        }
    }

    @Test
    fun min_find_intPropertyCoercedCorrectly() {
        min_find_numericPropertiesCoercedCorrectly(QuerySample::intField)
    }

    @Test
    fun min_find_doublePropertyCoercedCorrectly() {
        min_find_numericPropertiesCoercedCorrectly(QuerySample::doubleField)
    }

    @Test
    fun min_find_floatPropertyCoercedCorrectly() {
        min_find_numericPropertiesCoercedCorrectly(QuerySample::floatField)
    }

    @Test
    fun min_throwsIfInvalidProperty() {
        // Numeric to something else, only test Int or Short or Long for RLM_PROPERTY_TYPE_INT columns
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .min<String>(QuerySample::intField.name)
        }
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .min<String>(QuerySample::floatField.name)
        }
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .min<String>(QuerySample::doubleField.name)
        }

        // Max of an invalid primitive
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .min<Int>(QuerySample::stringField.name)
        }

        // Max of a non-numerical RealmList
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .min<Int>(QuerySample::stringListField.name)
        }

        // Max of an object
        assertFailsWithMessage<IllegalArgumentException>("Conversion not possible between") {
            realm.query<QuerySample>()
                .min<Int>(QuerySample::child.name)
        }

        // Max of a RealmAny column
        assertFailsWithMessage<IllegalArgumentException>("RealmAny properties cannot be aggregated") {
            realm.query<QuerySample>()
                .min<Int>(QuerySample::realmAnyField.name)
        }
    }

    @Test
    fun min_asFlow() {
        for (propertyDescriptor in allPropertyDescriptorsForMin) {
            asFlowAggregatorAssertions(AggregatorQueryType.MIN, propertyDescriptor)
            asFlowDeleteObservableAssertions(AggregatorQueryType.MIN, propertyDescriptor)
            asFlowCancel(AggregatorQueryType.MIN, propertyDescriptor)
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

            channel.receiveOrFail().let { objectChange ->
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

            channel.receiveOrFail().let { objectChange ->
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

            channel.receiveOrFail().let { objectChange ->
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

            channel.receiveOrFail().let { objectChange ->
                assertIs<UpdatedObject<QuerySample>>(objectChange)
                assertEquals(7, objectChange.obj.intField)
            }

            // Delete the head element 6
            // [4, 3, 2, 1]
            realm.writeBlocking {
                delete(query<QuerySample>("intField = $0", 7).first().find()!!)
            }

            assertIs<DeletedObject<QuerySample>>(channel.receiveOrFail())

            channel.receiveOrFail().let { objectChange ->
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

            channel.receiveOrFail().let { objectChange ->
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

            assertIs<DeletedObject<QuerySample>>(channel.receiveOrFail())

            channel.receiveOrFail().let { objectChange ->
                assertIs<InitialObject<QuerySample>>(objectChange)
                assertEquals(10, objectChange.obj.intField)
            }

            // Empty the list
            // []
            realm.writeBlocking {
                delete(query<QuerySample>())
            }

            assertIs<DeletedObject<QuerySample>>(channel.receiveOrFail())

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

            assertIs<PendingObject<*>>(channel1.receiveOrFail())
            assertIs<PendingObject<*>>(channel2.receiveOrFail())

            // Write one object
            realm.write {
                copyToRealm(QuerySample().apply { stringField = "Bar" })
            }

            // Assert emission and cancel first subscription
            assertIs<InitialObject<*>>(channel1.receiveOrFail())
            assertIs<InitialObject<*>>(channel2.receiveOrFail())
            observer1.cancel()

            // Update object
            realm.write {
                query<QuerySample>("stringField = $0", "Bar").first().find {
                    it!!.stringField = "Baz"
                }
            }

            // Assert emission and that the original channel hasn't been received
            assertIs<UpdatedObject<*>>(channel2.receiveOrFail())
            assertTrue(channel1.isEmpty)

            observer2.cancel()
            channel1.close()
            channel2.close()
        }
    }

    @Test
    fun list_query() {
        realm.writeBlocking {
            copyToRealm(
                QuerySample().apply {
                    intListField = realmListOf(1, 2, 3)
                    floatListField = realmListOf(1f, 2f, 3f)
                    doubleListField = realmListOf(1.0, 2.0, 3.0)
                    decimal128ListField = realmListOf(
                        Decimal128("1"),
                        Decimal128("2"),
                        Decimal128("3")
                    )
                    nullableIntListField = realmListOf(1, 2, 3)
                    nullableFloatListField = realmListOf(1f, 2f, 3f)
                    nullableDoubleListField = realmListOf(1.0, 2.0, 3.0)
                    nullableDecimal128ListField = realmListOf(
                        Decimal128("1"),
                        Decimal128("2"),
                        Decimal128("3")
                    )
                }
            )
        }

        // Supported fields for aggregations
        listOf("@sum", "@min", "@max", "@avg")
            .forEach { aggregator ->
                listOf(
                    QuerySample::intListField.name, // Just test one Long value
                    QuerySample::floatListField.name,
                    QuerySample::doubleListField.name,
                    QuerySample::decimal128ListField.name,
                    QuerySample::nullableIntListField.name,
                    QuerySample::nullableFloatListField.name,
                    QuerySample::nullableDoubleListField.name,
                    QuerySample::nullableDecimal128ListField.name,
                ).forEach { field ->
                    realm.query<QuerySample>("$field.$aggregator < 7")
                        .find { assertEquals(1, it.size) }
                    realm.query<QuerySample>("$field.$aggregator > 42")
                        .find { assertTrue(it.isEmpty()) }
                }
            }

        // Unsupported fields
        listOf("@sum", "@min", "@max", "@avg")
            .forEach { aggregator ->
                listOf(
                    QuerySample::booleanListField.name,
                    QuerySample::objectIdListField.name,
                    QuerySample::bsonObjectIdListField.name,
                    QuerySample::timestampListField.name,
                    QuerySample::binaryListField.name,
                    QuerySample::nullableBooleanListField.name,
                    QuerySample::nullableObjectIdListField.name,
                    QuerySample::nullableBsonObjectIdListField.name,
                    QuerySample::nullableTimestampListField.name,
                    QuerySample::nullableBinaryListField.name,
                ).forEach { field ->
                    assertFailsWithMessage<IllegalArgumentException>("Cannot use aggregate '.$aggregator' for this type of property") {
                        realm.query<QuerySample>("$field.$aggregator > 42")
                    }
                }
            }
    }

    @Test
    fun set_query() {
        realm.writeBlocking {
            copyToRealm(
                QuerySample().apply {
                    intSetField = realmSetOf(1, 2, 3)
                    floatSetField = realmSetOf(1f, 2f, 3f)
                    doubleSetField = realmSetOf(1.0, 2.0, 3.0)
                    decimal128SetField = realmSetOf(
                        Decimal128("1"),
                        Decimal128("2"),
                        Decimal128("3")
                    )
                    nullableIntSetField = realmSetOf(1, 2, 3)
                    nullableFloatSetField = realmSetOf(1f, 2f, 3f)
                    nullableDoubleSetField = realmSetOf(1.0, 2.0, 3.0)
                    nullableDecimal128SetField = realmSetOf(
                        Decimal128("1"),
                        Decimal128("2"),
                        Decimal128("3")
                    )
                }
            )
        }

        // Supported fields for aggregations
        listOf("@sum", "@min", "@max", "@avg")
            .forEach { aggregator ->
                listOf(
                    QuerySample::intSetField.name, // Just test one Long value
                    QuerySample::floatSetField.name,
                    QuerySample::doubleSetField.name,
                    QuerySample::decimal128SetField.name,
                    QuerySample::nullableIntSetField.name,
                    QuerySample::nullableFloatSetField.name,
                    QuerySample::nullableDoubleSetField.name,
                    QuerySample::nullableDecimal128SetField.name,
                ).forEach { field ->
                    realm.query<QuerySample>("$field.$aggregator < 7")
                        .find { assertEquals(1, it.size) }
                    realm.query<QuerySample>("$field.$aggregator > 42")
                        .find { assertTrue(it.isEmpty()) }
                }
            }

        // Unsupported fields
        listOf("@sum", "@min", "@max", "@avg")
            .forEach { aggregator ->
                listOf(
                    QuerySample::booleanSetField.name,
                    QuerySample::objectIdSetField.name,
                    QuerySample::bsonObjectIdSetField.name,
                    QuerySample::timestampSetField.name,
                    QuerySample::binaryListField.name,
                    QuerySample::nullableBooleanSetField.name,
                    QuerySample::nullableObjectIdSetField.name,
                    QuerySample::nullableBsonObjectIdSetField.name,
                    QuerySample::nullableTimestampSetField.name,
                    QuerySample::nullableBinaryListField.name,
                ).forEach { field ->
                    assertFailsWithMessage<IllegalArgumentException>("Cannot use aggregate '.$aggregator' for this type of property") {
                        realm.query<QuerySample>("$field.$aggregator > 42")
                    }
                }
            }
    }

    @Test
    fun dictionary_query() {
        realm.writeBlocking {
            copyToRealm(
                QuerySample().apply {
                    intDictionaryField = realmDictionaryOf("A" to 1, "B" to 2, "C" to 3)
                    floatDictionaryField = realmDictionaryOf("A" to 1f, "B" to 2f, "C" to 3f)
                    doubleDictionaryField = realmDictionaryOf("A" to 1.0, "B" to 2.0, "C" to 3.0)
                    decimal128DictionaryField = realmDictionaryOf(
                        "A" to Decimal128("1"),
                        "B" to Decimal128("2"),
                        "C" to Decimal128("3")
                    )
                    nullableIntDictionaryField = realmDictionaryOf("A" to 1, "B" to 2, "C" to 3)
                    nullableFloatDictionaryField = realmDictionaryOf("A" to 1f, "B" to 2f, "C" to 3f)
                    nullableDoubleDictionaryField = realmDictionaryOf("A" to 1.0, "B" to 2.0, "C" to 3.0)
                    nullableDecimal128DictionaryField = realmDictionaryOf(
                        "A" to Decimal128("1"),
                        "B" to Decimal128("2"),
                        "C" to Decimal128("3")
                    )
                }
            )
        }

        // Supported fields for aggregations
        listOf("@sum", "@min", "@max", "@avg")
            .forEach { aggregator ->
                listOf(
                    QuerySample::intDictionaryField.name, // Just test one Long value
                    QuerySample::floatDictionaryField.name,
                    QuerySample::doubleDictionaryField.name,
                    QuerySample::decimal128DictionaryField.name,
                    QuerySample::nullableIntDictionaryField.name,
                    QuerySample::nullableFloatDictionaryField.name,
                    QuerySample::nullableDoubleDictionaryField.name,
                    QuerySample::nullableDecimal128DictionaryField.name,
                ).forEach { field ->
                    realm.query<QuerySample>("$field.$aggregator < 7")
                        .find { assertEquals(1, it.size) }
                    realm.query<QuerySample>("$field.$aggregator > 42")
                        .find { assertTrue(it.isEmpty()) }
                }
            }

        // Unsupported fields
        listOf("@sum", "@min", "@max", "@avg")
            .forEach { aggregator ->
                listOf(
                    QuerySample::booleanDictionaryField.name,
                    QuerySample::objectIdDictionaryField.name,
                    QuerySample::bsonObjectIdDictionaryField.name,
                    QuerySample::timestampDictionaryField.name,
                    QuerySample::binaryListField.name,
                    QuerySample::nullableBooleanDictionaryField.name,
                    QuerySample::nullableObjectIdDictionaryField.name,
                    QuerySample::nullableBsonObjectIdDictionaryField.name,
                    QuerySample::nullableTimestampDictionaryField.name,
                    QuerySample::nullableBinaryListField.name,
                ).forEach { field ->
                    assertFailsWith<IllegalArgumentException> {
                        realm.query<QuerySample>("$field.$aggregator > 42")
                    }
                }
            }
    }

    @Test
    fun query_inListArgument_mixedCollectionTypes() {
        realm.writeBlocking {
            (0..15).forEach { copyToRealm(QuerySample().apply { intField = it }) }
        }
        val oddNumbers = mutableSetOf(1, 3, 5, 7, 9, 11, 13, 15)
        val arg0: List<Int> = listOf(1, 3)
        val arg1: Int = 5
        val arg2: Set<Int> = setOf(7, 9)
        val arg3: List<Int> = listOf(11) // Single element list should still be passed as list for IN
        val arg4: IntProgression = 13..15 step 2

        realm.query<QuerySample>(
            "intField IN $0 OR intField == $1 OR intField IN $2 or intField IN $3 or intField IN $4",
            arg0,
            arg1,
            arg2,
            arg3,
            arg4
        ).find().run {
            assertEquals(oddNumbers.size, size)
            forEach {
                assertTrue { oddNumbers.remove(it.intField) }
            }
        }
        assertTrue { oddNumbers.isEmpty() }
    }

    @Test
    fun query_inListArgument_boolean() {
        realm.writeBlocking {
            (0 until 15).forEach {
                copyToRealm(
                    QuerySample().apply {
                        intField = it
                        booleanField = it % 2 == 0
                    }
                )
            }
        }
        realm.query<QuerySample>("booleanField IN $0", listOf(true, false)).find().run {
            assertEquals(15, size)
        }
    }

    @Test
    fun query_inListArgument_string() {
        realm.writeBlocking {
            copyToRealm(QuerySample().apply { stringField = "1" })
            copyToRealm(QuerySample().apply { stringField = "2" })
        }
        realm.query<QuerySample>("stringField IN $0", listOf("1", "2")).find().run {
            assertEquals(2, size)
        }
    }

    @Test
    fun query_inListArgument_escapedString() {
        val values = mutableSetOf("\"Realm\"", "'Realm'", "Realms'", "😀")
        realm.writeBlocking {
            values.forEach {
                copyToRealm(QuerySample().apply { stringField = it })
            }
        }
        assertEquals(values.size, realm.query<QuerySample>().find().size)
        realm.query<QuerySample>(
            "stringField IN $0",
            values
        ).find().run {
            assertEquals(values.size, size)
            forEach {
                assertTrue { values.remove(it.stringField) }
            }
        }
        assertTrue { values.isEmpty() }
    }

    @Test
    fun query_inListArgument_links() {
        val (even, odd) = realm.writeBlocking {
            val even = copyToRealm(QuerySample().apply { stringField = "EVEN" })
            copyToRealm(QuerySample().apply { nullableRealmObject = even })
            val odd = copyToRealm(QuerySample().apply { stringField = "ODD" })
            copyToRealm(QuerySample().apply { nullableRealmObject = odd })
            even to odd
        }
        assertEquals(4, realm.query<QuerySample>().find().size)
        realm.query<QuerySample>("nullableRealmObject IN $0", listOf(even, odd)).find().run {
            assertEquals(2, size)
            forEach {
                assertTrue { it.stringField !in setOf("EVEN", "ODD") }
            }
        }
    }

    // ----------------------------------
    // Multithreading with query objects
    // ----------------------------------

    @Test
    fun playground_multiThreadScenario() {
        val channel = TestChannel<RealmResults<QuerySample>>()
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
            val results = channel.receiveOrFail()
            assertEquals(1, results.size)

            query!!.find { res ->
                assertEquals(1, res.size)
            }

            channel.close()
            obs.cancel()
        }
    }

    // --------------------------------------------------
    // Full-text search smoke tests
    // --------------------------------------------------
    @Test
    fun fullTextSearch() {
        realm.writeBlocking {
            copyToRealm(QuerySample(1).apply { fulltextField = "The quick brown fox jumped over the lazy dog." })
            copyToRealm(QuerySample(2).apply { fulltextField = "I sat at the cafè, drinking a BIG cup of coffee." })
            copyToRealm(QuerySample(3).apply { fulltextField = "Rødgrød med fløde." })
            copyToRealm(QuerySample(4).apply { fulltextField = "full-text-search is hard to implement!" })
            copyToRealm(QuerySample(5).apply { fulltextField = "Trying to search for an emoji, like 😊, inside a text string is not supported." })
        }

        assertEquals(1, realm.query<QuerySample>("fulltextField TEXT 'quick dog'").find().size) // words at different locations
        assertEquals(0, realm.query<QuerySample>("fulltextField TEXT 'brown -fox'").find().size) // exclusion
        assertEquals(2, realm.query<QuerySample>("fulltextField TEXT 'fo*'").find().size) // token prefix search is supported.
        assertEquals(1, realm.query<QuerySample>("fulltextField TEXT 'cafe big'").find().size) // case- and diacritics-insensitive
        assertEquals(1, realm.query<QuerySample>("fulltextField TEXT 'rødgrød'").find().size) // Latin-1 supplement
        assertEquals(0, realm.query<QuerySample>("fulltextField TEXT '😊'").find().size) // Searching outside supported chars return nothing
    }

    @Test
    fun fullTextSearch_invalidArguments() {
        assertFailsWith<IllegalArgumentException> {
            realm.query<QuerySample>("fulltextField TEXT ''").find().size
        }
    }

    @Test
    fun fullTextSearch_onNonFullTextFieldThrows() {
        assertFailsWithMessage<IllegalStateException>("Column has no fulltext index") {
            realm.query<QuerySample>("stringField TEXT 'foo'")
        }
    }

    // --------------------------------------------------
    // Class instantiation with property setting helpers
    // --------------------------------------------------

    private fun MutableRealm.saveData(propertyDescriptor: PropertyDescriptor) {
        copyToRealm(getInstance(propertyDescriptor, QuerySample(), 0))
        copyToRealm(getInstance(propertyDescriptor, QuerySample(), 1))

        // Add all values for RealmAny manually
        if (propertyDescriptor.clazz == RealmAny::class) {
            for (i in 2 until QUERY_REALM_ANY_VALUES.size) {
                copyToRealm(getInstance(propertyDescriptor, QuerySample(), i))
            }
        }
    }

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
            Decimal128::class -> setProperty(
                instance,
                property as KMutableProperty1<C, Decimal128?>,
                values as List<Decimal128?>,
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
            RealmAny::class -> setProperty(
                instance,
                property as KMutableProperty1<C, RealmAny?>,
                values as List<RealmAny?>,
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
            Decimal128::class -> when (type) {
                AggregatorQueryType.MIN -> DECIMAL128_MIN_VALUE
                AggregatorQueryType.MAX -> DECIMAL128_MAX_VALUE
                AggregatorQueryType.SUM -> Decimal128("1.800000000000000000000000000000000E+601")
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
            RealmAny::class -> when (type) {
                AggregatorQueryType.MIN -> REALM_ANY_MIN
                AggregatorQueryType.MAX -> REALM_ANY_MAX
                AggregatorQueryType.SUM -> REALM_ANY_SUM
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
        val channel = TestChannel<Any?>()
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

            val aggregatedValue = channel.receiveOrFail()
            when (type) {
                AggregatorQueryType.SUM -> when (aggregatedValue) {
                    is Number -> assertEquals(0, aggregatedValue.toInt())
                    is Char -> assertEquals(0, aggregatedValue.code)
                    is Decimal128 -> assertEquals(Decimal128("0"), aggregatedValue)
                    else -> throw IllegalStateException("Expected a Number or a Char but got $aggregatedValue.")
                }
                else -> assertNull(aggregatedValue)
            }

            // Trigger an emission
            realm.writeBlocking {
                saveData(propertyDescriptor)
            }

            val receivedAggregate = channel.receiveOrFail()
            when (propertyDescriptor.clazz) {
                RealmAny::class -> when (type) {
                    AggregatorQueryType.MIN -> {
                        val actualValue = (receivedAggregate as RealmAny)
                            .asBoolean()
                        val expectedValue = (expectedAggregate as RealmAny)
                            .asBoolean()
                        assertEquals(expectedValue, actualValue)
                    }
                    AggregatorQueryType.MAX -> {
                        val actualValue = (receivedAggregate as RealmAny)
                            .asRealmObject<QuerySample>()
                            .stringField
                        val expectedValue = (expectedAggregate as RealmAny)
                            .asRealmObject<QuerySample>()
                            .stringField
                        assertEquals(expectedValue, actualValue)
                    }
                    AggregatorQueryType.SUM ->
                        assertEquals(expectedAggregate, receivedAggregate)
                }
                else -> when (type) {
                    AggregatorQueryType.MIN -> assertEquals(expectedAggregate, receivedAggregate)
                    AggregatorQueryType.MAX -> assertEquals(expectedAggregate, receivedAggregate)
                    AggregatorQueryType.SUM -> when (receivedAggregate) {
                        is Number -> assertEquals(expectedAggregate, receivedAggregate)
                        is Char -> assertEquals(expectedAggregate, receivedAggregate.code)
                        is Decimal128 -> assertEquals(expectedAggregate, receivedAggregate)
                    }
                }
            }

            // Attempt to fool the flow by not changing the aggregations
            // This should NOT trigger an emission
            // NOTE - if the aggregated value is a RealmObject the value WILL BE EMITTED
            realm.writeBlocking {
                // First delete the existing data within the transaction
                delete(query<QuerySample>())

                // Then insert the same data - which should result in the same aggregated values
                // Therefore not emitting anything
                saveData(propertyDescriptor)
            }

            // Should not receive anything and should time out
            // NOTE - if the aggregated value is a RealmObject the value WILL BE EMITTED
            when (propertyDescriptor.clazz) {
                RealmAny::class -> when (type) {
                    AggregatorQueryType.SUM -> assertFailsWith<TimeoutCancellationException> {
                        withTimeout(100) { channel.receiveOrFail() }
                    }
                    // MAX for RealmAny is RealmObject so emitted objects will not be the same
                    // even though they may the same at a structural level
                    AggregatorQueryType.MAX -> {
                        val receivedRepeatedAggregate = channel.receiveOrFail()
                        val actualString = (receivedRepeatedAggregate as RealmAny)
                            .asRealmObject<QuerySample>()
                            .stringField
                        val expectedString = (expectedAggregate as RealmAny)
                            .asRealmObject<QuerySample>()
                            .stringField
                        assertEquals(expectedString, actualString)
                    }
                    AggregatorQueryType.MIN -> assertFailsWith<TimeoutCancellationException> {
                        withTimeout(100) { channel.receiveOrFail() }
                    }
                }
                else -> assertFailsWith<TimeoutCancellationException> {
                    withTimeout(100) { channel.receiveOrFail() }
                }
            }

            // Now change the values again to trigger an update
            realm.writeBlocking {
                delete(query<QuerySample>())
            }

            val finalAggregatedValue = channel.receiveOrFail()
            when (type) {
                AggregatorQueryType.SUM -> when (finalAggregatedValue) {
                    is Number -> assertEquals(0, finalAggregatedValue.toInt())
                    is Char -> assertEquals(0, finalAggregatedValue.code)
                    is Decimal128 -> assertEquals(Decimal128("0"), finalAggregatedValue)
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
        val channel = TestChannel<Any?>()
        val expectedAggregate = when (type) {
            AggregatorQueryType.MIN -> expectedMin(propertyDescriptor.clazz)
            AggregatorQueryType.MAX -> expectedMax(propertyDescriptor.clazz)
            AggregatorQueryType.SUM -> expectedSum(propertyDescriptor.clazz)
        }

        runBlocking {
            realm.writeBlocking {
                saveData(propertyDescriptor)
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

            val receivedAggregate = channel.receiveOrFail()
            when (propertyDescriptor.clazz) {
                RealmAny::class -> when (type) {
                    AggregatorQueryType.MIN -> {
                        val actualValue = (receivedAggregate as RealmAny)
                            .asBoolean()
                        val expectedValue = (expectedAggregate as RealmAny)
                            .asBoolean()
                        assertEquals(expectedValue, actualValue)
                    }
                    AggregatorQueryType.MAX -> {
                        val actualValue = (receivedAggregate as RealmAny)
                            .asRealmObject<QuerySample>()
                            .stringField
                        val expectedValue = (expectedAggregate as RealmAny)
                            .asRealmObject<QuerySample>()
                            .stringField
                        assertEquals(expectedValue, actualValue)
                    }
                    AggregatorQueryType.SUM ->
                        assertEquals(expectedAggregate, receivedAggregate)
                }
                else -> when (type) {
                    AggregatorQueryType.MIN -> assertEquals(expectedAggregate, receivedAggregate)
                    AggregatorQueryType.MAX -> assertEquals(expectedAggregate, receivedAggregate)
                    AggregatorQueryType.SUM -> when (receivedAggregate) {
                        is Number -> assertEquals(expectedAggregate, receivedAggregate)
                        is Char -> assertEquals(expectedAggregate, receivedAggregate.code)
                        is Decimal128 -> assertEquals(expectedAggregate, receivedAggregate)
                    }
                }
            }

            // Now delete objects to trigger a new emission
            realm.write {
                delete(query<QuerySample>())
            }

            val aggregatedValue = channel.receiveOrFail()
            when (type) {
                AggregatorQueryType.SUM -> when (aggregatedValue) {
                    is Number -> assertEquals(0, aggregatedValue.toInt())
                    is Char -> assertEquals(0, aggregatedValue.code)
                    is Decimal128 -> assertEquals(Decimal128("0"), aggregatedValue)
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
        val channel1 = TestChannel<Any?>()
        val channel2 = TestChannel<Any?>()

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

            val initialAggregate1 = channel1.receiveOrFail()
            val initialAggregate2 = channel2.receiveOrFail()
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
                    is Decimal128 -> {
                        assertEquals(Decimal128("0"), initialAggregate1)
                        assertEquals(Decimal128("0"), initialAggregate2)
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
            val receivedAggregate = channel2.receiveOrFail()
            if (propertyDescriptor.clazz == RealmAny::class) {
                when (type) {
                    AggregatorQueryType.SUM -> {
                        val expectedRealmAnyValue =
                            (propertyDescriptor.values as List<RealmAny?>)[0]
                        if (expectedRealmAnyValue?.type != RealmAny.Type.INT) {
                            throw IllegalArgumentException("Wrong RealmAny value to compare. Expected 'INT' but was '${expectedRealmAnyValue?.type}'.")
                        }
                        val asLong = expectedRealmAnyValue.asLong()
                        assertEquals(Decimal128(asLong.toString()), receivedAggregate)
                    }
                    else -> {
                        val expectedRealmAnyValue =
                            (propertyDescriptor.values as List<RealmAny?>)[0]
                        if (expectedRealmAnyValue?.type != RealmAny.Type.INT) {
                            throw IllegalArgumentException("Wrong RealmAny value to compare. Expected 'INT' but was '${expectedRealmAnyValue?.type}'.")
                        }
                        assertEquals(expectedRealmAnyValue, receivedAggregate)
                    }
                }
            } else {
                val expectedAggregate = propertyDescriptor.values[0]
                assertEquals(expectedAggregate, receivedAggregate)
            }
            assertTrue(channel1.isEmpty)

            observer2.cancel()
            channel1.close()
            channel2.close()

            // Make sure to delete all objects after assertions as aggregators to clean state and
            // avoid "null vs 0" results when testing
            cleanUpBetweenProperties()
        }
    }

    @Test
    fun asFlow_results_withKeyPath() {
        val channel = Channel<ResultsChange<QuerySample>>(1)
        runBlocking {
            val observer = async {
                realm.query<QuerySample>()
                    .asFlow(listOf("stringField"))
                    .collect { results ->
                        assertNotNull(results)
                        channel.send(results)
                    }
            }
            channel.receiveOrFail().let { resultsChange ->
                assertIs<InitialResults<*>>(resultsChange)
                assertTrue(resultsChange.list.isEmpty())
            }
            val obj = realm.writeBlocking {
                copyToRealm(QuerySample())
            }
            channel.receiveOrFail().let { resultsChange ->
                assertIs<UpdatedResults<*>>(resultsChange)
                assertEquals(1, resultsChange.list.size)
            }
            realm.writeBlocking {
                // Should not trigger notification
                findLatest(obj)!!.intField = 42
            }
            realm.writeBlocking {
                // Should trigger notification
                findLatest(obj)!!.stringField = "update"
            }
            channel.receiveOrFail().let { resultsChange ->
                assertIs<UpdatedResults<*>>(resultsChange)
                assertEquals(1, resultsChange.list.size)
                assertEquals("update", resultsChange.list.first().stringField)
            }
            observer.cancel()
            channel.close()
        }
    }

    @Test
    fun asFlow_objectBound_withKeyPath() {
        val channel = Channel<SingleQueryChange<QuerySample>>(1)
        runBlocking {
            val observer = async {
                realm.query<QuerySample>()
                    .first()
                    .asFlow(listOf("stringField"))
                    .collect { change ->
                        assertNotNull(change)
                        channel.send(change)
                    }
            }
            channel.receiveOrFail().let { objChange ->
                assertIs<PendingObject<*>>(objChange)
            }
            val obj = realm.writeBlocking {
                copyToRealm(QuerySample())
            }
            channel.receiveOrFail().let { objChange ->
                assertIs<InitialObject<*>>(objChange)
            }
            realm.writeBlocking {
                // Should not trigger notification
                findLatest(obj)!!.intField = 42
            }
            realm.writeBlocking {
                // Should trigger notification
                findLatest(obj)!!.stringField = "update"
            }
            channel.receiveOrFail().let { objChange ->
                assertIs<UpdatedObject<*>>(objChange)
                assertEquals(1, objChange.changedFields.size)
                assertEquals("stringField", objChange.changedFields.first())
            }
            observer.cancel()
            channel.close()
        }
    }

    // Smoke-test for wildcards.
    @Test
    fun keyPath_usingWildCards() = runBlocking<Unit> {
        val channel = Channel<ResultsChange<QuerySample>>(1)
        val observer = async {
            realm.query<QuerySample>("stringField = 'parent'")
                // Should match what the notifier is doing by default
                .asFlow(listOf("*.*.*.*"))
                .collect { results ->
                    assertNotNull(results)
                    channel.send(results)
                }
        }
        channel.receiveOrFail().let { resultsChange ->
            assertIs<InitialResults<*>>(resultsChange)
            assertTrue(resultsChange.list.isEmpty())
        }
        val obj = realm.write {
            copyToRealm(
                QuerySample().apply {
                    stringField = "parent"
                    nullableRealmObject = QuerySample().apply {
                        stringField = "child"
                    }
                }
            )
        }
        channel.receiveOrFail().let { resultsChange ->
            assertIs<UpdatedResults<*>>(resultsChange)
            assertEquals(1, resultsChange.list.size)
        }
        realm.write {
            findLatest(obj)!!.intField = 42
        }
        channel.receiveOrFail().let { resultsChange ->
            assertIs<UpdatedResults<*>>(resultsChange)
            assertEquals(1, resultsChange.list.size)
            assertEquals(42, resultsChange.list.first().intField)
        }
        realm.write {
            findLatest(obj)!!.nullableRealmObject!!.stringField = "update"
        }
        channel.receiveOrFail().let { resultsChange ->
            assertIs<UpdatedResults<*>>(resultsChange)
            assertEquals(1, resultsChange.list.size)
            assertEquals("update", resultsChange.list.first().nullableRealmObject!!.stringField)
        }
        observer.cancel()
        channel.close()
    }

    // ----------------
    // KNN search
    // ----------------
    @Test
    fun knn_search() {
        val realm = Realm.open(RealmConfiguration.create(setOf(VectorEmbeddingSample::class)))
        realm.writeBlocking {
            copyToRealm(VectorEmbeddingSample().apply {
                stringField = "vector 1"
                embedding.addAll(
                    listOf(
                        0.003f, 0.004f, 0.005f, 0.100f, 0.010f
                    )
                )
            })

            copyToRealm(VectorEmbeddingSample().apply {
                stringField = "vector 2"
                embedding.addAll(
                    listOf(
                        0.001f, 0.004f, 0.005f, 0.100f, 0.010f
                    )
                )
            })

            copyToRealm(VectorEmbeddingSample().apply {
                stringField = "vector 3"
                embedding.addAll(
                    listOf(
                        0.001f, 0.004f, 0.005f, 0.100f, 0.010f
                    )
                )
            })

            copyToRealm(VectorEmbeddingSample().apply {
                stringField = "vector 4"
                embedding.addAll(
                    listOf(
                        0.004f, 0.005f, 0.010f, 0.025f, 0.100f
                    )
                )
            })

            copyToRealm(VectorEmbeddingSample().apply {
                stringField = "vector 5"
                embedding.addAll(
                    listOf(
                        0.003f, 0.007f, 0.008f, 0.020f, 0.100f
                    )
                )
            })
        }
        val knn: RealmResults<VectorEmbeddingSample> = realm.query<VectorEmbeddingSample>()
            .knn("embedding", arrayOf(0.003f, 0.005f, 0.010f, 0.020f, 0.100f), 2)
        assertEquals(2, knn.size)
        assertEquals("vector 4", knn[0].stringField)
        assertEquals("vector 5", knn[1].stringField)
    }


    // ----------------
    // Coercion helpers
    // ----------------

    private fun <T : Any> sum_find_numericPropertiesCoercedCorrectly(
        property: KMutableProperty1<QuerySample, T>
    ) {
        val sum = 3
        realm.writeBlocking {
            for (i in 1..3) {
                val sample = QuerySample().apply {
                    intField = 1
                    floatField = 1F
                    doubleField = 1.0
                }
                copyToRealm(sample)
            }
        }

        // Long coercion
        realm.query<QuerySample>()
            .sum<Long>(property.name)
            .find {
                assertEquals(sum.toLong(), assertNotNull(it))
            }

        // Float coercion
        realm.query<QuerySample>()
            .sum<Float>(property.name)
            .find {
                assertEquals(sum.toFloat(), assertNotNull(it))
            }

        // Double coercion
        realm.query<QuerySample>()
            .sum<Double>(property.name)
            .find {
                assertEquals(sum.toDouble(), assertNotNull(it))
            }
    }

    private fun <T : Any> max_find_numericPropertiesCoercedCorrectly(
        property: KMutableProperty1<QuerySample, T>
    ) {
        val max = 3
        realm.writeBlocking {
            for (i in 1..max) {
                val sample = QuerySample().apply {
                    intField = i
                    floatField = i.toFloat()
                    doubleField = i.toDouble()
                }
                copyToRealm(sample)
            }
        }

        // Long coercion
        realm.query<QuerySample>()
            .max<Long>(property.name)
            .find {
                assertEquals(max.toLong(), assertNotNull(it))
            }

        // Float coercion
        realm.query<QuerySample>()
            .max<Float>(property.name)
            .find {
                assertEquals(max.toFloat(), assertNotNull(it))
            }

        // Double coercion
        realm.query<QuerySample>()
            .max<Double>(property.name)
            .find {
                assertEquals(max.toDouble(), assertNotNull(it))
            }
    }

    private fun <T : Any> min_find_numericPropertiesCoercedCorrectly(
        property: KMutableProperty1<QuerySample, T>
    ) {
        val min = 1
        realm.writeBlocking {
            for (i in min..3) {
                val sample = QuerySample().apply {
                    intField = i
                    floatField = i.toFloat()
                    doubleField = i.toDouble()
                }
                copyToRealm(sample)
            }
        }

        // Long coercion
        realm.query<QuerySample>()
            .min<Long>(property.name)
            .find {
                assertEquals(min.toLong(), assertNotNull(it))
            }

        // Float coercion
        realm.query<QuerySample>()
            .min<Float>(property.name)
            .find {
                assertEquals(min.toFloat(), assertNotNull(it))
            }

        // Double coercion
        realm.query<QuerySample>()
            .min<Double>(property.name)
            .find {
                assertEquals(min.toDouble(), assertNotNull(it))
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
        Decimal128::class -> PropertyDescriptor(
            if (isNullable) QuerySample::nullableDecimal128Field else QuerySample::decimal128Field,
            Decimal128::class,
            if (isNullable) NULLABLE_DECIMAL128_VALUES else DECIMAL128_VALUES
        )
        RealmInstant::class -> PropertyDescriptor(
            if (isNullable) QuerySample::nullableTimestampField else QuerySample::timestampField,
            RealmInstant::class,
            if (isNullable) NULLABLE_TIMESTAMP_VALUES else TIMESTAMP_VALUES
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
        RealmAny::class -> PropertyDescriptor(
            QuerySample::realmAnyField,
            RealmAny::class,
            QUERY_REALM_ANY_VALUES
        )
        else -> throw IllegalArgumentException("Invalid type descriptor: $classifier")
    }

    private val supportedSumTypes = TypeDescriptor.classifiers.filter { (_, coreFieldType) -> coreFieldType.aggregatorSupport.contains(TypeDescriptor.AggregatorSupport.SUM) }.keys
    private val propertyDescriptorForSum = supportedSumTypes.map { getDescriptor(it, false) }
    private val nullablePropertyDescriptorsForSum = supportedSumTypes.map { getDescriptor(it, true) }
    private val allPropertyDescriptorsForSum: List<PropertyDescriptor> = propertyDescriptorForSum + nullablePropertyDescriptorsForSum

    private val supportedMinTypes = TypeDescriptor.classifiers.filter { (_, coreFieldType) -> coreFieldType.aggregatorSupport.contains(TypeDescriptor.AggregatorSupport.MIN) }.keys
    private val propertyDescriptorForMin = supportedMinTypes.map { getDescriptor(it, false) }
    private val nullablePropertyDescriptorsForMin = supportedMinTypes.map { getDescriptor(it, true) }
    private val allPropertyDescriptorsForMin: List<PropertyDescriptor> = propertyDescriptorForMin + nullablePropertyDescriptorsForMin

    private val supportedMaxTypes = TypeDescriptor.classifiers.filter { (_, coreFieldType) -> coreFieldType.aggregatorSupport.contains(TypeDescriptor.AggregatorSupport.MIN) }.keys
    private val propertyDescriptorForMax = supportedMaxTypes.map { getDescriptor(it, false) }
    private val nullablePropertyDescriptorsForMax = supportedMaxTypes.map { getDescriptor(it, true) }
    private val allPropertyDescriptorsForMax: List<PropertyDescriptor> = propertyDescriptorForMax + nullablePropertyDescriptorsForMax

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
private data class PropertyDescriptor constructor(
    val property: KMutableProperty1<QuerySample, *>,
    val clazz: KClass<*>,
    val values: List<Any?>
)

class VectorEmbeddingSample : RealmObject {
    var stringField: String = ""
    var embedding: RealmList<Float> = realmListOf()
}
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

    @PersistedName("persistedNameStringField")
    var publicNameStringField: String = "Realm"

    @FullText
    var fulltextField: String = "A very long string"

    var stringField: String = "Realm"
    var byteField: Byte = 0
    var charField: Char = 'a'
    var shortField: Short = 0
    var intField: Int = 0
    var longField: Long = 0
    var booleanField: Boolean = true
    var floatField: Float = 0F
    var doubleField: Double = 0.0
    var decimal128Field: Decimal128 = Decimal128("1.84467440737095E-6157")
    var timestampField: RealmInstant = RealmInstant.from(100, 1000)
    var objectIdField: ObjectId = ObjectId.from("507f191e810c19729de860ea")
    var bsonObjectIdField: BsonObjectId = BsonObjectId("507f191e810c19729de860ea")
    var uuidField: RealmUUID = RealmUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d76")
    var binaryField: ByteArray = byteArrayOf(42)
    var realmAnyField: RealmAny? = RealmAny.create("Hello")

    var nullableStringField: String? = null
    var nullableByteField: Byte? = null
    var nullableCharField: Char? = null
    var nullableShortField: Short? = null
    var nullableIntField: Int? = null
    var nullableLongField: Long? = null
    var nullableBooleanField: Boolean? = null
    var nullableFloatField: Float? = null
    var nullableDoubleField: Double? = null
    var nullableDecimal128Field: Decimal128? = null
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
    var decimal128ListField: RealmList<Decimal128> = realmListOf()
    var timestampListField: RealmList<RealmInstant> = realmListOf()
    var objectIdListField: RealmList<ObjectId> = realmListOf()
    var bsonObjectIdListField: RealmList<BsonObjectId> = realmListOf()
    var binaryListField: RealmList<ByteArray> = realmListOf()
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
    var nullableDecimal128ListField: RealmList<Decimal128?> = realmListOf()
    var nullableTimestampListField: RealmList<RealmInstant?> = realmListOf()
    var nullableObjectIdListField: RealmList<ObjectId?> = realmListOf()
    var nullableBsonObjectIdListField: RealmList<BsonObjectId?> = realmListOf()
    var nullableBinaryListField: RealmList<ByteArray?> = realmListOf()

    var stringSetField: RealmSet<String> = realmSetOf()
    var byteSetField: RealmSet<Byte> = realmSetOf()
    var charSetField: RealmSet<Char> = realmSetOf()
    var shortSetField: RealmSet<Short> = realmSetOf()
    var intSetField: RealmSet<Int> = realmSetOf()
    var longSetField: RealmSet<Long> = realmSetOf()
    var booleanSetField: RealmSet<Boolean> = realmSetOf()
    var floatSetField: RealmSet<Float> = realmSetOf()
    var doubleSetField: RealmSet<Double> = realmSetOf()
    var decimal128SetField: RealmSet<Decimal128> = realmSetOf()
    var timestampSetField: RealmSet<RealmInstant> = realmSetOf()
    var objectIdSetField: RealmSet<ObjectId> = realmSetOf()
    var bsonObjectIdSetField: RealmSet<BsonObjectId> = realmSetOf()
    var binarySetField: RealmSet<ByteArray> = realmSetOf()
    var objectSetField: RealmSet<QuerySample> = realmSetOf()

    var nullableStringSetField: RealmSet<String?> = realmSetOf()
    var nullableByteSetField: RealmSet<Byte?> = realmSetOf()
    var nullableCharSetField: RealmSet<Char?> = realmSetOf()
    var nullableShortSetField: RealmSet<Short?> = realmSetOf()
    var nullableIntSetField: RealmSet<Int?> = realmSetOf()
    var nullableLongSetField: RealmSet<Long?> = realmSetOf()
    var nullableBooleanSetField: RealmSet<Boolean?> = realmSetOf()
    var nullableFloatSetField: RealmSet<Float?> = realmSetOf()
    var nullableDoubleSetField: RealmSet<Double?> = realmSetOf()
    var nullableDecimal128SetField: RealmSet<Decimal128?> = realmSetOf()
    var nullableTimestampSetField: RealmSet<RealmInstant?> = realmSetOf()
    var nullableObjectIdSetField: RealmSet<ObjectId?> = realmSetOf()
    var nullableBsonObjectIdSetField: RealmSet<BsonObjectId?> = realmSetOf()
    var nullableBinarySetField: RealmSet<ByteArray?> = realmSetOf()

    var stringDictionaryField: RealmDictionary<String> = realmDictionaryOf()
    var byteDictionaryField: RealmDictionary<Byte> = realmDictionaryOf()
    var charDictionaryField: RealmDictionary<Char> = realmDictionaryOf()
    var shortDictionaryField: RealmDictionary<Short> = realmDictionaryOf()
    var intDictionaryField: RealmDictionary<Int> = realmDictionaryOf()
    var longDictionaryField: RealmDictionary<Long> = realmDictionaryOf()
    var booleanDictionaryField: RealmDictionary<Boolean> = realmDictionaryOf()
    var floatDictionaryField: RealmDictionary<Float> = realmDictionaryOf()
    var doubleDictionaryField: RealmDictionary<Double> = realmDictionaryOf()
    var decimal128DictionaryField: RealmDictionary<Decimal128> = realmDictionaryOf()
    var timestampDictionaryField: RealmDictionary<RealmInstant> = realmDictionaryOf()
    var objectIdDictionaryField: RealmDictionary<ObjectId> = realmDictionaryOf()
    var bsonObjectIdDictionaryField: RealmDictionary<BsonObjectId> = realmDictionaryOf()
    var binaryDictionaryField: RealmDictionary<ByteArray> = realmDictionaryOf()

    var nullableStringDictionaryField: RealmDictionary<String?> = realmDictionaryOf()
    var nullableByteDictionaryField: RealmDictionary<Byte?> = realmDictionaryOf()
    var nullableCharDictionaryField: RealmDictionary<Char?> = realmDictionaryOf()
    var nullableShortDictionaryField: RealmDictionary<Short?> = realmDictionaryOf()
    var nullableIntDictionaryField: RealmDictionary<Int?> = realmDictionaryOf()
    var nullableLongDictionaryField: RealmDictionary<Long?> = realmDictionaryOf()
    var nullableBooleanDictionaryField: RealmDictionary<Boolean?> = realmDictionaryOf()
    var nullableFloatDictionaryField: RealmDictionary<Float?> = realmDictionaryOf()
    var nullableDoubleDictionaryField: RealmDictionary<Double?> = realmDictionaryOf()
    var nullableDecimal128DictionaryField: RealmDictionary<Decimal128?> = realmDictionaryOf()
    var nullableTimestampDictionaryField: RealmDictionary<RealmInstant?> = realmDictionaryOf()
    var nullableObjectIdDictionaryField: RealmDictionary<ObjectId?> = realmDictionaryOf()
    var nullableBsonObjectIdDictionaryField: RealmDictionary<BsonObjectId?> = realmDictionaryOf()
    var nullableBinaryDictionaryField: RealmDictionary<ByteArray?> = realmDictionaryOf()
    var nullableObjectDictionaryField: RealmDictionary<QuerySample?> = realmDictionaryOf()

    var child: QuerySample? = null
}

internal val QUERY_REALM_ANY_OBJECT = RealmAny.create(
    QuerySample().apply { stringField = "foo" },
    QuerySample::class
)

// Use this for QUERY tests as this file does exhaustive testing on all RealmAny types
internal val QUERY_REALM_ANY_VALUES = REALM_ANY_PRIMITIVE_VALUES + QUERY_REALM_ANY_OBJECT

internal val REALM_ANY_SUM = Decimal128("82") // sum of numerics present in PRIMITIVE_REALM_ANY_VALUES = -12+13+14+15+16L+17F+18.0+Decimal128("1")
internal val REALM_ANY_MIN = RealmAny.create(false) // Boolean is the "lowest" type when comparing Mixed types
internal val REALM_ANY_MAX = QUERY_REALM_ANY_OBJECT // RealmObject is "highest" when comparing Mixed types
