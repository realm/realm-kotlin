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

@file:OptIn(ExperimentalRealmSerializerApi::class, ExperimentalRealmSerializerApi::class)

package io.realm.kotlin.test.mongodb.common.mongo

import io.realm.kotlin.annotations.ExperimentalRealmSerializerApi
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.mongodb.mongo.MongoClient
import io.realm.kotlin.mongodb.mongo.MongoCollection
import io.realm.kotlin.mongodb.mongo.MongoDatabase
import io.realm.kotlin.mongodb.mongo.deleteMany
import io.realm.kotlin.mongodb.mongo.deleteOne
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
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.ObjectId
import org.mongodb.kbson.serialization.Bson
import org.mongodb.kbson.serialization.EJson
import org.mongodb.kbson.serialization.encodeToBsonValue
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SERVICE_NAME = "BackingDB"

@OptIn(ExperimentalRealmSerializerApi::class)
class MongoClientTest {

    lateinit var app: TestApp
    lateinit var client: MongoClient
    lateinit var database: MongoDatabase
    lateinit var collection: MongoCollection

    @BeforeTest
    fun setUp() {
        app = TestApp(
            this::class.simpleName,
            builder = { builder: AppConfiguration.Builder ->
                builder.httpLogObfuscator(null)
            }
        )
        val databaseName = app.configuration.appId

        app.asTestApp.run {
            runBlocking {
                deleteDocuments(databaseName, "SyncDog", "{}")
            }
        }
        val user = app.createUserAndLogin()
        client = user.mongoClient(SERVICE_NAME)
        database = client.database(databaseName)
        collection = database.collection("SyncDog")
    }

    @AfterTest
    fun teadDown() {
        RealmLog.level = LogLevel.WARN
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun properties() {
        assertEquals("SyncDog", collection.name)
        assertEquals(app.configuration.appId, database.name)
        assertEquals(SERVICE_NAME, client.serviceName)
    }

    @Test
    fun count() = runBlocking<Unit> {
        assertEquals(0, collection.count())

        app.asTestApp.run {
            (1..10).forEach {
                insertDocument(
                    "SyncDog",
                    Json.encodeToString(SyncDog.serializer(), SyncDog("dog-${it % 5}"))
                )
            }
            assertEquals(10, countDocuments("SyncDog"))
        }

        assertEquals(10, collection.count())
        assertEquals(5, collection.count(limit = 5))
        assertEquals(2, collection.count(filter = BsonDocument("name" to BsonString("dog-0"))))
        assertEquals(2, collection.count(filter = BsonDocument("name", "dog-0")))
        assertEquals(
            1,
            collection.count(filter = BsonDocument("name" to BsonString("dog-0")), limit = 1)
        )
    }

    @Test
    fun count_invalidFilter() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("operator") {
            collection.count(filter = BsonDocument("\$who", "dog-0"))
        }
    }

    @Test
    fun findOne() = runBlocking<Unit> {
        assertNull(collection.findOne<SyncDog>())

        app.asTestApp.run {
            (1..10).forEach {
                insertDocument(
                    "SyncDog",
                    Json.encodeToString(SyncDog.serializer(), SyncDog("dog-${it % 5}"))
                )
            }
            assertEquals(10, countDocuments("SyncDog"))
        }

        // No match
        assertNull(collection.findOne<SyncDog>(filter = BsonDocument("name", "cat")))

        // Only on document even though multiple matches
        assertIs<SyncDog>(collection.findOne<SyncDog>(filter = BsonDocument("name", "dog-0")))

        // Projection select field
        // Limit
        // Sort
    }

