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
        val unknownCollection = collection<Unknown, BsonValue>(Unknown::class)
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
        val unknownCollection = collection<Unknown, BsonValue>(Unknown::class)
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
        RealmLog.level = LogLevel.ALL
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
        assertNull(collection.findOne<BsonValue>())

        collection.insertMany((1..10).map { CollectionDataType("object-${it % 5}") })

        // No match
        assertNull(collection.findOne(filter = BsonDocument("name", "cat")))

        // Multiple matches, still only one document
        // Default types
        collection.findOne(filter = BsonDocument("name", "object-0")).run {
            assertIs<CollectionDataType>(this)
            assertEquals("object-0", this.name)
        }

        // projection
        val collectionDataType = CollectionDataType("object-6")
        assertEquals(collectionDataType._id, collection.insertOne(collectionDataType))

        collection.findOne(filter = BsonDocument("name", "object-6")).run {
            assertIs<CollectionDataType>(this)
            assertEquals("object-6", this.name)
            assertEquals(collectionDataType._id, this._id)
        }

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

        // sort
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
    fun findOne_extraFieldsAreDiscarded() { }

    @Test
    fun findOne_missingFieldsGetsDefaults() { }

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
        RealmLog.level = LogLevel.ALL
        assertTrue { collection.find().isEmpty() }
        assertTrue { collection.find<CollectionDataType>().isEmpty() }

        val x: List<Int> = collection.insertMany(listOf(CollectionDataType("dog1"), CollectionDataType("dog2")))
        assertEquals(2, collection.find<CollectionDataType>().size)
        //
        collection.find(filter = null, projection = null, sort = null, limit = null)
    }

//
//    @Test
//    fun find() {
//        with(getCollectionInternal()) {
//            // Find on an empty collection returns false on hasNext and null on first
//            var iter = find()
//            assertFalse(iter.iterator().get()!!.hasNext())
//            assertNull(iter.first().get())
//
//            val doc1 = Document("hello", "world")
//            val doc2 = Document("hello", "friend")
//            doc2["proj"] = "field"
//            insertMany(listOf(doc1, doc2)).get()
//
//            // Iterate after inserting two documents
//            assertTrue(iter.iterator().get()!!.hasNext())
//            assertEquals(doc1, iter.first().get()!!.withoutId())
//
//            // Get next with sort by desc "_id" and limit to 1 document
//            assertEquals(doc2,
//                iter.limit(1)
//                    .sort(Document("_id", -1))
//                    .iterator().get()!!
//                    .next().withoutId())
//
//            // Find first document
//            iter = find(doc1)
//            assertTrue(iter.iterator().get()!!.hasNext())
//            assertEquals(doc1,
//                iter.iterator().get()!!
//                    .next().withoutId())
//
//            // Find with filter for first document
//            iter = find().filter(doc1)
//            assertTrue(iter.iterator().get()!!.hasNext())
//            assertEquals(doc1,
//                iter.iterator().get()!!
//                    .next().withoutId())
//
//            // Find with projection shows "proj" in result
//            val expected = Document("proj", "field")
//            assertEquals(expected,
//                find(doc2)
//                    .projection(Document("proj", 1))
//                    .iterator().get()!!
//                    .next().withoutId())
//
//            // Getting a new iterator returns first element on tryNext
//            val asyncIter = iter.iterator().get()!!
//            assertEquals(doc1, asyncIter.tryNext().withoutId())
//        }
//    }
//
    @Test
    fun find_fails() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("unknown top level operator: \$who.") {
            collection.find<BsonDocument>(BsonDocument("\$who", 1)).first()
        }
    }

    @Test
    fun aggregate() = runBlocking<Unit> {
        RealmLog.level = LogLevel.ALL
        collection.aggregate(listOf())
        collection.aggregate<CollectionDataType>(listOf())
        collection.aggregate<BsonDocument>(listOf())

        val x: List<Int> = collection.insertMany(listOf(CollectionDataType(name = "dog1"), CollectionDataType(name = "dog2")))
        collection.aggregate<CollectionDataType>(listOf())

        collection.aggregate<CollectionDataType>(listOf(BsonDocument("\$sort", BsonDocument("name", -1)), BsonDocument("\$limit", 1)))
    }
//    @Test
//    fun aggregate() {
//        with(getCollectionInternal()) {
//            // Aggregate on an empty collection returns false on hasNext and null on first
//            var iter = aggregate(listOf())
//            assertFalse(iter.iterator().get()!!.hasNext())
//            assertNull(iter.first().get())
//
//            // Iterate after inserting two documents
//            val doc1 = Document("hello", "world")
//            val doc2 = Document("hello", "friend")
//            insertMany(listOf(doc1, doc2)).get()
//            assertTrue(iter.iterator().get()!!.hasNext())
//            assertEquals(doc1.withoutId(), iter.first().get()!!.withoutId())
//
//            // Aggregate with pipeline, sort by desc "_id" and limit to 1 document
//            iter = aggregate(listOf(Document("\$sort", Document("_id", -1)), Document("\$limit", 1)))
//            assertEquals(doc2.withoutId(),
//                iter.iterator().get()!!
//                    .next().withoutId())
//
//            // Aggregate with pipeline, match first document
//            iter = aggregate(listOf(Document("\$match", doc1)))
//            assertTrue(iter.iterator().get()!!.hasNext())
//            assertEquals(doc1.withoutId(), iter.iterator().get()!!.next().withoutId())
//        }
//    }
//
//    @Test
//    fun aggregate_fails() {
//        with(getCollectionInternal()) {
//            assertFailsWithErrorCode(ErrorCode.MONGODB_ERROR) {
//                aggregate(listOf(Document("\$who", 1))).first().get()
//            }.also { e ->
//                assertTrue(e.errorMessage!!.contains("pipeline", true))
//            }
//        }
//    }
//

