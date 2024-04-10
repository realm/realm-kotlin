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

package io.realm.kotlin.test.mongodb.common.mongo

import io.realm.kotlin.Realm
import io.realm.kotlin.entities.sync.COLLECTION_SCHEMAS
import io.realm.kotlin.entities.sync.ChildCollectionDataType
import io.realm.kotlin.entities.sync.CollectionDataType
import io.realm.kotlin.entities.sync.EmbeddedChildCollectionDataType
import io.realm.kotlin.entities.sync.ParentCollectionDataType
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.mongodb.ext.aggregate
import io.realm.kotlin.mongodb.ext.collection
import io.realm.kotlin.mongodb.ext.count
import io.realm.kotlin.mongodb.ext.deleteMany
import io.realm.kotlin.mongodb.ext.deleteOne
import io.realm.kotlin.mongodb.ext.find
import io.realm.kotlin.mongodb.ext.findOne
import io.realm.kotlin.mongodb.ext.findOneAndDelete
import io.realm.kotlin.mongodb.ext.findOneAndReplace
import io.realm.kotlin.mongodb.ext.findOneAndUpdate
import io.realm.kotlin.mongodb.ext.insertMany
import io.realm.kotlin.mongodb.ext.insertOne
import io.realm.kotlin.mongodb.ext.updateMany
import io.realm.kotlin.mongodb.ext.updateOne
import io.realm.kotlin.mongodb.mongo.MongoClient
import io.realm.kotlin.mongodb.mongo.MongoCollection
import io.realm.kotlin.mongodb.mongo.MongoDatabase
import io.realm.kotlin.mongodb.mongo.realmSerializerModule
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.common.FLEXIBLE_SYNC_SCHEMA
import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PersistedName
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Test class that verifies MongoDB Client API interactions through the
 * `MongoClient.database(...).collection(...)`-API.
 */
class MongoCollectionFromDatabaseTests : MongoCollectionTests() {

    lateinit var database: MongoDatabase

    @BeforeTest
    override fun setUp() {
        super.setUp()
        val databaseName = app.configuration.appId
        @OptIn(ExperimentalKBsonSerializerApi::class)
        database = client.database(databaseName)
        @OptIn(ExperimentalKBsonSerializerApi::class)
        collection = collection()
    }

    @Test
    override fun findOne_unknownCollection() = runBlocking<Unit> {
        // Unknown collections will create the collection if inserting document, so only use
        // NonSchemaType for queries
        @OptIn(ExperimentalKBsonSerializerApi::class)
        val unknownCollection = collection<NonSchemaType, BsonValue>()
        assertNull(unknownCollection.findOne())
    }
}

/**
 * Test class that verifies MongoDB Client API interactions through the
 * `MongoClient.collection(...)`-API.
 */
class MongoCollectionFromClientTests : MongoCollectionTests() {

    @BeforeTest
    override fun setUp() {
        super.setUp()
        @OptIn(ExperimentalKBsonSerializerApi::class)
        collection = collection()
    }

    @Test
    fun name_persistedName() {
        @OptIn(ExperimentalKBsonSerializerApi::class)
        assertEquals("CollectionDataType", client.collection<CustomDataType, ObjectId>().name)
    }

    @OptIn(ExperimentalKBsonSerializerApi::class)
    @Test
    override fun findOne_unknownCollection() = runBlocking<Unit> {
        @OptIn(ExperimentalKBsonSerializerApi::class)
        val unknownCollection = collection<NonSchemaType, BsonValue>()
        assertFailsWithMessage<ServiceException>("no matching collection found that maps to a table with title \"NonSchemaType\"") {
            unknownCollection.findOne()
        }
    }
}

sealed class MongoCollectionTests {

    lateinit var app: TestApp
    lateinit var user: User
    lateinit var client: MongoClient
    lateinit var collection: MongoCollection<CollectionDataType, Int>

    @BeforeTest
    open fun setUp() {
        app = TestApp(
            this::class.simpleName,
            TEST_APP_FLEX,
        )

        app.asTestApp.run {
            runBlocking {
                COLLECTION_SCHEMAS.forEach {
                    deleteDocuments(app.configuration.appId, it.simpleName!!, "{}")
                }
            }
        }
        user = app.createUserAndLogin()
        @OptIn(ExperimentalKBsonSerializerApi::class)
        client = user.mongoClient(
            TEST_SERVICE_NAME,
            EJson(
                serializersModule = realmSerializerModule(
                    setOf(
                        ParentCollectionDataType::class,
                        ChildCollectionDataType::class
                    )
                )
            )
        )

    }

