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

import io.realm.kotlin.entities.sync.CollectionDataType
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.ext.insertOne
import io.realm.kotlin.mongodb.mongo.MongoClient
import io.realm.kotlin.mongodb.mongo.MongoDatabase
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import kotlinx.serialization.SerializationException
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonInt32
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalKBsonSerializerApi::class)
class MongoDatabaseTests {

    lateinit var app: TestApp
    lateinit var client: MongoClient
    lateinit var databaseName: String
    lateinit var database: MongoDatabase

    @BeforeTest
    fun setUp() {
        app = TestApp(
            this::class.simpleName,
            appName = TEST_APP_FLEX,
        )
        val user = app.createUserAndLogin()
        client = user.mongoClient(TEST_SERVICE_NAME)
        databaseName = app.clientAppId
        database = client.database(databaseName)
    }

    @AfterTest
    fun teadDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun properties() {
        assertEquals(databaseName, database.name)
    }

    @Test
    fun collection_defaultTypes() = runBlocking<Unit> {
        val collection = database.collection("CollectionDataType")
        val value = collection.insertOne(BsonDocument("_id" to BsonInt32(Random.nextInt()), "name" to BsonString("object-1")))
        assertIs<BsonValue>(value)
    }

    @Test
    fun collection_typed() = runBlocking<Unit> {
        val collection = database.collection<CollectionDataType, Int>("CollectionDataType")
        val value = collection.insertOne(CollectionDataType("object-1", Random.nextInt()))
        assertIs<Int>(value)
    }

    @Test
    fun collection_defaultSerializer() = runBlocking<Unit> {
        assertIs<Int>(database.collection<CollectionDataType, Int>("CollectionDataType").insertOne(CollectionDataType("object-1")))
    }

    @Test
    fun collection_customSerializer() = runBlocking<Unit> {
        val collectionWithDefaultSerializer = database.collection<CustomDataType, BsonValue>("CollectionDataType")
        assertFailsWithMessage<SerializationException>("Serializer for class 'CustomDataType' is not found.") {
            collectionWithDefaultSerializer.insertOne(CustomDataType("object-1"))
        }

        val collectionWithCustomSerializer = database.collection<CustomDataType, CustomIdType>("CollectionDataType", customEjsonSerializer)
        assertIs<CustomIdType>(collectionWithCustomSerializer.insertOne(CustomDataType("object-1")))
    }
}