//    @Test
//    fun insertOne() = runBlocking<Unit> {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world").apply { this["_id"] = ObjectId() }
//            assertEquals(doc1.getObjectId("_id"), insertOne(doc1).get()!!.insertedId.asObjectId().value)
//            assertEquals(1, count().get())
//
//            val doc2 = Document("hello", "world")
//            val insertOneResult = insertOne(doc2).get()!!
//            assertNotNull(insertOneResult.insertedId.asObjectId().value)
//            assertEquals(2, count().get())
//        }
//    }

    @Test
    fun insertOne() = runBlocking<Unit> {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world").apply { this["_id"] = ObjectId() }
//            assertEquals(doc1.getObjectId("_id"), insertOne(doc1).get()!!.insertedId.asObjectId().value)
//            assertEquals(1, count().get())
//
//            val doc2 = Document("hello", "world")
//            val insertOneResult = insertOne(doc2).get()!!
//            assertNotNull(insertOneResult.insertedId.asObjectId().value)
//            assertEquals(2, count().get())
//        }

        RealmLog.level = LogLevel.ALL
        // Option 1 - Typed return value - BsonValue
        val insertedIdDocument: BsonValue = collection.insertOne(BsonDocument("name", "sadffds"))
        val insertedIdDocument2: Int = collection.insertOne(CollectionDataType("sadf"))

        // Option 2 - Explicit generic arguments to enabling fluent API
        val id = collection.insertOne<CollectionDataType, BsonValue>(CollectionDataType("sadf"))

        // Option 3 - Automatically serialized object
        val x2: Int = collection.insertOne(CollectionDataType("sadf"))

        val x3: ObjectId = collection.insertOne(BsonDocument("""{ "name" : "asdf" }"""))
    }

    @Test
    fun insertOne_throwsOnExistingPrimaryKey() { }
    @Test
    fun insertOne_throwsOnUnknownFields() { }

    @Test
    fun insertOne_throwsOnMissingRequiredFields() { }

    @Test
    fun insertOne_throwsOnTypeMismatch() = runBlocking<Unit> {
        val collectionWithFixedSchema = collection<SyncDog, ObjectId>(SyncDog::class)
        assertFailsWithMessage<ServiceException>("insert not permitted") {
            collectionWithFixedSchema.insertOne<BsonDocument, ObjectId>(BsonDocument("_id", ObjectId()))
        }
    }

//
//    @Test
//    fun insertOne_throwsWhenMixingIdTypes() {
//        with(getCollectionInternal()) {
//            // The default collection uses ObjectId for "_id"
//            val doc1 = Document("hello", "world").apply { this["_id"] = 666 }
//            assertFailsWith<AppException> {
//                insertOne(doc1).get()!!
//            }.let { e ->
//                assertEquals("insert not permitted", e.errorMessage)
//            }
//        }
//    }
//
//    @Test
//    fun insertOne_integerId() {
//        with(getCollectionInternal(COLLECTION_NAME_ALT)) {
//            val doc1 = Document("hello", "world").apply { this["_id"] = 666 }
//            val insertOneResult = insertOne(doc1).get()!!
//            assertEquals(doc1.getInteger("_id"), insertOneResult.insertedId.asInt32().value)
//            assertEquals(1, count().get())
//        }
//    }
//
//    @Test
//    fun insertOne_fails() {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world").apply { this["_id"] = ObjectId() }
//            insertOne(doc1).get()
//
//            assertFailsWithErrorCode(ErrorCode.MONGODB_ERROR) {
//                insertOne(doc1).get()
//            }.also { e ->
//                assertTrue(e.errorMessage!!.contains("duplicate", true))
//            }
//        }
//    }

//    @Test
//    fun insertMany() = runBlocking {
//        RealmLog.level = LogLevel.ALL
//        val x: List<BsonValue> = collection.insertMany(listOf(SyncDog("a")))
//        val syncDogIntIdCollection = collection<SyncDogIntId, Int>(SyncDogIntId::class)
//        val elements = SyncDogIntId("a", Random.nextInt())
//        val y: List<Int> = syncDogIntIdCollection.insertMany(listOf(elements))
//
//        assertFailsWithMessage<ServiceException>("dup key") {
//            val z: List<Int> = syncDogIntIdCollection.insertMany(listOf(elements))
//        }
//
//        val typedCollection = collection.reshape<SyncDog, ObjectId>()
//        val z: List<ObjectId> = typedCollection.insertMany(listOf(SyncDog("sadf")))
//        val tyz = typedCollection.insertMany<SyncDog, BsonValue>(listOf(SyncDog("sadf")))
//
//        val bsonSyncDogs: MongoCollection<BsonDocument, BsonValue> = collection.reshape()
//        val insertMany /*: List<BsonValue> */ = bsonSyncDogs.insertMany(listOf(BsonDocument("name", "x")))
//
//        val syncDogs: MongoCollection<SyncDog, ObjectId> = database.collection<SyncDog, ObjectId>("SyncDog")
//
//        val objectIds = syncDogs.insertMany(listOf(SyncDog("name")))
//
//        val objectIds2: List<ObjectId> = syncDogs.insertMany<BsonValue, ObjectId>(listOf(BsonDocument("name", "asdf")))
//    }

