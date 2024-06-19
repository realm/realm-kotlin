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
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.mongodb.ext.collection
import io.realm.kotlin.mongodb.ext.insertOne
import io.realm.kotlin.mongodb.mongo.MongoClient
import io.realm.kotlin.mongodb.mongo.realmSerializerModule
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.util.DefaultFlexibleSyncAppInitializer
import kotlinx.serialization.SerializationException
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal const val TEST_SERVICE_NAME = "BackingDB"

@OptIn(ExperimentalKBsonSerializerApi::class)
class MongoClientTests {

    lateinit var app: TestApp
    lateinit var client: MongoClient

    @BeforeTest
    fun setUp() {
        app = TestApp(
            this::class.simpleName,
            DefaultFlexibleSyncAppInitializer,
        )
        val user = app.createUserAndLogin()
        client = user.mongoClient(TEST_SERVICE_NAME, eJson = EJson(serializersModule = realmSerializerModule(setOf(CollectionDataType::class))))
    }

    @AfterTest
    fun teadDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun properties() {
        assertEquals(TEST_SERVICE_NAME, client.serviceName)
    }

    @Test
    fun database_defaultSerializer() = runBlocking<Unit> {
        assertIs<Int>(client.database(app.clientAppId).collection<CollectionDataType>("CollectionDataType").insertOne(CollectionDataType("object-1")))
    }

    @Test
    @OptIn(ExperimentalKBsonSerializerApi::class)
    fun database_customSerializer() = runBlocking<Unit> {
        val collectionWithDefaultSerializer = client.database(app.clientAppId)
            .collection<CustomDataType>("CollectionDataType")
        assertFailsWithMessage<SerializationException>("Serializer for class 'CustomDataType' is not found.") {
            collectionWithDefaultSerializer.insertOne(CustomDataType("object-1"))
        }
        val collectionWithCustomSerializer = client.database(app.clientAppId, customEjsonSerializer)
            .collection<CustomDataType>("CollectionDataType")
        assertIs<Int>(collectionWithCustomSerializer.insertOne(CustomDataType("object-1")))
    }

    @Test
    fun database_createsCollectionOnInsertToUnknownDatabase() = runBlocking<Unit> {
        val database = client.database("Unknown")
        val collection = database.collection<CollectionDataType>("NewCollection")
        assertIs<Int>(collection.insertOne<CollectionDataType>(CollectionDataType("object-1")) as Int)
    }

    @Test
    fun collection_defaultSerializer() = runBlocking<Unit> {
        assertIs<Int>(client.collection<CollectionDataType>().insertOne(CollectionDataType("object-1")))
    }

    @Test
    fun collection_customSerializer() = runBlocking<Unit> {
        val collectionWithDefaultSerializer = client.collection<CollectionDataType>()
        assertFailsWithMessage<SerializationException>("Serializer for class 'CustomDataType' is not found.") {
            collectionWithDefaultSerializer.withDocumentClass<CustomDataType>().insertOne(CustomDataType("object-1"))
        }

        val collectionWithCustomSerializer = client.collection<CustomDataType>(
            customEjsonSerializer
        )
        assertIs<Int>(
            collectionWithCustomSerializer.insertOne(
                CustomDataType("object-1")
            )
        )
    }

    @Test
    fun collection_unknownSchemaType() = runBlocking<Unit> {
        val collectionWithDefaultSerializer = client.collection<NonSchemaType>()
        assertFailsWithMessage<ServiceException>("no matching collection found that maps to a table with title \"NonSchemaType\".") {
            collectionWithDefaultSerializer.insertOne(NonSchemaType())
        }
    }
}