    @OptIn(ExperimentalKBsonSerializerApi::class)
//    @Test
//    fun findOne2() = runBlocking<Unit> {
//        val dog = SyncDog("dog-1")
//        val dog1 = collection.findOne<SyncDog>()
//        assertNull(dog1)
//
//        app.asTestApp.run {
//            (1..10).forEach {
//                insertDocument(
//                    "SyncDog",
//                    Json.encodeToString(SyncDog.serializer(), SyncDog("dog-${it % 5}"))
//                )
//            }
//            assertEquals(10, countDocuments("SyncDog"))
//        }
//
//        // No match
//        assertNull(collection.findOne<BsonDocument>(filter = BsonDocument("name" , "cat")))
//
//        // Only on document even though multiple matches
//        assertIs<SyncDog>(collection.findOne<SyncDog>(filter = BsonDocument("name", "dog-0")))
//
//        val dog2: SyncDog = collection.findOne {
//            filter("""{ "name": "dog-1" }""")
////            arg(SyncDog())
////            filter(BsonDocument("""{ "name": "dog-1" }"""))
////            filter(BsonDocument("asdf"))
////            projection(SyncDog::members)
//
////            sort()
//        }
//        assertEquals(dog, dog2)
//
//        val dog3: SyncDog = collection.findOne {
//            filter(BsonDocument("name" to BsonString("dog-1")))
////            arg(SyncDog())
////            filter(BsonDocument("""{ "name": "dog-1" }"""))
////            filter(BsonDocument("asdf"))
////            projection(SyncDog::members)
//
////            sort()
//        }
//        assertEquals(dog, dog3)
//
//        @Serializable
//        data class Filter(val name: String)
//
//        val dog4: SyncDog = collection.findOne {
//            filter(Filter("dog-1"))
////            arg(SyncDog())
////            filter(BsonDocument("""{ "name": "dog-1" }"""))
////            filter(BsonDocument("asdf"))
////            projection(SyncDog::members)
//
////            sort()
//        }
//        assertEquals(dog, dog4)
//
////        val dog5 = collection.findOne<SyncDog> {
////            query = """{ "name": "dog-1" }"""
////            query = BsonDocument("""{ "name": "dog-1" }""")
////        }
//    }
//        with(getCollectionInternal()) {
//            // Test findOne() on empty collection with no filter and no options
//            assertNull(findOne().get())
//
//            // Test findOne() with filter that does not match any documents and no options
//            assertNull(findOne(Document("hello", "worldDNE")).get())
//
//            val doc1 = Document("hello", "world1")
//            insertOne(doc1).get()
//            assertEquals(1, count().get())
//
//            // Test findOne() with filter that does not match any documents and no options
//            assertNull(findOne(Document("hello", "worldDNE")).get())
//        }
//    }
//
//    @Test
//    fun findOne_singleDocument() {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world1")
//
//            // Insert one document
//            insertOne(doc1).get()
//            assertEquals(1, count().get())
//
//            // No filter and no options
//            assertEquals(doc1, findOne().get()!!.withoutId())
//
//            // Projection (remove "_id") options
//            val projection = Document("hello", 1).apply { this["_id"] = 0 }
//            var options = FindOptions()
//                .limit(2)
//                .projection(projection)
//            assertEquals(doc1, findOne(Document(), options).get()!!)
//
//            // Projection (remove "_id") and sort (by desc "hello") options
//            options = FindOptions()
//                .limit(2)
//                .projection(projection)
//                .sort(Document("hello", -1))
//            assertEquals(doc1, findOne(Document(), options).get()!!)
//        }
//    }
//
//    @Test
//    fun findOne_multipleDocuments() {
//        with(getCollectionInternal()) {
//            val doc1 = Document("hello", "world1")
//            val doc2 = Document("hello", "world2")
//            val doc3 = Document("hello", "world3")
//
//            // Insert 3 documents
//            insertMany(listOf(doc1, doc2, doc3)).get()
//            assertEquals(3, count().get())
//
//            // Projection (remove "_id") and sort (by asc "hello") options
//            val projection = Document("hello", 1).apply { this["_id"] = 0 }
//            var options = FindOptions()
//                .limit(2)
//                .projection(projection)
//                .sort(Document("hello", 1))
//            assertEquals(doc1, findOne(Document(), options).get()!!)
//
//            // Projection (remove "_id") and sort (by desc "hello") options
//            options = FindOptions()
//                .limit(2)
//                .projection(projection)
//                .sort(Document("hello", -1))
//            assertEquals(doc3, findOne(Document(), options).get()!!)
//        }
//    }
//
//    @Test
//    fun findOne_fails() {
//        with(getCollectionInternal()) {
//            assertFailsWithErrorCode(ErrorCode.MONGODB_ERROR) {
//                findOne(Document("\$who", 1)).get()
//            }.also { e ->
//                assertTrue(e.errorMessage!!.contains("operator", true))
//            }
//        }
//    }
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
//    @Test
//    fun find_fails() {
//        with(getCollectionInternal()) {
//            assertFailsWithErrorCode(ErrorCode.MONGODB_ERROR) {
//                find(Document("\$who", 1)).first().get()
//            }.also { e ->
//                assertTrue(e.errorMessage!!.contains("operator", true))
//            }
//        }
//    }
//
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

        // Option 1 - Typed return value - BsonValue
        val insertedIdDocument: BsonValue = collection.insertOne(BsonDocument("name", "sadffds"))
        val insertedIdDocument2: BsonValue = collection.insertOne(SyncDog("sadf"))