//
//    @Test
//    fun insertMany_singleDocument() {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world").apply { this["_id"] = ObjectId() }
//
//            assertEquals(doc1.getObjectId("_id"),
//                insertMany(listOf(doc1)).get()!!.insertedIds[0]!!.asObjectId().value)
//            val doc2 = Document("hello", "world")
//
//            assertNotEquals(doc1.getObjectId("_id"), insertMany(listOf(doc2)).get()!!.insertedIds[0]!!.asObjectId().value)
//
//            val doc3 = Document("one", "two")
//            val doc4 = Document("three", 4)
//
//            insertMany(listOf(doc3, doc4)).get()
//        }
//    }
//
//    @Test
//    fun insertMany_singleDocument_fails() {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world").apply { this["_id"] = ObjectId() }
//            insertMany(listOf(doc1)).get()
//
//            assertFailsWithErrorCode(ErrorCode.MONGODB_ERROR) {
//                insertMany(listOf(doc1)).get()
//            }.also { e ->
//                assertTrue(e.errorMessage!!.contains("duplicate", true))
//            }
//        }
//    }
//
//    @Test
//    fun insertMany_multipleDocuments() {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world").apply { this["_id"] = ObjectId() }
//            val doc2 = Document("hello", "world").apply { this["_id"] = ObjectId() }
//            val documents = listOf(doc1, doc2)
//
//            insertMany(documents).get()!!
//                .insertedIds
//                .forEach { entry ->
//                    assertEquals(documents[entry.key.toInt()]["_id"], entry.value.asObjectId().value)
//                }
//
//            val doc3 = Document("one", "two")
//            val doc4 = Document("three", 4)
//
//            insertMany(listOf(doc3, doc4)).get()
//            assertEquals(4, count().get())
//        }
//    }
//
//    @Test
//    fun insertMany_multipleDocuments_IntegerId() {
//        with(getCollectionInternal(COLLECTION_NAME_ALT)) {
//            val doc1 = Document("hello", "world").apply { this["_id"] = 42 }
//            val doc2 = Document("hello", "world").apply { this["_id"] = 42 + 1 }
//            val documents = listOf(doc1, doc2)
//
//            insertMany(documents).get()!!
//                .insertedIds
//                .forEach { entry ->
//                    assertEquals(documents[entry.key.toInt()]["_id"], entry.value.asInt32().value)
//                }
//
//            val doc3 = Document("one", "two")
//            val doc4 = Document("three", 4)
//
//            insertMany(listOf(doc3, doc4)).get()
//            assertEquals(4, count().get())
//        }
//    }
//
//    @Test
//    fun insertMany_throwsWhenMixingIdTypes() {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world").apply { this["_id"] = 42 }
//            val doc2 = Document("hello", "world").apply { this["_id"] = 42 + 1 }
//            val documents = listOf(doc1, doc2)
//
//            assertFailsWith<AppException> {
//                insertMany(documents).get()!!
//            }.let { e ->
//                assertEquals("insert not permitted", e.errorMessage)
//            }
//        }
//    }
//
//    @Test
//    fun insertMany_multipleDocuments_fails() {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world").apply { this["_id"] = ObjectId() }
//            val doc2 = Document("hello", "world").apply { this["_id"] = ObjectId() }
//            val documents = listOf(doc1, doc2)
//            insertMany(documents).get()
//
//            assertFailsWithErrorCode(ErrorCode.MONGODB_ERROR) {
//                insertMany(documents).get()
//            }.also { e ->
//                assertTrue(e.errorMessage!!.contains("duplicate", true))
//            }
//        }
//    }
//

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

    @Test
    fun deleteOne_fails() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("unknown top level operator: \$who.") {
            collection.deleteOne(BsonDocument("\$who", 1))
        }
    }

    @Test
    fun deleteMany() = runBlocking {
        // Argument wrapper DSL
        RealmLog.level = LogLevel.ALL
        assertEquals(0, collection.deleteMany(BsonDocument()))

        assertEquals(
            2,
            collection.insertMany<CollectionDataType, BsonValue>(
                listOf(
                    CollectionDataType("object-1"),
                    CollectionDataType("object-1")
                )
            ).size
        )
        assertEquals(2, collection.deleteMany(BsonDocument("""{ "name": "object-1" }""")))

        assertEquals(
            3,
            collection.insertMany<CollectionDataType, BsonValue>(
                listOf(
                    CollectionDataType("object-1"),
                    CollectionDataType("object-1"),
                    CollectionDataType("object-1")
                )
            ).size
        )
        assertEquals(3, collection.deleteMany(BsonDocument()))
    }

