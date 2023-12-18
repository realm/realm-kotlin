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

@file:OptIn(ExperimentalKBsonSerializerApi::class)

package io.realm.kotlin.test.mongodb.common.mongo

import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.mongodb.mongo.MongoClient
import io.realm.kotlin.mongodb.mongo.MongoCollection
import io.realm.kotlin.mongodb.mongo.MongoDatabase
import io.realm.kotlin.mongodb.mongo.aggregate
import io.realm.kotlin.mongodb.mongo.collection
import io.realm.kotlin.mongodb.mongo.count
import io.realm.kotlin.mongodb.mongo.deleteMany
import io.realm.kotlin.mongodb.mongo.deleteOne
import io.realm.kotlin.mongodb.mongo.find
import io.realm.kotlin.mongodb.mongo.findOne
import io.realm.kotlin.mongodb.mongo.findOneAndDelete
import io.realm.kotlin.mongodb.mongo.findOneAndReplace
import io.realm.kotlin.mongodb.mongo.findOneAndUpdate
import io.realm.kotlin.mongodb.mongo.insertMany
import io.realm.kotlin.mongodb.mongo.insertOne
import io.realm.kotlin.mongodb.mongo.updateMany
import io.realm.kotlin.mongodb.mongo.updateOne
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PersistedName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonInt32
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.ObjectId
import org.mongodb.kbson.serialization.EJson
import org.mongodb.kbson.serialization.encodeToBsonValue
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MongoCollectionFromDatabaseTests : MongoCollectionTests() {

    lateinit var database: MongoDatabase

    @BeforeTest
    override fun setUp() {
        super.setUp()
        val databaseName = app.configuration.appId
        database = client.database(databaseName)
        collection = collection(CollectionDataType::class)
    }

    @Test
    override fun findOne_unknownCollection() = runBlocking<Unit> {
        // Unknown collections will create the collection
        val unknownCollection = collection<NonSchemaType, BsonValue>(NonSchemaType::class)
        assertNull(unknownCollection.findOne())
    }
}
class MongoCollectionFromClientTests : MongoCollectionTests() {

    @BeforeTest
    override fun setUp() {
        super.setUp()
        collection = collection(CollectionDataType::class)
    }

    @Test
    fun name_persistedName() {
        assertEquals("CollectionDataType", client.collection<CustomDataType, ObjectId>().name)
    }

    @Test
    override fun findOne_unknownCollection() = runBlocking<Unit> {
        val unknownCollection = collection<NonSchemaType, BsonValue>(NonSchemaType::class)
        assertFailsWithMessage<ServiceException>("no matching collection found that maps to a table with title \"Unknown\"") {
            RealmLog.level = LogLevel.ALL
            unknownCollection.findOne()
        }
    }
}

abstract sealed class MongoCollectionTests {

    lateinit var app: TestApp
    lateinit var client: MongoClient
    lateinit var collection: MongoCollection<CollectionDataType, Int>

    @BeforeTest
    open fun setUp() {
        app = TestApp(
            this::class.simpleName,
            builder = { builder: AppConfiguration.Builder ->
                builder.httpLogObfuscator(null)
            }
        )

        app.asTestApp.run {
            runBlocking {
                deleteDocuments(app.configuration.appId, "CollectionDataType", "{}")
            }
        }
        val user = app.createUserAndLogin()
        client = user.mongoClient(TEST_SERVICE_NAME)
    }

    @AfterTest
    fun teadDown() {
        RealmLog.level = LogLevel.WARN
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun name() {
        assertEquals("CollectionDataType", collection.name)
    }

    @Test
    open fun reshape() = runBlocking<Unit> {
        // Original typing
        assertIs<Int>(collection.insertOne(CollectionDataType("object-1", Random.nextInt())))
        assertIs<CollectionDataType>(collection.findOne())

        // Reshaped
        val bsonCollection: MongoCollection<BsonDocument, BsonValue> = collection.reshape()
        assertIs<BsonValue>(bsonCollection.insertOne(BsonDocument("name", "object-2")))
        assertIs<BsonDocument>(bsonCollection.findOne())
        assertEquals(2, bsonCollection.count())
    }

    @Test
    fun reshape_withCustomSerialization() = runBlocking<Unit> {
        val reshapedCollectionWithDefaultSerializer: MongoCollection<CustomDataType, CustomIdType> =
            collection.reshape()

        assertFailsWithMessage<SerializationException>("Serializer for class 'CustomDataType' is not found.") {
            reshapedCollectionWithDefaultSerializer.insertOne(CustomDataType("object-2"))
        }

        val reshapedCollectionWithCustomSerializer: MongoCollection<CustomDataType, CustomIdType> =
            collection.reshape(customEjsonSerializer)

        assertIs<CustomIdType>(reshapedCollectionWithCustomSerializer.insertOne(CustomDataType("object-2")))
    }

    @Test
    fun count() = runBlocking<Unit> {
        assertEquals(0, collection.count())

        collection.insertMany((1..10).map { CollectionDataType("object-${it % 5}") })

        assertEquals(10, collection.count())
        assertEquals(5, collection.count(limit = 5))
        assertEquals(2, collection.count(filter = BsonDocument("name" to BsonString("object-0"))))
        assertEquals(2, collection.count(filter = BsonDocument("name", "object-0")))
        assertEquals(
            1,
            collection.count(filter = BsonDocument("name" to BsonString("object-0")), limit = 1)
        )
    }

    @Test
    fun count_invalidFilter() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("operator") {
            collection.count(filter = BsonDocument("\$who", "object-0"))
        }
    }