    @AfterTest
    fun teadDown() {
        app.asTestApp.run {
            runBlocking {
                COLLECTION_SCHEMAS.forEach {
                    deleteDocuments(app.configuration.appId, it.simpleName!!, "{}")
                }
            }
        }
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun name() {
        assertEquals("CollectionDataType", collection.name)
    }

    @Test
    open fun withDocumentClass() = runBlocking<Unit> {
        // Original typing
        assertIs<Int>(collection.insertOne(CollectionDataType("object-1", Random.nextInt())))
        assertIs<CollectionDataType>(collection.findOne())

        // Reshaped
        @OptIn(ExperimentalKBsonSerializerApi::class)
        val bsonCollection: MongoCollection<BsonDocument, BsonValue> = collection.withDocumentClass()
        assertIs<BsonValue>(bsonCollection.insertOne(BsonDocument("_id" to BsonInt32(Random.nextInt()), "name" to BsonString("object-2"))))
        assertIs<BsonDocument>(bsonCollection.findOne())
        assertEquals(2, bsonCollection.count())
    }

    @Test
    fun withDocumentClass_withCustomSerialization() = runBlocking<Unit> {
        @OptIn(ExperimentalKBsonSerializerApi::class)
        val reshapedCollectionWithDefaultSerializer: MongoCollection<CustomDataType, CustomIdType> =
            collection.withDocumentClass()

        assertFailsWithMessage<SerializationException>("Serializer for class 'CustomDataType' is not found.") {
            reshapedCollectionWithDefaultSerializer.insertOne(CustomDataType("object-2"))
        }

        @OptIn(ExperimentalKBsonSerializerApi::class)
        val reshapedCollectionWithCustomSerializer: MongoCollection<CustomDataType, CustomIdType> =
            collection.withDocumentClass(customEjsonSerializer)

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
        collection.findOne<CollectionDataType>(
            filter = BsonDocument("name", "object-6"),
            projection = BsonDocument("""{ "name" : 0}""")
        ).run {
            assertIs<CollectionDataType>(this)
            assertEquals("Default", this.name)
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
    open fun findOne_links() = runBlocking<Unit> {
        Realm.open(
            SyncConfiguration.Builder(user, COLLECTION_SCHEMAS)
                .initialSubscriptions {
                    add(it.query<ParentCollectionDataType>())
                    add(it.query<ChildCollectionDataType>())
                }
                .build()
        ).use {
            val syncedParent = it.write {
                copyToRealm(ParentCollectionDataType().apply { child = ChildCollectionDataType() })
            }
            // We need to upload schema before proceeding
            it.syncSession.uploadAllLocalChanges(30.seconds)
            // The translator should have some time to integrate the synced data
            delay(5.seconds)

            @OptIn(ExperimentalKBsonSerializerApi::class)
            val mongoDBClientParent = collection<ParentCollectionDataType, String>().findOne()
            assertEquals(syncedParent._id, mongoDBClientParent!!._id)
            assertEquals(syncedParent.child!!._id, mongoDBClientParent!!.child!!._id)
        }
    }

    @Test
    open fun findOne_typedLinks() = runBlocking<Unit> {
        Realm.open(
            SyncConfiguration.Builder(user, COLLECTION_SCHEMAS)
                .initialSubscriptions {
                    add(it.query<ParentCollectionDataType>())
                    add(it.query<ChildCollectionDataType>())
                }
                .build()
        ).use {
            val syncedParent = it.write {
                copyToRealm(
                    ParentCollectionDataType().apply {
                        any = RealmAny.create(ChildCollectionDataType())
                    }
                )
            }
            // We need to upload schema before proceeding
            it.syncSession.uploadAllLocalChanges(30.seconds)
            // The translator should have some time to integrate the synced data
            delay(5.seconds)

            @OptIn(ExperimentalKBsonSerializerApi::class)
            val mongoDBClientParent = collection<ParentCollectionDataType, String>().findOne()
            assertEquals(syncedParent._id, mongoDBClientParent!!._id)
            assertEquals(syncedParent.any!!.asRealmObject<ChildCollectionDataType>()._id, mongoDBClientParent.any!!.asRealmObject<ChildCollectionDataType>()._id)
        }
    }

    @Test
    open fun findOne_typedLinks_throwsOnMissingTargetSchema() = runBlocking<Unit> {
        Realm.open(
            SyncConfiguration.Builder(user, FLEXIBLE_SYNC_SCHEMA)
                .initialSubscriptions {
                    add(it.query<ParentCollectionDataType>())
                    add(it.query<ChildCollectionDataType>())
                }
                .build()
        ).use {
            val syncedParent = it.write {
                copyToRealm(
                    ParentCollectionDataType().apply {
                        any = RealmAny.create(ChildCollectionDataType())
                    }
                )
            }
            // We need to upload schema before proceeding
            it.syncSession.uploadAllLocalChanges(30.seconds)
            // The translator should have some time to integrate the synced data
            delay(3.seconds)

            @OptIn(ExperimentalKBsonSerializerApi::class)
            val parentCollection = collection<ParentCollectionDataType, String>(
                EJson(
                    serializersModule = realmSerializerModule(
                        setOf(ParentCollectionDataType::class)
                    )
                )
            )

            assertFailsWithMessage<SerializationException>("Cannot resolve target class in schema: Unknown class '${"$"}ref=ChildCollectionDataType'") {
                parentCollection.findOne()
            }
        }
    }

    @OptIn(ExperimentalKBsonSerializerApi::class)
    @Test
    open fun findOne_embeddedObjects() = runBlocking<Unit> {
        // Empty collections
        assertNull(collection.findOne())

        val parentCollection = collection<ParentCollectionDataType, ObjectId>()
        parentCollection.insertOne(
            ParentCollectionDataType().apply {
                embeddedChild = EmbeddedChildCollectionDataType().apply { name = "EMBEDDED-NAME" }
            }
        )

        parentCollection.find().single().run {
            assertEquals("EMBEDDED-NAME", embeddedChild!!.name)
        }
    }

    @Test
    fun findOne_extraFieldsAreDiscarded() = runBlocking<Unit> {
        collection.insertOne<BsonDocument, Int>(
            BsonDocument(
                mapOf(
                    "_id" to BsonInt32(Random.nextInt()),
                    "name" to BsonString("object-1"),
                    "extra" to BsonString("extra"),
                )
            )
        )

        // Show that remote method returns extra properties
        collection.findOne<BsonDocument>()!!.let {
            assertEquals("extra", it["extra"]!!.asString().value)
        }
        // But these properties are silently discarded by the serialization framework
        assertIs<CollectionDataType>(collection.findOne())
    }

    @Test
    fun findOne_missingFieldsGetsDefaults() = runBlocking<Unit> {
        collection.insertOne(CollectionDataType("object-1"))
        collection.findOne<CollectionDataType>(
            projection = BsonDocument("""{ "name" : 0}""")
        ).run {
            assertIs<CollectionDataType>(this)
            assertEquals("Default", this.name)
        }
    }

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
                assertEquals("Default", it.name)
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
                assertEquals("object-1", it.name)
                // Objects have new ids
                assertFalse(it._id in ids)
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
        collection.insertMany<BsonDocument, BsonValue>(names.map { BsonDocument("_id" to BsonInt32(Random.nextInt()), "name" to BsonString(it)) })

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
    @OptIn(ExperimentalKBsonSerializerApi::class)
    open fun insertOne_links() = runBlocking<Unit> {
        // Open a synced realm and verified that the linked entities we upload through the
        Realm.open(
            SyncConfiguration.Builder(user, FLEXIBLE_SYNC_SCHEMA)
                .initialSubscriptions {
                    add(it.query<ParentCollectionDataType>())
                    add(it.query<ChildCollectionDataType>())
                }
                .build()
        ).use { realm ->
            // We need to upload schema before proceeding
            realm.syncSession.uploadAllLocalChanges()

            // We set up listeners to be able to react on when the objects are seen in the synced realm
            val childChannel = Channel<ResultsChange<ChildCollectionDataType>>(10)
            val childListener =
                async { realm.query<ChildCollectionDataType>().asFlow().collect { childChannel.send(it) } }
            childChannel.receiveOrFail(message = "Didn't receive initial value").let {
                assertTrue { it.list.isEmpty() }
            }
            val parentChannel = Channel<ResultsChange<ParentCollectionDataType>>(10)
            val parentListener =
                async { realm.query<ParentCollectionDataType>().asFlow().collect { parentChannel.send(it) } }
            parentChannel.receiveOrFail(message = "Didn't receive initial value").let {
                assertTrue { it.list.isEmpty() }
            }

            val childCollection = collection<ChildCollectionDataType, ObjectId>()
            val unmanagedChild = ChildCollectionDataType()
            assertEquals(unmanagedChild._id, childCollection.insertOne(unmanagedChild))
            // We can't rely on the translator to incorporate the insertOnes in order so we need to
            // assure that the child is actually added before verifying the link in the parent.
            childChannel.receiveOrFail(message = "Didn't receive initial value").let {
                assertEquals(unmanagedChild._id, it.list.first()._id)
            }

            val parentCollection = collection<ParentCollectionDataType, ObjectId>()
            val unmanagedParent = ParentCollectionDataType().apply {
                this.child = unmanagedChild
            }
            val actual = parentCollection.insertOne(unmanagedParent)
            assertEquals(unmanagedParent._id, actual)

            // Verifying that the parent include the correct link
            parentChannel.receiveOrFail(
                timeout = 5.seconds,
                message = "Didn't receive update value"
            ).let {
                val parent = it.list.first()
                assertEquals(unmanagedParent._id, parent._id)
                parent!!.child!!.let {
                    assertEquals(unmanagedChild._id, it._id)
                    assertEquals(unmanagedChild.name, it.name)
                }
            }
            parentListener.cancel()
            childListener.cancel()
        }
    }

    @Test
    @OptIn(ExperimentalKBsonSerializerApi::class)
    open fun insertOne_typedLinks() = runBlocking<Unit> {
        // Open a synced realm and verified that the linked entities we upload through the
        Realm.open(
            SyncConfiguration.Builder(user, FLEXIBLE_SYNC_SCHEMA)
                .initialSubscriptions {
                    add(it.query<ParentCollectionDataType>())
                    add(it.query<ChildCollectionDataType>())
                }
                .build()
        ).use { realm ->
            // We need to upload schema before proceeding
            realm.syncSession.uploadAllLocalChanges()

            // We set up listeners to be able to react on when the objects are seen in the synced realm
            val childChannel = Channel<ResultsChange<ChildCollectionDataType>>(10)
            val childListener =
                async { realm.query<ChildCollectionDataType>().asFlow().collect { childChannel.send(it) } }
            childChannel.receiveOrFail(message = "Didn't receive initial value").let {
                assertTrue { it.list.isEmpty() }
            }
            val parentChannel = Channel<ResultsChange<ParentCollectionDataType>>(10)
            val parentListener =
                async { realm.query<ParentCollectionDataType>().asFlow().collect { parentChannel.send(it) } }
            parentChannel.receiveOrFail(message = "Didn't receive initial value").let {
                assertTrue { it.list.isEmpty() }
            }

            val childCollection = collection<ChildCollectionDataType, ObjectId>()
            assertEquals(0, childCollection.find().size)
            val unmanagedChild = ChildCollectionDataType()
            RealmLog.level = LogLevel.ALL
            assertEquals(unmanagedChild._id, childCollection.insertOne(unmanagedChild))
            // We can't rely on the translator to incorporate the insertOnes in order so we need to
            // assure that the child is actually added before verifying the link in the parent.
            childChannel.receiveOrFail(message = "Didn't receive initial value").let {
                assertEquals(unmanagedChild._id, it.list.first()._id)
            }

            val parentCollection = collection<ParentCollectionDataType, ObjectId>()
            val unmanagedParent = ParentCollectionDataType().apply {
                this.any = RealmAny.create(unmanagedChild)
            }

            val actual = parentCollection.insertOne(unmanagedParent)
            assertEquals(unmanagedParent._id, actual)

            // Verifying that the parent include the correct link
            parentChannel.receiveOrFail(
                timeout = 5.seconds,
                message = "Didn't receive update value"
            ).let {
                val parent = it.list.first()
                assertEquals(unmanagedParent._id, parent._id)
                parent.any!!.asRealmObject<ChildCollectionDataType>().let {
                    assertEquals(unmanagedChild._id, it._id)
                    assertEquals(unmanagedChild.name, it.name)
                }
            }
            parentListener.cancel()
            childListener.cancel()
        }
    }

    @OptIn(ExperimentalKBsonSerializerApi::class)
    @Test
    fun insertOne_embeddedObjects() = runBlocking<Unit> {
        val parentCollection = collection<ParentCollectionDataType, ObjectId>()
        // Empty collections
        assertNull(parentCollection.findOne())
        parentCollection.insertOne(
            ParentCollectionDataType().apply {
                embeddedChild = EmbeddedChildCollectionDataType().apply { name = "EMBEDDED-NAME" }
            }
        )

        parentCollection.find().single().run {
            assertEquals("EMBEDDED-NAME", embeddedChild!!.name)
        }
    }

    @Test
    fun insertOne_explicitTypes() = runBlocking<Unit> {
        assertEquals(0, collection.find().size)
        // Inserting document without _id will use ObjectId as _id
        collection.insertOne<BsonDocument, BsonValue>(BsonDocument("_id" to BsonInt32(Random.nextInt()), "name" to BsonString("object-1"))).let {
            assertIs<BsonInt32>(it)
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
    open fun insertOne_throwsOnMissingRequiredFields() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("insert not permitted") {
            collection.insertOne<BsonDocument, BsonValue>(BsonDocument("_id", ObjectId()))
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

        collection.insertMany<BsonDocument, BsonValue>(
            (1..10).map {
                BsonDocument("_id" to BsonInt32(Random.nextInt()), "name" to BsonString("object-${it % 5}"))
            }
        ).let {
            assertEquals(10, it.size)
            it.forEach {
                assertIs<BsonInt32>(it)
            }
        }
        assertEquals(10, collection.find<BsonDocument>().size)
    }

    // InsertMany with links

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
    open fun insertMany_throwsOnMissingRequiredFields() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("insert not permitted") {
            collection.insertMany<BsonDocument, BsonValue>(listOf(BsonDocument()))
        }
    }

    @Test
    fun insertMany_throwsOnTypeMismatch() = runBlocking<Unit> {
        assertFailsWithMessage<ServiceException>("insert not permitted") {
            collection.insertMany<BsonDocument, ObjectId>(listOf(BsonDocument(mapOf("_id" to ObjectId(), "name" to BsonString("object-1")))))
        }
    }

    @Test
    fun deleteOne() = runBlocking {
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
        collection.updateOne(
            BsonDocument("""{ "name": "NOMATCH"}"""),
            BsonDocument("\$set", BsonDocument("""{ "name": "UPDATED"}""")),
        ).let { (updated, upsertedId) ->
            assertFalse(updated)
            assertNull(upsertedId)
        }

        // Update with match match
        collection.updateOne(
            BsonDocument("""{ "name": "object-1"}"""),
            BsonDocument("\$set", BsonDocument("""{ "name": "object-2"}""")),
        ).let { (updated, upsertedId) ->
            assertTrue(updated)
            assertNull(upsertedId)
        }
        assertEquals(4, collection.count())
        assertEquals(3, collection.count(filter = BsonDocument("""{"name": "object-1"}""")))
        assertEquals(1, collection.count(filter = BsonDocument("""{"name": "object-2"}""")))

        // Upsert no match
        collection.updateOne(
            BsonDocument("""{ "name": "object-3"}"""), BsonDocument(""" { "name": "object-2", "_id" : ${Random.nextInt()}}"""), upsert = true
        ).let { (updated, upsertedId) ->
            assertFalse(updated)
            assertIs<Int>(upsertedId)
        }
        assertEquals(5, collection.count())
        assertEquals(2, collection.count(filter = BsonDocument("""{"name": "object-2"}""")))

        // Upsert with match
        collection.updateOne(
            BsonDocument("""{ "name": "object-2"}"""), BsonDocument(""" { "name": "object-3"}"""), upsert = true
        ).let { (updated, upsertedId) ->
            assertTrue(updated)
            assertNull(upsertedId)
        }
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
        collection.updateMany(
            BsonDocument("""{"name": "NOMATCH"}"""),
            BsonDocument("""{"name": "UPDATED"}"""),
        ).let { (modifiedCount, upsertedId) ->
            assertEquals(0L, modifiedCount)
            assertNull(upsertedId)
        }
        assertEquals(0, collection.count(filter = BsonDocument("""{"name": "UPDATED"}""")))
        assertEquals(4, collection.count())

        // Update with match
        collection.updateMany(
            BsonDocument("""{ "name": "x"}"""),
            BsonDocument("""{ "name": "UPDATED"}"""),
        ).let { (modifiedCount, upsertedId) ->
            assertEquals(2L, modifiedCount)
            assertNull(upsertedId)
        }
        assertEquals(2, collection.count(filter = BsonDocument("""{"name": "UPDATED"}""")))
        assertEquals(4, collection.count())

        // Upsert no match
        collection.updateMany(
            BsonDocument("""{ "name": "NOMATCH"}"""),
            BsonDocument(""" { "name": "UPSERTED", "_id" : ${Random.nextInt()}}"""),
            upsert = true
        ).let { (modifiedCount, upsertedId) ->
            assertEquals(0L, modifiedCount)
            assertIs<Int>(upsertedId)
        }
        assertEquals(5, collection.count())
        assertEquals(1, collection.count(filter = BsonDocument("""{"name": "UPSERTED"}""")))

        // Upsert with match
        collection.updateMany(
            BsonDocument("""{ "name": "y"}"""), BsonDocument(""" { "name": "z"}"""), upsert = true
        ).let { (modifiedCount, upsertedId) ->
            assertEquals(1L, modifiedCount)
            assertNull(upsertedId)
        }
        assertEquals(5, collection.count())
        assertEquals(0, collection.count(filter = BsonDocument("""{"name": "y"}""")))
    }

    @Test
    fun updateMany_explicitTypes() = runBlocking<Unit> {
        collection.updateMany<BsonValue>(
            BsonDocument("""{ "name": "object-3"}"""),
            BsonDocument(""" { "name": "object-2", "_id" : ${Random.nextInt()}}"""),
            upsert = true
        ).let { (modifiedCount, upsertedId) ->
            assertEquals(0, modifiedCount)
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
            assertEquals("Default", this.name)
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
            // We don't know the id as the constructor default generated a new one
            assertFalse { this._id in ids }
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
            assertEquals("Default", this.name)
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
            // We don't know the id as the constructor default generated a new one
            assertFalse { this._id in ids }
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
            assertEquals("Default", this.name)
            // _id is included by default and matched one of the previously inserted objects
            assertTrue { this._id in ids }
        }
        // Project without _id, have to be explicitly excluded
        collection.findOneAndDelete(
            filter = BsonDocument("name", "object-3"),
            projection = BsonDocument("""{ "_id" : 0}""")
        )!!.run {
            assertEquals("object-3", name)
            // We don't know the id as the constructor default generated a new one
            assertFalse { this._id in ids }
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

    @Test
    fun throwsOnLoggedOutUser() = runBlocking<Unit> {
        user.logOut()
        assertFailsWithMessage<ServiceException>("unauthorized") {
            collection.findOne()
        }
    }
}

// Helper method to be able to differentiate collection creation across test classes
@OptIn(ExperimentalKBsonSerializerApi::class)
inline fun <reified T : BaseRealmObject, K> MongoCollectionTests.collection(eJson: EJson? = null): MongoCollection<T, K> {
    return when (this) {
        is MongoCollectionFromDatabaseTests -> database.collection(T::class.simpleName!!, eJson)
        is MongoCollectionFromClientTests -> client.collection(eJson)
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

// Distinct data type with same fields as the above CollectionDataType used to showcase injection
// of custom serializers.
@PersistedName("CollectionDataType")
class CustomDataType(var name: String, var _id: Int = Random.nextInt()) : RealmObject {
    @Suppress("unused")
    constructor() : this("Default")
}
// Custom Id type to showcase that we can use custom serializers for primary key return values.
class CustomIdType(val id: Int)

// Custom serializers to showcase that we can inject serializers throughout the MongoClient APIs.
class CustomDataTypeSerializer : KSerializer<CustomDataType> {

    val serializer = BsonValue.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor
    override fun deserialize(decoder: Decoder): CustomDataType {
        return decoder.decodeSerializableValue(serializer).let {
            val _id = it.asDocument()["_id"]!!.asInt32().value
            val name = it.asDocument()["name"]!!.asString().value
            CustomDataType(name, _id)
        }
    }

    override fun serialize(encoder: Encoder, value: CustomDataType) {
        val document = BsonDocument()
        document["_id"] = BsonInt32(value._id)
        document["name"] = BsonString(value.name)
        encoder.encodeSerializableValue(serializer, document)
    }
}
class CustomIdSerializer : KSerializer<CustomIdType> {
    val serializer = BsonInt32.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor
    override fun deserialize(decoder: Decoder): CustomIdType {
        return decoder.decodeSerializableValue(serializer).let {
            CustomIdType(it.asInt32().value)
        }
    }

    override fun serialize(encoder: Encoder, value: CustomIdType) {
        encoder.encodeSerializableValue(serializer, BsonInt32(value.id))
    }
}

@OptIn(ExperimentalKBsonSerializerApi::class)
val customEjsonSerializer = EJson(
    serializersModule = SerializersModule {
        contextual(CustomDataType::class, CustomDataTypeSerializer())
        contextual(CustomIdType::class, CustomIdSerializer())
    }
)