//    @Test
//    fun deleteMany_singleDocument() {
//        with(getCollectionInternal()) {
//            assertEquals(0, count().get())
//
//            val rawDoc = Document("hello", "world")
//            val doc1 = Document(rawDoc)
//
//            insertOne(doc1).get()
//            assertEquals(1, count().get())
//            assertEquals(1, deleteMany(doc1).get()!!.deletedCount)
//            assertEquals(0, count().get())
//        }
//    }
//
//    @Test
//    fun deleteMany_multipleDocuments() {
//        with(getCollectionInternal()) {
//            assertEquals(0, count().get())
//
//            val rawDoc = Document("hello", "world")
//            val doc1 = Document(rawDoc)
//            val doc1b = Document(rawDoc)
//            val doc2 = Document("foo", "bar")
//            val doc3 = Document("42", "666")
//            insertMany(listOf(doc1, doc1b, doc2, doc3)).get()
//            assertEquals(2, deleteMany(rawDoc).get()!!.deletedCount)                 // two docs will be deleted
//            assertEquals(2, count().get())                                           // two docs still present
//            assertEquals(2, deleteMany(Document()).get()!!.deletedCount)             // delete all
//            assertEquals(0, count().get())
//
//            insertMany(listOf(doc1, doc1b, doc2, doc3)).get()
//            assertEquals(4, deleteMany(Document()).get()!!.deletedCount)             // delete all
//            assertEquals(0, count().get())
//        }
//    }
//
//    @Test
//    fun deleteMany_fails() {
//        with(getCollectionInternal()) {
//            assertFailsWithErrorCode(ErrorCode.MONGODB_ERROR) {
//                deleteMany(Document("\$who", 1)).get()
//            }.also { e ->
//                assertTrue(e.errorMessage!!.contains("operator", true))
//            }
//        }
//    }
//
    @Test
    open fun updateOne() = runBlocking<Unit> {
        // Argument wrapper DSL
        assertEquals(0, collection.count())

        RealmLog.level = LogLevel.ALL
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

        // update no match
        val updateWithoutMatch = collection.updateOne<BsonDocument>(
            BsonDocument("""{ "name": "NOMATCH"}"""),
            BsonDocument("""{ "name": "UPDATED"}"""),
        )
        assertEquals(false to null, updateWithoutMatch)

        // update with match match
        val updateWithMatch = collection.updateOne(
            BsonDocument("""{ "name": "object-1"}"""),
            BsonDocument("""{ "name": "object-2"}"""),
        )
        assertEquals(true to null, updateWithMatch)
        assertEquals(4, collection.count())
        assertEquals(3, collection.count(filter = BsonDocument("""{"name": "object-1"}""")))
        assertEquals(1, collection.count(filter = BsonDocument("""{"name": "object-2"}""")))

        // upsert no match
        val upsertWithoutMatch = collection.updateOne<Int>(
            BsonDocument("""{ "name": "object-3"}"""), BsonDocument(""" { "name": "object-2", "_id" : ${Random.nextInt()}}"""), upsert = true
        )
        upsertWithoutMatch.let { (updated, upsertedId) ->
            assertFalse(updated)
            assertIs<Int>(upsertedId)
        }
        assertEquals(5, collection.count())
        assertEquals(2, collection.count(filter = BsonDocument("""{"name": "object-2"}""")))

        // upsert with match
        val upsertWithMatch = collection.updateOne<BsonValue>(
            BsonDocument("""{ "name": "object-2"}"""), BsonDocument(""" { "name": "object-3"}"""), upsert = true
        )
        assertEquals(true to null, upsertWithMatch)
        assertEquals(5, collection.count())
        assertEquals(1, collection.count(filter = BsonDocument("""{"name": "object-2"}""")))
    }

    //    @Test
//    fun updateOne_emptyCollection() {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world")
//
//            // Update on an empty collection
//            updateOne(Document(), doc1)
//                .get()!!
//                .let {
//                    assertEquals(0, it.matchedCount)
//                    assertEquals(0, it.modifiedCount)
//                    assertNull(it.upsertedId)
//                }
//
//            // Update on an empty collection adding some values
//            val doc2 = Document("\$set", Document("woof", "meow"))
//            updateOne(Document(), doc2)
//                .get()!!
//                .let {
//                    assertEquals(0, it.matchedCount)
//                    assertEquals(0, it.modifiedCount)
//                    assertNull(it.upsertedId)
//                    assertEquals(0, count().get())
//                }
//        }
//    }
//
//    @Test
//    fun updateOne_emptyCollectionWithUpsert() {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world")
//
//            // Update on empty collection with upsert
//            val options = UpdateOptions().upsert(true)
//            updateOne(Document(), doc1, options)
//                .get()!!
//                .let {
//                    assertEquals(0, it.matchedCount)
//                    assertEquals(0, it.modifiedCount)
//                    assertFalse(it.upsertedId!!.isNull)
//                }
//            assertEquals(1, count().get())
//
//            assertEquals(doc1, find(Document()).first().get()!!.withoutId())
//        }
//    }
//
//    @Test
//    fun updateOne_fails() {
//        with(getCollectionInternal()) {
//            assertFailsWithErrorCode(ErrorCode.MONGODB_ERROR) {
//                updateOne(Document("\$who", 1), Document()).get()
//            }.also { e ->
//                assertTrue(e.errorMessage!!.contains("operator", true))
//            }
//        }
//    }
//
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
        val updateWithoutMatch = collection.updateMany<BsonValue>(
            BsonDocument("""{"name": "NOMATCH"}"""),
            BsonDocument("""{"name": "UPDATED"}"""),
        )
        assertEquals(0L to null, updateWithoutMatch)
        assertEquals(0, collection.count(filter = BsonDocument("""{"name": "UPDATED"}""")))
        assertEquals(4, collection.count())

        // update with match match
        val updateWithMatch = collection.updateMany(
            BsonDocument("""{ "name": "x"}"""),
            BsonDocument("""{ "name": "UPDATED"}"""),
        )
        assertEquals(2L to null, updateWithMatch)
        assertEquals(2, collection.count(filter = BsonDocument("""{"name": "UPDATED"}""")))
        assertEquals(4, collection.count())

        // upsert no match
        val upsertWithoutMatch = collection.updateMany<Int>(
            BsonDocument("""{ "name": "NOMATCH"}"""), BsonDocument(""" { "name": "UPSERTED", "_id" : ${Random.nextInt()}}"""), upsert = true
        )
        upsertWithoutMatch.let {
            assertEquals(0, it.first)
            assertIs<Int>(it.second)
        }
        assertEquals(5, collection.count())
        assertEquals(1, collection.count(filter = BsonDocument("""{"name": "UPSERTED"}""")))
        // upsert with match
        val upsertWithMatch = collection.updateMany<BsonValue>(
            BsonDocument("""{ "name": "y"}"""), BsonDocument(""" { "name": "z"}"""), upsert = true
        )
        assertEquals(1L to null, upsertWithMatch)
        assertEquals(5, collection.count())
        assertEquals(0, collection.count(filter = BsonDocument("""{"name": "y"}""")))
    }

    //    @Test