    @Test
    open fun findOne() = runBlocking<Unit> {
        RealmLog.level = LogLevel.ALL
        // Empty collections
        assertNull(collection.findOne())

        collection.insertMany((1..10).map { CollectionDataType("object-${it % 5}") })

        // No match
        assertNull(collection.findOne(filter = BsonDocument("name", "cat")))

        // Multiple matches, still only one document
        // Default types
        collection.findOne(filter = BsonDocument("name", "object-0")).run {
            assertIs<CollectionDataType>(this)
            assertEquals("object-0", this.name)
        }

        // Projection
        val collectionDataType = CollectionDataType("object-6")
        assertEquals(collectionDataType._id, collection.insertOne(collectionDataType))

        collection.findOne(filter = BsonDocument("name", "object-6")).run {
            assertIs<CollectionDataType>(this)
            assertEquals("object-6", this.name)
            assertEquals(collectionDataType._id, this._id)
        }

        // Project without name
        collection.findOne(
            filter = BsonDocument("name", "object-6"),
            projection = BsonDocument("""{ "name" : 0}""")
        ).run {
            assertIs<CollectionDataType>(this)
            // FIXME Should be "Default" but serialization fails if field is non-nullable even though descriptor correctly has isOptional=true
            // assertEquals("Default", this.name)
            assertNull(this.name) // Currently null because of nullability, but should have default value "Default"
            // _id is included by default
            assertEquals(collectionDataType._id, this._id)
        }
        // Project without _id, have to be explicitly excluded
        collection.findOne(
            filter = BsonDocument("name", "object-6"),
            projection = BsonDocument("""{ "_id" : 0}""")
        ).run {
            assertIs<CollectionDataType>(this)
            assertEquals(collectionDataType.name, name)
            // FIXME Should be "Default" but serialization fails if field is non-nullable even though descriptor correctly has isOptional=true
            // _id is included by default
            assertNotEquals(collectionDataType._id, this._id)
        }

        // Sort
        collection.findOne(sort = BsonDocument(mapOf("name" to BsonInt32(-1)))).run {
            assertIs<CollectionDataType>(this)
            assertEquals("object-6", this.name)
        }
        collection.findOne(sort = BsonDocument(mapOf("name" to BsonInt32(1)))).run {
            assertIs<CollectionDataType>(this)
            assertEquals("object-0", this.name)
        }
    }

    @Test
    fun findOne_explicitTypes() = runBlocking {
        // Empty collection
        assertNull(collection.findOne<BsonValue>())

        collection.insertMany((1..10).map { CollectionDataType("object-${it % 5}") })

        // Explicit types
        collection.findOne<BsonDocument>(filter = BsonDocument("name", "object-0")).run {
            assertIs<BsonDocument>(this)
            assertEquals("object-0", this["name"]!!.asString().value)
        }
    }

    @Test
    fun findOne_extraFieldsAreDiscarded() { TODO() }

    @Test
    fun findOne_missingFieldsGetsDefaults() { TODO() }

    @Test
    abstract fun findOne_unknownCollection()