        // Option 2 - Explicit generic arguments to enabling fluent API
        val id = collection.insertOne<SyncDog, BsonValue>(SyncDog("sadf"))
        println(id)

        // Option 3 - Automatically serialized object
        val x2: ObjectId = collection.insertOne(SyncDog("sadf"))
        println(x2)
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

    @Test
    fun insertMany() = runBlocking {
        RealmLog.level = LogLevel.ALL
        val x: List<BsonValue> = collection.insertMany(listOf(SyncDog("a")))
        val syncDogIntIdCollection = database.collection("SyncDogIntId")
        val elements = SyncDogIntId("a", Random.nextInt())
        val y: List<Int> = syncDogIntIdCollection.insertMany(listOf(elements))

        assertFailsWithMessage<ServiceException>("dup key") {
            val z: List<Int> = syncDogIntIdCollection.insertMany(listOf(elements))
        }
        println(x)
        println(y)

    }
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

        // A bit ann
        val elements = SyncDog("x")
        assertEquals(2, collection.insertMany<SyncDog, BsonValue>(listOf(elements, elements)).size)
        assertTrue { collection.deleteOne(BsonDocument("""{ "name": "x" }""")) }
    }

    //    @Test
//    fun deleteOne_singleDocument() {
//        with(getCollectionInternal()) {
//            assertEquals(0, deleteOne(Document()).get()!!.deletedCount)
//            assertEquals(0, deleteOne(Document("hello", "world")).get()!!.deletedCount)
//
//            val doc1 = Document("hello", "world")
//
//            insertOne(doc1).get()
//            assertEquals(1, deleteOne(doc1).get()!!.deletedCount)
//            assertEquals(0, count().get())
//        }
//    }
//
//    @Test
//    fun deleteOne_fails() {
//        with(getCollectionInternal()) {
//            assertFailsWithErrorCode(ErrorCode.MONGODB_ERROR) {
//                deleteOne(Document("\$who", 1)).get()
//            }.also { e ->
//                assertTrue(e.errorMessage!!.contains("operator", true))
//            }
//        }
//    }
//
//    @Test
//    fun deleteOne_multipleDocuments() {
//        with(getCollectionInternal()) {
//            assertEquals(0, count().get())
//
//            val rawDoc = Document("hello", "world")
//            val doc1 = Document(rawDoc)
//            val doc1b = Document(rawDoc)
//            val doc2 = Document("foo", "bar")
//            val doc3 = Document("42", "666")
//            insertMany(listOf(doc1, doc1b, doc2, doc3)).get()
//            assertEquals(1, deleteOne(rawDoc).get()!!.deletedCount)
//            assertEquals(1, deleteOne(Document()).get()!!.deletedCount)
//            assertEquals(2, count().get())
//        }
//    }
//
    @Test
    fun deleteMany() = runBlocking {
        // Argument wrapper DSL
        RealmLog.level = LogLevel.ALL
        assertEquals(0, collection.deleteMany(BsonDocument()))

        // A bit ann
        val elements = SyncDog("x")
        assertEquals(2, collection.insertMany<SyncDog, BsonValue>(listOf(elements, elements)).size)
        assertEquals(2, collection.deleteMany(BsonDocument("""{ "name": "x" }""")))

        assertEquals(
            3,
            collection.insertMany<SyncDog, BsonValue>(listOf(elements, elements, elements)).size
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
    fun updateOne() = runBlocking<Unit> {
        // Argument wrapper DSL
        RealmLog.level = LogLevel.ALL

        val elements = SyncDog("x")
        assertEquals(2, collection.insertMany<SyncDog, BsonValue>(listOf(elements, elements)).size)
        assertEquals(2, collection.count(filter = BsonDocument("""{"name": "x"}""")))

        // A bit ann
        collection.updateOne<BsonValue>(
            BsonDocument("""{ "name": "x"}"""),
            BsonDocument("""{ "name": "y"}"""),
            true
        )
//        assertEquals(2, collection.deleteMany(BsonDocument("""{ "name": "x" }""" )) )

//        assertEquals(3, collection.insertMany<SyncDog, BsonValue>(listOf(elements, elements, elements)).size)
//        assertEquals(3, collection.deleteMany(BsonDocument()) )

        collection.updateOne<BsonValue>(
            BsonDocument("""{ "name": "z"}"""),
            BsonDocument(""" { "name": "y"}"""),
            upsert = true
        )
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
        RealmLog.level = LogLevel.ALL
        assertEquals(
            4,
            collection.insertMany<SyncDog, BsonValue>(
                listOf(
                    SyncDog("x"),
                    SyncDog("x"),
                    SyncDog("y"),
                    SyncDog("z")
                )
            ).size
        )
        assertEquals(2, collection.count(filter = BsonDocument("""{"name": "x"}""")))
        collection.updateMany<BsonValue>(
            BsonDocument("""{"name": "x2"}"""),
            BsonDocument("""{"name": "x1"}"""),
            true
        )
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
        assertNull(collection.findOneAndUpdate<SyncDog>(BsonDocument(), BsonDocument()))
        collection.insertMany<SyncDog, ObjectId>(
            listOf(
                SyncDog("dog1"),
                SyncDog("dog1"),
                SyncDog("dog2")
            )
        )
        collection.findOneAndUpdate<SyncDog>(
            BsonDocument(),
            BsonDocument("""{ "name": "dog1" }"""),
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
        assertNull(collection.findOneAndReplace<SyncDog>(BsonDocument(), BsonDocument()))
        collection.insertMany<SyncDog, ObjectId>(
            listOf(
                SyncDog("dog1"),
                SyncDog("dog1"),
                SyncDog("dog2")
            )
        )
        val x = collection.findOneAndReplace<SyncDog>(
            BsonDocument(),
            BsonDocument("""{ "name": "dog1" }"""),
            upsert = true
        )
        println(x)
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
        assertNull(collection.findOneAndDelete<SyncDog>(BsonDocument(), BsonDocument()))
        collection.insertMany<SyncDog, ObjectId>(
            listOf(
                SyncDog("dog1"),
                SyncDog("dog1"),
                SyncDog("dog2")
            )
        )
        val x: SyncDog = collection.findOneAndDelete<SyncDog>(
            BsonDocument(),
            BsonDocument("""{ "name": "dog1" }"""),
            upsert = true
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

@Serializable
class SyncDog : RealmObject {
    constructor() {
        this.name = "Default"
    }

    constructor(name: String) {
        this.name = name
    }

    var name: String
}

@Serializable
data class SyncDogIntId(val name: String, val _id: Int)

//@OptIn(ExperimentalKBsonSerializerApi::class)
//inline fun <reified T : Any> EJson.BsonDocument(key: String, value: T): BsonDocument {
//    return BsonDocument(key to this.encodeToBsonValue(value))
//}
@OptIn(ExperimentalKBsonSerializerApi::class)
inline operator fun <reified T : Any> BsonDocument.Companion.invoke(
    key: String,
    value: T,
): BsonDocument {
    return BsonDocument(key to EJson.Default.encodeToBsonValue(value))
}

//fun BsonDocument2(vararg args: Pair<String, Any?>): BsonDocument =
//    BsonDocument(args.toMap().mapValues { anyToBsonValue(it.value) })
//private fun anyToBsonValue(value: Any?): BsonValue {
//return    when (value) {
//        // BsonNull -> TODO()
//        null -> BsonNull
//
//        // is BsonString -> TODO()
//        is String -> BsonString(value)
//
//        // is BsonBoolean -> TODO()
//        is Boolean -> BsonBoolean(value)
//
//        // is BsonNumber -> TODO()
//        is Int -> BsonInt32(value)
//        is Long -> BsonInt64(value)
//        is Double -> BsonDouble(value)
//
//        // is BsonObjectId -> TODO()
//        is ObjectId -> value
//        // is BsonDecimal128 -> TODO()
//        is Decimal128 -> value
//
//        // is BsonBinary -> TODO()
//        is ByteArray -> BsonBinary(value)
//
//        // is BsonTimestamp -> TODO()
//        is RealmInstant -> {
//            if (value.epochSeconds.let { it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }) {
//                BsonTimestamp(value.epochSeconds.toInt(), value.nanosecondsOfSecond)
//            } else throw IllegalArgumentException("Cannot represent RealmInstant in BsonTimestamp: $$value")
//        }
//
//        // is BsonArray -> TODO()
//        is List<*> -> BsonArray(value.map { anyToBsonValue(it) } )
//
//        // is BsonDocument -> TODO()
//        is Map<*, *> -> BsonDocument(value.mapValues { anyToBsonValue(it) } as Map<String, BsonValue>)
//        // is BsonDBPointer -> TODO()
//        // is BsonDateTime -> TODO()
//        // is BsonJavaScript -> TODO()
//        // is BsonJavaScriptWithScope -> TODO()
//        // BsonMaxKey -> TODO()
//        // BsonMinKey -> TODO()
//        // is BsonRegularExpression -> TODO()
//        // is BsonSymbol -> TODO()
//        // BsonUndefined -> TODO()
//        else -> TODO("$value ${value::class}")
//    }
//
//}