//    fun updateMany_emptyCollection() {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world")
//
//            // Update on empty collection
//            updateMany(Document(), doc1)
//                .get()!!
//                .let {
//                    assertEquals(0, it.matchedCount)
//                    assertEquals(0, it.modifiedCount)
//                    assertNull(it.upsertedId)
//                }
//            assertEquals(0, count().get())
//        }
//    }
//
//    @Test
//    fun updateMany_emptyCollectionWithUpsert() {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world")
//
//            // Update on empty collection with upsert
//            updateMany(Document(), doc1, UpdateOptions().upsert(true))
//                .get()!!
//                .let {
//                    assertEquals(0, it.matchedCount)
//                    assertEquals(0, it.modifiedCount)
//                    assertNotNull(it.upsertedId)
//                }
//            assertEquals(1, count().get())
//
//            // Add new value using update
//            val update = Document("woof", "meow")
//            updateMany(Document(), Document("\$set", update))
//                .get()!!
//                .let {
//                    assertEquals(1, it.matchedCount)
//                    assertEquals(1, it.modifiedCount)
//                    assertNull(it.upsertedId)
//                }
//            assertEquals(1, count().get())
//            val expected = Document(doc1).apply { this["woof"] = "meow" }
//            assertEquals(expected, find().first().get()!!.withoutId())
//
//            // Insert empty document, add ["woof", "meow"] to it and check it worked
//            insertOne(Document()).get()
//            updateMany(Document(), Document("\$set", update))
//                .get()!!
//                .let {
//                    assertEquals(2, it.matchedCount)
//                    assertEquals(2, it.modifiedCount)
//                }
//            assertEquals(2, count().get())
//            find().iterator()
//                .get()!!
//                .let {
//                    assertEquals(expected, it.next().withoutId())
//                    assertEquals(update, it.next().withoutId())
//                    assertFalse(it.hasNext())
//                }
//        }
//    }
//
//    @Test
//    fun updateMany_fails() {
//        with(getCollectionInternal()) {
//            assertFailsWithErrorCode(ErrorCode.MONGODB_ERROR) {
//                updateMany(Document("\$who", 1), Document()).get()
//            }.also { e ->
//                assertTrue(e.errorMessage!!.contains("operator", true))
//            }
//        }
//    }
//
    @Test
    fun findOneAndUpdate() = runBlocking<Unit> {
        RealmLog.level = LogLevel.ALL
        assertNull(collection.findOneAndUpdate<CollectionDataType>(BsonDocument(), BsonDocument()))
        collection.insertMany<CollectionDataType, Int>(
            listOf(
                CollectionDataType("object-1"),
                CollectionDataType("object-1"),
                CollectionDataType("object-2")
            )
        )
        collection.findOneAndUpdate<CollectionDataType>(
            BsonDocument(),
            BsonDocument("""{ "name": "object-1" }"""),
            upsert = true
        )
    }