    @Test
    fun findOne_fails() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("operator") {
            collection.findOne(BsonDocument("\$who", 1))
        }
    }

    @Test
    fun find() = runBlocking<Unit> {
        assertTrue { collection.find().isEmpty() }

        val names = (1..10).map { "object-${it % 5}" }
        val ids: List<Int> = collection.insertMany(names.map { CollectionDataType(it) })

        assertEquals(10, collection.find().size)

        collection.find(filter = BsonDocument("name", "object-1")).let {
            assertEquals(2, it.size)
        }

        // Limit
        collection.find(
            limit = 5,
        ).let {
            assertEquals(5, it.size)
        }

        // Projection
        collection.find(
            filter = BsonDocument("name", "object-1"),
            projection = BsonDocument("""{ "name" : 0}"""),
        ).let {
            assertEquals(2, it.size)
            it.forEach {
                // FIXME Should be "Default" but serialization fails if field is non-nullable even though descriptor correctly has isOptional=true
                // assertEquals("Default", it.name)
                assertNull(it.name)
                // _id is included by default
                assertTrue(it._id in ids)
            }
        }
        collection.find(
            filter = BsonDocument("name", "object-1"),
            projection = BsonDocument("""{ "_id" : 0}"""),
        ).let {
            assertEquals(2, it.size)
            it.forEach {
                assertNotNull(it.name)
                assertNotEquals("Default", it.name)
                // Objects have new ids
                // FIXME Should be assigned new Objects ids, but serialization fails if field is non-nullable even though descriptor correctly has isOptional=true
                // assertFalse(it._id in ids)
                assertNull(it._id)
            }
        }

        // Sort
        collection.find(sort = BsonDocument("""{ "name" : -1}""")).let { results ->
            assertEquals(names.sorted().reversed(), results.map { it.name })
        }
        collection.find(sort = BsonDocument("""{ "name" : 1}""")).let { results ->
            assertEquals(names.sorted(), results.map { it.name })
        }
    }

    @Test
    fun find_explicitTypes() = runBlocking<Unit> {
        collection.find<BsonDocument>().let { assertTrue { it.isEmpty() } }

        val names = (1..10).map { "object-${it % 5}" }
        collection.insertMany<BsonDocument, BsonValue>(names.map { BsonDocument("name", it) })

        collection.find<BsonDocument>().let { results ->
            results.forEach {
                assertIs<BsonDocument>(it)
                assertTrue { it.asDocument()["name"]!!.asString().value in names }
            }
        }
    }

    @Test
    fun find_fails() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("unknown top level operator: \$who.") {
            collection.find<BsonDocument>(BsonDocument("\$who", 1)).first()
        }
    }

    @Test
    fun aggregate() = runBlocking<Unit> {
        collection.aggregate(listOf()).let { assertTrue { it.isEmpty() } }

        val names = (1..10).map { "object-${it % 5}" }
        val ids: List<Int> = collection.insertMany(names.map { CollectionDataType(it) })

        collection.aggregate<CollectionDataType>(listOf()).let {
            assertEquals(10, it.size)
            it.forEach {
                assertTrue { it.name in names }
            }
        }

        collection.aggregate<CollectionDataType>(listOf(BsonDocument("\$sort", BsonDocument("name", -1)), BsonDocument("\$limit", 2))).let {
            assertEquals(2, it.size)
            it.forEach {
                assertEquals("object-4", it.name)
            }
        }
    }

    @Test
    fun aggregate_fails() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("Unrecognized pipeline stage name: '\$who'.") {
            collection.aggregate(pipeline = listOf(BsonDocument("\$who", 1)))
        }
    }

    @Test
    fun insertOne() = runBlocking<Unit> {
        assertEquals(0, collection.find().size)

        collection.insertOne(CollectionDataType("object-1")).let {
            assertIs<Int>(it)
        }
        assertEquals(1, collection.find().size)
    }

    @Test
    fun insertOne_explicitTypes() = runBlocking<Unit> {
        assertEquals(0, collection.find().size)
        // Inserting document without _id will use ObjectId as _id
        collection.insertOne<BsonDocument, BsonValue>(BsonDocument("name", "object-1")).let {
            assertIs<ObjectId>(it)
        }
        // Inserted document will have ObjectId key and cannot be serialized into CollectionDataType
        // so find must also  use BsonDocument
        assertEquals(1, collection.find<BsonDocument>().size)
    }

    @Test
    fun insertOne_throwsOnExistingPrimaryKey() = runBlocking {
        assertEquals(0, collection.find().size)

        val document = CollectionDataType("object-1")
        collection.insertOne(document).let {
            assertIs<Int>(it)
        }
        assertFailsWithMessage<ServiceException>("Duplicate key error") {
            collection.insertOne(document)
        }
        assertEquals(1, collection.find().size)
    }

    @Test
    fun insertOne_throwsOnMissingRequiredFields() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("insert not permitted") {
            collection.insertOne<BsonDocument, BsonValue>(BsonDocument())
        }
    }

    @Test
    fun insertOne_throwsOnTypeMismatch() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("insert not permitted") {
            collection.insertOne<BsonDocument, ObjectId>(BsonDocument(mapOf("_id" to ObjectId(), "name" to BsonString("object-1"))))
        }
    }
    @Test
    fun insertMany() = runBlocking {
        assertEquals(0, collection.find().size)

        collection.insertMany((1..10).map { CollectionDataType("object-${it % 5}") }).let { ids ->
            assertEquals(10, ids.size)
            ids.forEach { id ->
                assertIs<Int>(id)
            }
        }

        assertEquals(10, collection.find().size)
    }

    @Test
    fun insertMany_explictTyped() = runBlocking<Unit> {
        assertEquals(0, collection.find().size)

        collection.insertMany<BsonDocument, BsonValue>((1..10).map { BsonDocument("name", "object-${it % 5}") }).let {
            assertEquals(10, it.size)
            it.forEach {
                assertIs<ObjectId>(it)
            }
        }
        assertEquals(10, collection.find<BsonDocument>().size)
    }

    @Test
    fun insertMany_throwsOnEmptyList() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("must provide at least one element") {
            collection.insertMany(emptyList())
        }
    }

    @Test
    fun insertMany_throwsOnExistingPrimaryKey() = runBlocking {
        assertEquals(0, collection.find().size)

        val document = CollectionDataType("object-1")
        assertFailsWithMessage<ServiceException>("Duplicate key error") {
            collection.insertMany(listOf(document.apply { name = "sadf" }, document))
        }
        // Above call will throw an error, but we have actually inserted one document
        assertEquals(1, collection.find().size)
    }

    @Test
    fun insertMany_throwsOnMissingRequiredFields() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("insert not permitted") {
            collection.insertOne<BsonDocument, BsonValue>(BsonDocument())
        }
    }

    @Test
    fun insertMany_throwsOnTypeMismatch() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("insert not permitted") {
            collection.insertOne<BsonDocument, ObjectId>(BsonDocument(mapOf("_id" to ObjectId(), "name" to BsonString("object-1"))))
        }
    }

    @Test
    fun deleteOne() = runBlocking {
        // Argument wrapper DSL
        RealmLog.level = LogLevel.ALL
        assertFalse { collection.deleteOne(BsonDocument()) }

        assertEquals(
            2,
            collection.insertMany<CollectionDataType, BsonValue>(
                listOf(
                    CollectionDataType("object-1"),
                    CollectionDataType("object-1")
                )
            ).size
        )
        assertEquals(2, collection.count(BsonDocument("""{ "name": "object-1" }""")))

        assertTrue { collection.deleteOne(BsonDocument("""{ "name": "object-1" }""")) }
        assertEquals(1, collection.count(BsonDocument("""{ "name": "object-1" }""")))
    }

    // Explicit types

    @Test
    fun deleteOne_fails() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("unknown top level operator: \$who.") {
            collection.deleteOne(BsonDocument("\$who", 1))
        }
    }

    @Test
    fun deleteMany() = runBlocking {
        assertEquals(0, collection.deleteMany(BsonDocument()))

        collection.insertMany((1..10).map { CollectionDataType("object-${it % 5}") })
        assertEquals(10, collection.find().size)

        assertEquals(2, collection.deleteMany(BsonDocument("""{ "name": "object-1" }""")))

        assertEquals(8, collection.find().size)

        assertEquals(8, collection.deleteMany(BsonDocument()))
    }

    @Test
    fun deleteMany_fails() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("unknown top level operator: \$who.") {
            collection.deleteMany(BsonDocument("\$who", 1))
        }
    }

    @Test
    open fun updateOne() = runBlocking<Unit> {
        assertEquals(0, collection.count())

        assertEquals(
            4,
            collection.insertMany<CollectionDataType, BsonValue>(
                listOf(
                    CollectionDataType("object-1"),
                    CollectionDataType("object-1"),
                    CollectionDataType("object-1"),
                    CollectionDataType("object-1")
                )
            ).size
        )
        assertEquals(4, collection.count())

        // Update no match
        val updateWithoutMatch = collection.updateOne(
            BsonDocument("""{ "name": "NOMATCH"}"""),
            BsonDocument("\$set", BsonDocument("""{ "name": "UPDATED"}""")),
        )
        assertEquals(false to null, updateWithoutMatch)

        // Update with match match
        val updateWithMatch = collection.updateOne(
            BsonDocument("""{ "name": "object-1"}"""),
            BsonDocument("\$set", BsonDocument("""{ "name": "object-2"}""")),
        )
        assertEquals(true to null, updateWithMatch)
        assertEquals(4, collection.count())
        assertEquals(3, collection.count(filter = BsonDocument("""{"name": "object-1"}""")))
        assertEquals(1, collection.count(filter = BsonDocument("""{"name": "object-2"}""")))

        // Upsert no match
        val upsertWithoutMatch = collection.updateOne(
            BsonDocument("""{ "name": "object-3"}"""), BsonDocument(""" { "name": "object-2", "_id" : ${Random.nextInt()}}"""), upsert = true
        )
        upsertWithoutMatch.let { (updated, upsertedId) ->
            assertFalse(updated)
            assertIs<Int>(upsertedId)
        }
        assertEquals(5, collection.count())
        assertEquals(2, collection.count(filter = BsonDocument("""{"name": "object-2"}""")))

        // Upsert with match
        val upsertWithMatch = collection.updateOne(
            BsonDocument("""{ "name": "object-2"}"""), BsonDocument(""" { "name": "object-3"}"""), upsert = true
        )
        assertEquals(true to null, upsertWithMatch)
        assertEquals(5, collection.count())
        assertEquals(1, collection.count(filter = BsonDocument("""{"name": "object-2"}""")))
    }

    @Test
    fun updateOne_explicitTypes() = runBlocking<Unit> {
        val upsertWithoutMatch = collection.updateOne<BsonValue>(
            BsonDocument("""{ "name": "object-3"}"""), BsonDocument(""" { "name": "object-2", "_id" : ${Random.nextInt()}}"""), upsert = true
        )
        upsertWithoutMatch.let { (updated, upsertedId) ->
            assertFalse(updated)
            assertIs<BsonValue>(upsertedId)
        }
    }

    @Test
    fun updateOne_fails() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("unknown top level operator: \$who.") {
            collection.updateOne(BsonDocument("\$who", 1), BsonDocument())
        }
    }

    @Test
    fun updateMany() = runBlocking<Unit> {
        assertEquals(0, collection.count())
        RealmLog.level = LogLevel.ALL
        assertEquals(
            4,
            collection.insertMany<CollectionDataType, BsonValue>(
                listOf(
                    CollectionDataType("x"),
                    CollectionDataType("x"),
                    CollectionDataType("y"),
                    CollectionDataType("z")
                )
            ).size
        )
        assertEquals(4, collection.count())
        // Update with no match
        val updateWithoutMatch = collection.updateMany(
            BsonDocument("""{"name": "NOMATCH"}"""),
            BsonDocument("""{"name": "UPDATED"}"""),
        )
        assertEquals(0L to null, updateWithoutMatch)
        assertEquals(0, collection.count(filter = BsonDocument("""{"name": "UPDATED"}""")))
        assertEquals(4, collection.count())

        // Update with match
        val updateWithMatch = collection.updateMany(
            BsonDocument("""{ "name": "x"}"""),
            BsonDocument("""{ "name": "UPDATED"}"""),
        )
        assertEquals(2L to null, updateWithMatch)
        assertEquals(2, collection.count(filter = BsonDocument("""{"name": "UPDATED"}""")))
        assertEquals(4, collection.count())

        // Upsert no match
        val upsertWithoutMatch = collection.updateMany(
            BsonDocument("""{ "name": "NOMATCH"}"""), BsonDocument(""" { "name": "UPSERTED", "_id" : ${Random.nextInt()}}"""), upsert = true
        )
        upsertWithoutMatch.let {
            assertEquals(0, it.first)
            assertIs<Int>(it.second)
        }
        assertEquals(5, collection.count())
        assertEquals(1, collection.count(filter = BsonDocument("""{"name": "UPSERTED"}""")))
        // Upsert with match
        val upsertWithMatch = collection.updateMany(
            BsonDocument("""{ "name": "y"}"""), BsonDocument(""" { "name": "z"}"""), upsert = true
        )
        assertEquals(1L to null, upsertWithMatch)
        assertEquals(5, collection.count())
        assertEquals(0, collection.count(filter = BsonDocument("""{"name": "y"}""")))
    }

    @Test
    fun updateMany_explicitTypes() = runBlocking<Unit> {
        collection.updateMany<BsonValue>(
            BsonDocument("""{ "name": "object-3"}"""),
            BsonDocument(""" { "name": "object-2", "_id" : ${Random.nextInt()}}"""),
            upsert = true
        ).let { (updated, upsertedId) ->
            assertEquals(0, updated)
            assertIs<BsonValue>(upsertedId)
        }
    }

    @Test
    fun updateMany_fails() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("Unknown modifier: \$who") {
            collection.updateOne(BsonDocument(), BsonDocument("\$who", 1))
        }
    }

    @Test
    fun findOneAndUpdate() = runBlocking<Unit> {
        assertNull(collection.findOneAndUpdate(BsonDocument(), BsonDocument()))

        val names = (1..10).map { "object-${it % 5}" }
        val ids: List<Int> = collection.insertMany(names.map { CollectionDataType(it) })

        // Update with no match
        assertNull(
            collection.findOneAndUpdate(
                BsonDocument("""{"name": "NOMATCH"}"""),
                BsonDocument("""{"name": "UPDATED"}"""),
            )
        )
        assertEquals(0, collection.count(filter = BsonDocument("""{"name": "UPDATED"}""")))

        // Update with match - return old
        collection.findOneAndUpdate(
            BsonDocument("""{ "name": "object-1"}"""),
            BsonDocument("""{ "name": "UPDATED"}"""),
        )!!.let {
            assertEquals("object-1", it.name)
        }
        assertEquals(1, collection.count(filter = BsonDocument("""{"name": "UPDATED"}""")))
        // Update with match - return new
        collection.findOneAndUpdate(
            BsonDocument("""{ "name": "object-1"}"""),
            BsonDocument("""{ "name": "UPDATED"}"""),
            returnNewDoc = true
        )!!.let {
            assertEquals("UPDATED", it.name)
        }
        assertEquals(2, collection.count(filter = BsonDocument("""{"name": "UPDATED"}""")))

        // Upsert no match
        assertNull(
            collection.findOneAndUpdate(
                filter = BsonDocument("""{ "name": "NOMATCH"}"""),
                update = BsonDocument(""" { "name": "UPSERTED", "_id" : ${Random.nextInt()}}"""),
                upsert = true,
            )
        )
        // Upsert no match - return new document
        collection.findOneAndUpdate(
            filter = BsonDocument("""{ "name": "NOMATCH"}"""),
            update = BsonDocument(""" { "name": "UPSERTED", "_id" : ${Random.nextInt()}}"""),
            upsert = true,
            returnNewDoc = true,
        )!!.let {
            assertEquals("UPSERTED", it.name)
        }

        // Upsert with match
        collection.findOneAndUpdate(
            filter = BsonDocument("""{ "name": "object-2"}"""),
            update = BsonDocument(""" { "name": "UPSERTED" }"""),
            upsert = true,
        )!!.let {
            assertEquals("object-2", it.name)
        }
        collection.findOneAndUpdate(
            filter = BsonDocument("""{ "name": "object-2"}"""),
            update = BsonDocument(""" { "name": "UPSERTED"}"""),
            upsert = true,
            returnNewDoc = true,
        )!!.let {
            assertEquals("UPSERTED", it.name)
        }

        // Project without name
        collection.findOneAndUpdate(
            filter = BsonDocument("name", "object-3"),
            update = BsonDocument(""" { "name": "UPDATED"}"""),
            projection = BsonDocument("""{ "name" : 0}""")
        )!!.run {
            // FIXME Should be "Default" but serialization fails if field is non-nullable even though descriptor correctly has isOptional=true
            // assertEquals("Default", this.name)
            assertNull(this.name) // Currently null because of nullability, but should have default value "Default"
            // _id is included by default and matched one of the previously inserted objects
            assertTrue { this._id in ids }
        }
        // Project without _id, have to be explicitly excluded
        collection.findOneAndUpdate(
            filter = BsonDocument("name", "object-3"),
            update = BsonDocument(""" { "name": "UPDATED"}"""),
            projection = BsonDocument("""{ "_id" : 0}""")
        )!!.run {
            assertEquals("object-3", name)
            // FIXME Should be constructor default arguments but serialization fails if field is non-nullable even though descriptor correctly has isOptional=true
            // We don't know the id as the constructor default generated a new one
            // assertFalse { this._id in ids}
            assertNull(this._id)
        }

        // Sort
        val sortedNames: List<String> = collection.find().map { it.name!! }.sorted()
        collection.findOneAndUpdate(
            filter = BsonDocument(),
            update = BsonDocument(""" { "name": "FIRST"}"""),
            sort = BsonDocument(mapOf("name" to BsonInt32(1)))
        )!!.run {
            assertEquals(sortedNames.first(), this.name)
        }
        collection.findOneAndUpdate(
            filter = BsonDocument(),
            update = BsonDocument(""" { "name": "LAST"}"""),
            sort = BsonDocument(mapOf("name" to BsonInt32(-1)))
        )!!.run {
            assertEquals(sortedNames.last(), this.name)
        }
    }

    @Test
    fun findOneAndUpdate_explicitTypes() = runBlocking<Unit> {
        collection.insertOne(CollectionDataType("object-1"))

        collection.findOneAndUpdate<BsonDocument>(
            BsonDocument("""{ "name": "object-1"}"""),
            BsonDocument("""{ "name": "UPDATED"}"""),
        )!!.let {
            assertEquals("object-1", it.asDocument()["name"]!!.asString().value)
        }
    }

    @Test
    fun findOneAndUpdate_fails() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("Unknown modifier: \$who") {
            collection.findOneAndUpdate(BsonDocument(), BsonDocument("\$who", 1))
        }
    }

    @Test
    fun findOneAndReplace() = runBlocking<Unit> {
        assertNull(collection.findOneAndReplace(BsonDocument(), BsonDocument()))

        val names = (1..10).map { "object-${it % 5}" }
        val ids: List<Int> = collection.insertMany(names.map { CollectionDataType(it) })

        // Replace with no match
        assertNull(
            collection.findOneAndReplace(
                BsonDocument("""{"name": "NOMATCH"}"""),
                BsonDocument(""" { "name": "REPLACED", "_id" : ${Random.nextInt()}}"""),
            )
        )
        assertEquals(0, collection.count(filter = BsonDocument("""{"name": "REPLACED"}""")))

        // Replace with match - return old
        collection.findOneAndReplace(
            BsonDocument("""{ "name": "object-1"}"""),
            BsonDocument(""" { "name": "REPLACED"}"""),
        )!!.let {
            assertEquals("object-1", it.name)
        }
        assertEquals(1, collection.count(filter = BsonDocument("""{"name": "REPLACED"}""")))

        // Replace with match - return new
        collection.findOneAndReplace(
            BsonDocument("""{ "name": "object-1"}"""),
            BsonDocument("""{ "name": "REPLACED"}"""),
            returnNewDoc = true
        )!!.let {
            assertEquals("REPLACED", it.name)
        }
        assertEquals(2, collection.count(filter = BsonDocument("""{"name": "REPLACED"}""")))

        // Upsert no match
        assertNull(
            collection.findOneAndReplace(
                filter = BsonDocument("""{ "name": "NOMATCH"}"""),
                document = BsonDocument(""" { "name": "UPSERTED", "_id" : ${Random.nextInt()}}"""),
                upsert = true,
            )
        )
        // Upsert no match - return new document
        collection.findOneAndReplace(
            filter = BsonDocument("""{ "name": "NOMATCH"}"""),
            document = BsonDocument(""" { "name": "UPSERTED", "_id" : ${Random.nextInt()}}"""),
            upsert = true,
            returnNewDoc = true,
        )!!.let {
            assertEquals("UPSERTED", it.name)
        }

        // Upsert with match
        collection.findOneAndReplace(
            filter = BsonDocument("""{ "name": "object-2"}"""),
            document = BsonDocument(""" { "name": "UPSERTED" }"""),
            upsert = true,
        )!!.let {
            assertEquals("object-2", it.name)
        }
        collection.findOneAndReplace(
            filter = BsonDocument("""{ "name": "object-2"}"""),
            document = BsonDocument(""" { "name": "UPSERTED"}"""),
            upsert = true,
            returnNewDoc = true,
        )!!.let {
            assertEquals("UPSERTED", it.name)
        }

        // Project without name
        collection.findOneAndReplace(
            filter = BsonDocument("name", "object-3"),
            document = BsonDocument(""" { "name": "REPLACED"}"""),
            projection = BsonDocument("""{ "name" : 0}""")
        )!!.run {
            // FIXME Should be "Default" but serialization fails if field is non-nullable even though descriptor correctly has isOptional=true
            // assertEquals("Default", this.name)
            assertNull(this.name) // Currently null because of nullability, but should have default value "Default"
            // _id is included by default and matched one of the previously inserted objects
            assertTrue { this._id in ids }
        }
        // Project without _id, have to be explicitly excluded
        collection.findOneAndReplace(
            filter = BsonDocument("name", "object-3"),
            document = BsonDocument(""" { "name": "REPLACED"}"""),
            projection = BsonDocument("""{ "_id" : 0}""")
        )!!.run {
            assertEquals("object-3", name)
            // FIXME Should be constructor default arguments but serialization fails if field is non-nullable even though descriptor correctly has isOptional=true
            // We don't know the id as the constructor default generated a new one
            // assertFalse { this._id in ids}
            assertNull(this._id)
        }

        // Sort
        val sortedNames: List<String> = collection.find().map { it.name!! }.sorted()
        collection.findOneAndReplace(
            filter = BsonDocument(),
            document = BsonDocument(""" { "name": "FIRST"}"""),
            sort = BsonDocument(mapOf("name" to BsonInt32(1)))
        )!!.run {
            assertEquals(sortedNames.first(), this.name)
        }
        collection.findOneAndReplace(
            filter = BsonDocument(),
            document = BsonDocument(""" { "name": "LAST"}"""),
            sort = BsonDocument(mapOf("name" to BsonInt32(-1)))
        )!!.run {
            assertEquals(sortedNames.last(), this.name)
        }
    }

    @Test
    fun findOneAndReplace_explicitTypes() = runBlocking<Unit> {
        collection.insertOne(CollectionDataType("object-1"))

        collection.findOneAndReplace<BsonDocument>(
            BsonDocument("""{ "name": "object-1"}"""),
            BsonDocument("""{ "name": "REPLACED"}"""),
        )!!.let {
            assertEquals("object-1", it.asDocument()["name"]!!.asString().value)
        }
    }

    @Test
    fun findOneAndReplace_fails() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("the replace operation document must not contain atomic operators") {
            collection.findOneAndReplace(BsonDocument(), BsonDocument("\$who", 1))
        }
    }

    @Test
    fun findOneAndDelete() = runBlocking<Unit> {
        assertNull(collection.findOneAndDelete(BsonDocument(), BsonDocument()))

        val names = (1..10).map { "object-${it % 5}" }
        val ids: List<Int> = collection.insertMany(names.map { CollectionDataType(it) })

        RealmLog.level = LogLevel.ALL
        // Delete with no match
        assertNull(collection.findOneAndDelete(BsonDocument("""{"name": "NOMATCH"}""")))
        assertEquals(10, collection.count(filter = BsonDocument()))

        // Delete with match
        collection.findOneAndDelete(
            BsonDocument("""{ "name": "object-1"}"""),
        )!!.let {
            assertEquals("object-1", it.name)
        }
        assertEquals(9, collection.count(filter = BsonDocument()))

        // Project without name
        collection.findOneAndDelete(
            filter = BsonDocument("name", "object-3"),
            projection = BsonDocument("""{ "name" : 0}""")
        )!!.run {
            // FIXME Should be "Default" but serialization fails if field is non-nullable even though descriptor correctly has isOptional=true
            // assertEquals("Default", this.name)
            assertNull(this.name) // Currently null because of nullability, but should have default value "Default"
            // _id is included by default and matched one of the previously inserted objects
            assertTrue { this._id in ids }
        }
        // Project without _id, have to be explicitly excluded
        collection.findOneAndDelete(
            filter = BsonDocument("name", "object-3"),
            projection = BsonDocument("""{ "_id" : 0}""")
        )!!.run {
            assertEquals("object-3", name)
            // FIXME Should be constructor default arguments but serialization fails if field is non-nullable even though descriptor correctly has isOptional=true
            // We don't know the id as the constructor default generated a new one
            // assertFalse { this._id in ids}
            assertNull(this._id)
        }

        // Sort
        val sortedNames: List<String> = collection.find().map { it.name!! }.sorted()
        collection.findOneAndDelete(
            filter = BsonDocument(),
            sort = BsonDocument(mapOf("name" to BsonInt32(1)))
        )!!.run {
            assertEquals(sortedNames.first(), this.name)
        }
        collection.findOneAndDelete(
            filter = BsonDocument(),
            sort = BsonDocument(mapOf("name" to BsonInt32(-1)))
        )!!.run {
            assertEquals(sortedNames.last(), this.name)
        }
    }

    @Test
    fun findOneAndDelete_explicitTypes() = runBlocking<Unit> {
        collection.insertOne(CollectionDataType("object-1"))

        collection.findOneAndDelete<BsonDocument>(
            BsonDocument("""{ "name": "object-1"}"""),
        )!!.let {
            assertEquals("object-1", it.asDocument()["name"]!!.asString().value)
        }
    }

    @Test
    fun findOneAndDelete_fails() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("unknown top level operator: \$who.") {
            collection.findOneAndDelete(BsonDocument("\$who", 1))
        }
    }
}