//    @Test
//    fun findOneAndUpdate_emptyCollection() {
//        with(getCollectionInternal()) {
//            // Test null return format
//            assertNull(findOneAndUpdate(Document(), Document()).get())
//        }
//    }
//
//    @Test
//    fun findOneAndUpdate_noUpdates() {
//        with(getCollectionInternal()) {
//            assertNull(findOneAndUpdate(Document(), Document()).get())
//            assertEquals(0, count().get())
//        }
//    }
//
//    @Test
//    fun findOneAndUpdate_noUpsert() {
//        with(getCollectionInternal()) {
//            val sampleDoc = Document("hello", "world1")
//            sampleDoc["num"] = 2
//
//            // Insert a sample Document
//            insertOne(sampleDoc).get()
//            assertEquals(1, count().get())
//
//            // Sample call to findOneAndUpdate() where we get the previous document back
//            val sampleUpdate = Document("\$set", Document("hello", "hellothere")).apply {
//                this["\$inc"] = Document("num", 1)
//            }
//            findOneAndUpdate(Document("hello", "world1"), sampleUpdate)
//                .get()!!
//                .withoutId()
//                .let {
//                    assertEquals(sampleDoc.withoutId(), it)
//                }
//            assertEquals(1, count().get())
//
//            // Make sure the update took place
//            val expectedDoc = Document("hello", "hellothere")
//            expectedDoc["num"] = 3
//            assertEquals(expectedDoc.withoutId(), find().first().get()!!.withoutId())
//            assertEquals(1, count().get())
//
//            // Call findOneAndUpdate() again but get the new document
//            sampleUpdate.remove("\$set")
//            expectedDoc["num"] = 4
//            val options = FindOneAndModifyOptions()
//                .returnNewDocument(true)
//            findOneAndUpdate(Document("hello", "hellothere"), sampleUpdate, options)
//                .get()!!
//                .withoutId()
//                .let {
//                    assertEquals(expectedDoc.withoutId(), it)
//                }
//            assertEquals(1, count().get())
//
//            // Test null behaviour again with a filter that should not match any documents
//            assertNull(findOneAndUpdate(Document("hello", "zzzzz"), Document()).get())
//            assertEquals(1, count().get())
//        }
//    }
//
//    @Test
//    fun findOneAndUpdate_upsert() {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world1").apply { this["num"] = 1 }
//            val doc2 = Document("hello", "world2").apply { this["num"] = 2 }
//            val doc3 = Document("hello", "world3").apply { this["num"] = 3 }
//
//            val filter = Document("hello", "hellothere")
//
//            // Test the upsert option where it should not actually be invoked
//            var options = FindOneAndModifyOptions()
//                .returnNewDocument(true)
//                .upsert(true)
//            val update1 = Document("\$set", doc1)
//            assertEquals(doc1,
//                findOneAndUpdate(filter, update1, options)
//                    .get()!!
//                    .withoutId())
//            assertEquals(1, count().get())
//            assertEquals(doc1.withoutId(),
//                find().first()
//                    .get()!!
//                    .withoutId())
//
//            // Test the upsert option where the server should perform upsert and return new document
//            val update2 = Document("\$set", doc2)
//            assertEquals(doc2,
//                findOneAndUpdate(filter, update2, options)
//                    .get()!!
//                    .withoutId())
//            assertEquals(2, count().get())
//
//            // Test the upsert option where the server should perform upsert and return old document
//            // The old document should be empty
//            options = FindOneAndModifyOptions()
//                .upsert(true)
//            val update = Document("\$set", doc3)
//            assertNull(findOneAndUpdate(filter, update, options).get())
//            assertEquals(3, count().get())
//        }
//    }
//
//    @Test
//    fun findOneAndUpdate_withProjectionAndSort() {
//        with(getCollectionInternal()) {
//            insertMany(listOf(
//                Document(mapOf(Pair("team", "Fearful Mallards"), Pair("score", 25000))),
//                Document(mapOf(Pair("team", "Tactful Mooses"), Pair("score", 23500))),
//                Document(mapOf(Pair("team", "Aquatic Ponies"), Pair("score", 19250))),
//                Document(mapOf(Pair("team", "Cuddly Zebras"), Pair("score", 15235))),
//                Document(mapOf(Pair("team", "Garrulous Bears"), Pair("score", 18000)))
//            )).get()
//
//            assertEquals(5, count().get())
//            assertNotNull(findOne(Document("team", "Cuddly Zebras")))
//
//            // Project: team, hide _id; Sort: score ascending
//            val project = Document(mapOf(Pair("_id", 0), Pair("team", 1), Pair("score", 1)))
//            val sort = Document("score", 1)
//
//            // This results in the update of Cuddly Zebras
//            val updatedDocument = findOneAndUpdate(
//                Document("score", Document("\$lt", 22250)),
//                Document("\$inc", Document("score", 1)),
//                FindOneAndModifyOptions()
//                    .projection(project)
//                    .sort(sort)
//            ).get()
//
//            assertEquals(5, count().get())
//            assertEquals(
//                Document(mapOf(Pair("team", "Cuddly Zebras"), Pair("score", 15235))),
//                updatedDocument
//            )
//            assertEquals(
//                Document(mapOf(Pair("team", "Cuddly Zebras"), Pair("score", 15235 + 1))),
//                findOne(Document("team", "Cuddly Zebras")).get().withoutId()
//            )
//        }
//    }
//
//    @Test
//    fun findOneAndUpdate_fails() {
//        with(getCollectionInternal()) {
//            assertFailsWithErrorCode(ErrorCode.MONGODB_ERROR) {
//                findOneAndUpdate(Document(), Document("\$who", 1)).get()
//            }.also { e ->
//                assertTrue(e.errorMessage!!.contains("modifier", true))
//            }
//
//            assertFailsWithErrorCode(ErrorCode.MONGODB_ERROR) {
//                findOneAndUpdate(Document(), Document("\$who", 1), FindOneAndModifyOptions().upsert(true)).get()
//            }.also { e ->
//                assertTrue(e.errorMessage!!.contains("modifier", true))
//            }
//        }
//    }

    // FIXME Invalid fields?~?
    @Test
    fun findOneAndReplace() = runBlocking<Unit> {
        RealmLog.level = LogLevel.ALL
        assertNull(collection.findOneAndReplace<CollectionDataType>(BsonDocument(), BsonDocument()))
        collection.insertMany(
            listOf(
                CollectionDataType("object-1"),
                CollectionDataType("object-1"),
                CollectionDataType("object-2")
            )
        )
        val x = collection.findOneAndReplace<CollectionDataType>(
            BsonDocument(),
            BsonDocument("""{ "name": "object-1" }"""),
            upsert = true
        )
    }
//
//    @Test
//    fun findOneAndReplace_noUpdates() {
//        with(getCollectionInternal()) {
//            // Test null behaviour again with a filter that should not match any documents
//            assertNull(findOneAndReplace(Document("hello", "zzzzz"), Document()).get())
//            assertEquals(0, count().get())
//            assertNull(findOneAndReplace(Document(), Document()).get())
//            assertEquals(0, count().get())
//        }
//    }
//
//    @Test
//    fun findOneAndReplace_noUpsert() {
//        with(getCollectionInternal()) {
//            val sampleDoc = Document("hello", "world1").apply { this["num"] = 2 }
//
//            // Insert a sample Document
//            insertOne(sampleDoc).get()
//            assertEquals(1, count().get())
//
//            // Sample call to findOneAndReplace() where we get the previous document back
//            var sampleUpdate = Document("hello", "world2").apply { this["num"] = 2 }
//            assertEquals(sampleDoc.withoutId(),
//                findOneAndReplace(Document("hello", "world1"), sampleUpdate).get()!!.withoutId())
//            assertEquals(1, count().get())
//
//            // Make sure the update took place
//            val expectedDoc = Document("hello", "world2").apply { this["num"] = 2 }
//            assertEquals(expectedDoc.withoutId(), find().first().get()!!.withoutId())
//            assertEquals(1, count().get())
//
//            // Call findOneAndReplace() again but get the new document
//            sampleUpdate = Document("hello", "world3").apply { this["num"] = 3 }
//            val options = FindOneAndModifyOptions().returnNewDocument(true)
//            assertEquals(sampleUpdate.withoutId(),
//                findOneAndReplace(Document(), sampleUpdate, options).get()!!.withoutId())
//            assertEquals(1, count().get())
//
//            // Test null behaviour again with a filter that should not match any documents
//            assertNull(findOneAndReplace(Document("hello", "zzzzz"), Document()).get())
//            assertEquals(1, count().get())
//        }
//    }
//
//    @Test
//    fun findOneAndReplace_upsert() {
//        with(getCollectionInternal()) {
//            val doc4 = Document("hello", "world4").apply { this["num"] = 4 }
//            val doc5 = Document("hello", "world5").apply { this["num"] = 5 }
//            val doc6 = Document("hello", "world6").apply { this["num"] = 6 }
//
//            // Test the upsert option where it should not actually be invoked
//            val sampleUpdate = Document("hello", "world4").apply { this["num"] = 4 }
//            var options = FindOneAndModifyOptions()
//                .returnNewDocument(true)
//                .upsert(true)
//            assertEquals(doc4.withoutId(),
//                findOneAndReplace(Document("hello", "world3"), doc4, options)
//                    .get()!!
//                    .withoutId())
//            assertEquals(1, count().get())
//            assertEquals(doc4.withoutId(), find().first().get()!!.withoutId())
//
//            // Test the upsert option where the server should perform upsert and return new document
//            options = FindOneAndModifyOptions().returnNewDocument(true).upsert(true)
//            assertEquals(doc5.withoutId(), findOneAndReplace(Document("hello", "hellothere"), doc5, options).get()!!.withoutId())
//            assertEquals(2, count().get())
//
//            // Test the upsert option where the server should perform upsert and return old document
//            // The old document should be empty
//            options = FindOneAndModifyOptions().upsert(true)
//            assertNull(findOneAndReplace(Document("hello", "hellothere"), doc6, options).get())
//            assertEquals(3, count().get())
//        }
//    }
//
//    @Test
//    fun findOneAndReplace_withProjectionAndSort() {
//        with(getCollectionInternal()) {
//            insertMany(listOf(
//                Document(mapOf(Pair("team", "Fearful Mallards"), Pair("score", 25000))),
//                Document(mapOf(Pair("team", "Tactful Mooses"), Pair("score", 23500))),
//                Document(mapOf(Pair("team", "Aquatic Ponies"), Pair("score", 19250))),
//                Document(mapOf(Pair("team", "Cuddly Zebras"), Pair("score", 15235))),
//                Document(mapOf(Pair("team", "Garrulous Bears"), Pair("score", 18000)))
//            )).get()
//
//            assertEquals(5, count().get())
//            assertNotNull(findOne(Document("team", "Cuddly Zebras")))
//
//            // Project: team, hide _id; Sort: score ascending
//            val project = Document(mapOf(Pair("_id", 0), Pair("team", 1)))
//            val sort = Document("score", 1)
//
//            // This results in the replacement of Cuddly Zebras
//            val replacedDocument = findOneAndReplace(
//                Document("score", Document("\$lt", 22250)),
//                Document(mapOf(Pair("team", "Therapeutic Hamsters"), Pair("score", 22250))),
//                FindOneAndModifyOptions()
//                    .projection(project)
//                    .sort(sort)
//            ).get()
//
//            assertEquals(5, count().get())
//            assertEquals(Document("team", "Cuddly Zebras"), replacedDocument)
//            assertNull(findOne(Document("team", "Cuddly Zebras")).get())
//            assertNotNull(findOne(Document("team", "Therapeutic Hamsters")).get())
//
//            // Check returnNewDocument
//            val newDocument = findOneAndReplace(
//                Document("score", 22250),
//                Document(mapOf(Pair("team", "New Therapeutic Hamsters"), Pair("score", 30000))),
//                FindOneAndModifyOptions().returnNewDocument(true)
//            ).get()
//
//            assertEquals(Document(mapOf(Pair("team", "New Therapeutic Hamsters"), Pair("score", 30000))), newDocument.withoutId())
//        }
//    }
//
//    @Test
//    fun findOneAndReplace_fails() {
//        with(getCollectionInternal()) {
//            assertFailsWithErrorCode(ErrorCode.INVALID_PARAMETER) {
//                findOneAndReplace(Document(), Document("\$who", 1)).get()
//            }
//
//            assertFailsWithErrorCode(ErrorCode.INVALID_PARAMETER) {
//                findOneAndReplace(Document(), Document("\$who", 1), FindOneAndModifyOptions().upsert(true)).get()
//            }
//        }
//    }

    @Test
    fun findOneAndDelete() = runBlocking<Unit> {
        RealmLog.level = LogLevel.ALL
        assertNull(collection.findOneAndDelete<CollectionDataType>(BsonDocument(), BsonDocument()))
        collection.insertMany(
            listOf(
                CollectionDataType("object-1"),
                CollectionDataType("object-1"),
                CollectionDataType("object-2")
            )
        )
        val x: CollectionDataType? = collection.findOneAndDelete<CollectionDataType>(
            BsonDocument(),
            BsonDocument("""{ "name": "object-1" }"""),
        )
    }
    //