// Helper method to be able to differentiate collection creation across test classes
inline fun <reified T : BaseRealmObject, K> MongoCollectionTests.collection(clazz: KClass<T>): MongoCollection<T, K> {
    return when (this) {
        is MongoCollectionFromDatabaseTests -> database.collection<T, K>(T::class.simpleName!!)
        is MongoCollectionFromClientTests -> client.collection()
    }
}

// Helper method to easy BsonDocument construction from Kotlin types and avoid BsonValue wrappers.
@Suppress("name")
@OptIn(ExperimentalKBsonSerializerApi::class)
inline operator fun <reified T : Any> BsonDocument.Companion.invoke(
    key: String,
    value: T,
): BsonDocument {
    return BsonDocument(key to EJson.Default.encodeToBsonValue(value))
}

// Class that is unknown to the server. Should never be inserted as the server would then
// automatically create the collection.
@Serializable
class NonSchemaType : RealmObject {
    var name = "Unknown"
}

@Serializable
class CollectionDataType(var name: String? = "Default", var _id: Int? = Random.nextInt()) : RealmObject {
    constructor() : this("Default")
}

// Distinct data type with same fields as the above CollectionDataType used to showcase injection
// of custom serializers.
@PersistedName("CollectionDataType")
class CustomDataType(var name: String) : RealmObject {
    @Suppress("unused")
    constructor() : this("Default")
}
// Custom Id type to showcase that we can use custom serializers for primary key return values.
class CustomIdType(val id: ObjectId)

// Custom serializers to showcase that we can inject serializers throughout the MongoClient APIs.
class CustomDataTypeSerializer : KSerializer<CustomDataType> {

    val serializer = BsonValue.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor
    override fun deserialize(decoder: Decoder): CustomDataType {
        return decoder.decodeSerializableValue(serializer).let {
            CustomDataType(it.asDocument()["name"]!!.asString().value)
        }
    }

    override fun serialize(encoder: Encoder, value: CustomDataType) {
        encoder.encodeSerializableValue(serializer, BsonDocument("name", value.name))
    }
}
class CustomIdSerializer : KSerializer<CustomIdType> {
    val serializer = BsonValue.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor
    override fun deserialize(decoder: Decoder): CustomIdType {
        return decoder.decodeSerializableValue(serializer).let {
            CustomIdType(it.asObjectId())
        }
    }

    override fun serialize(encoder: Encoder, value: CustomIdType) {
        encoder.encodeSerializableValue(serializer, value.id)
    }
}

@OptIn(ExperimentalKBsonSerializerApi::class)
val customEjsonSerializer = EJson(
    serializersModule = SerializersModule {
        contextual(CustomDataType::class, CustomDataTypeSerializer())
        contextual(CustomIdType::class, CustomIdSerializer())
    }
)