//    @Test
//    fun findOneAndDelete() {
//        with(getCollectionInternal()) {
//            val sampleDoc = Document("hello", "world1").apply { this["num"] = 1 }
//
//            // Collection should start out empty
//            // This also tests the null return format
//            assertNull(findOneAndDelete(Document()).get())
//
//            // Insert a sample Document
//            insertOne(sampleDoc).get()
//            assertEquals(1, count().get())
//
//            // Sample call to findOneAndDelete() where we delete the only doc in the collection
//            assertEquals(sampleDoc.withoutId(),
//                findOneAndDelete(Document()).get()!!.withoutId())
//
//            // There should be no documents in the collection now
//            assertEquals(0, count().get())
//
//            // Insert a sample Document
//            insertOne(sampleDoc).get()
//            assertEquals(1, count().get())
//
//            // Call findOneAndDelete() again but this time with a filter
//            assertEquals(sampleDoc.withoutId(),
//                findOneAndDelete(Document("hello", "world1")).get()!!.withoutId())
//
//            // There should be no documents in the collection now
//            assertEquals(0, count().get())
//
//            // Insert a sample Document
//            insertOne(sampleDoc).get()
//            assertEquals(1, count().get())
//
//            // Test null behaviour again with a filter that should not match any documents
//            assertNull(findOneAndDelete(Document("hello", "zzzzz")).get())
//            assertEquals(1, count().get())
//
//            val doc2 = Document("hello", "world2").apply { this["num"] = 2 }
//            val doc3 = Document("hello", "world3").apply { this["num"] = 3 }
//
//            // Insert new documents
//            insertMany(listOf(doc2, doc3)).get()
//            assertEquals(3, count().get())
//        }
//    }
//
//    @Test
//    fun findOneAndDelete_withProjectionAndSort() {
//        with(getCollectionInternal()) {
//            insertMany(listOf(
//                Document(mapOf(Pair("team", "Fearful Mallards"), Pair("score", 25000))),
//                Document(mapOf(Pair("team", "Tactful Mooses"), Pair("score", 23500))),
//                Document(mapOf(Pair("team", "Aquatic Ponies"), Pair("score", 19250))),
//                Document(mapOf(Pair("team", "Cuddly Zebras"), Pair("score", 15235))),
//                Document(mapOf(Pair("team", "Garrulous Bears"), Pair("score", 18000)))
//            )).get()
//
//            assertEquals(5, count().get())
//            assertNotNull(findOne(Document("team", "Cuddly Zebras")))
//
//            // Project: team, hide _id; Sort: score ascending
//            val project = Document(mapOf(Pair("_id", 0), Pair("team", 1)))
//            val sort = Document("score", 1)
//
//            // This results in the deletion of Cuddly Zebras
//            val deletedDocument = findOneAndDelete(
//                Document("score", Document("\$lt", 22250)),
//                FindOneAndModifyOptions()
//                    .projection(project)
//                    .sort(sort)
//            ).get()
//
//            assertEquals(4, count().get())
//            assertEquals(Document("team", "Cuddly Zebras"), deletedDocument.withoutId())
//            assertNull(findOne(Document("team", "Cuddly Zebras")).get())
//        }
//    }
}

inline fun <reified T : BaseRealmObject, K> MongoCollectionTests.collection(clazz: KClass<T>): MongoCollection<T, K> {
    return when (this) {
        is MongoCollectionFromDatabaseTests -> database.collection<T, K>(T::class.simpleName!!)
        is MongoCollectionFromClientTests -> client.collection()
    }
}

@Suppress("name")
@OptIn(ExperimentalKBsonSerializerApi::class)
inline operator fun <reified T : Any> BsonDocument.Companion.invoke(
    key: String,
    value: T,
): BsonDocument {
    return BsonDocument(key to EJson.Default.encodeToBsonValue(value))
}

@Serializable
class Unknown : RealmObject {
    var name = "Unknown"
}

// Strictly defined in schema in TestAppInitializer.kt
@Serializable
class SyncDog(var name: String) : RealmObject {
    constructor() : this("Default")
}

@Serializable
class CollectionDataType(var name: String? = "Default", var _id: Int? = Random.nextInt()) : RealmObject {
    constructor() : this("Default")
}

@PersistedName("CollectionDataType")
class CustomDataType(var name: String) : RealmObject {
    @Suppress("unused")
    constructor() : this("Default")
}

@Serializable
class NonSchemaType : RealmObject {
    var name: String = "Default"
}

class CustomIdType(val id: ObjectId)

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
//    ignoreUnknownKeys = false,
    serializersModule = SerializersModule {
        contextual(CustomDataType::class, CustomDataTypeSerializer())
        contextual(CustomIdType::class, CustomIdSerializer())
    }
)
