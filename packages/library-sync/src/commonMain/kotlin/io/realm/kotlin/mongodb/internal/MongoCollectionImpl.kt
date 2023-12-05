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

package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.annotations.ExperimentalRealmSerializerApi
import io.realm.kotlin.mongodb.ext.call
import io.realm.kotlin.mongodb.mongo.MongoCollection
import io.realm.kotlin.mongodb.mongo.insertMany
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonInt64
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson
import org.mongodb.kbson.serialization.decodeFromBsonValue
import org.mongodb.kbson.serialization.encodeToBsonValue

@OptIn(ExperimentalKBsonSerializerApi::class, ExperimentalRealmSerializerApi::class)
@PublishedApi
internal class MongoCollectionImpl<T, K> @OptIn(ExperimentalKBsonSerializerApi::class) constructor(
    @PublishedApi internal val database: MongoDatabaseImpl,
    override val name: String,
    val eJson: EJson,
): MongoCollection<T, K> {

    val client = this.database.client
    val user = client.user
    val functions = user.functions


    val defaults: Map<String, BsonValue> = mapOf(
            "database" to BsonString(database.name),
            "collection" to BsonString(name),
        )

    @ExperimentalRealmSerializerApi
    private suspend inline fun <reified R> call(name: String, crossinline document: MutableMap<String, BsonValue>.()-> Unit): R {
        return user.functions.call(name) {
            serviceName(client.serviceName)
            val doc = defaults.toMutableMap()
            document(doc)
            add(doc)
        }
    }

    @PublishedApi
    internal suspend fun count(filter: BsonDocument? = null, limit: Long? = null): Long {
        return call("count") {
            filter?.let { put("query", it) }
            limit?.let { put("limit", BsonInt64(it)) }
        }
    }

    @PublishedApi
    internal suspend fun findOne(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null): BsonValue =
        call("findOne") {
            filter?.let { put("query", it) }
            projection?.let { put("projection", it) }
            sort?.let { put("sort", it) }
        }

    @PublishedApi
    internal suspend fun find(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null, limit: Long? = null): BsonValue =
        call("find") {
            filter?.let { put("query", it) }
            projection?.let { put("projection", it) }
            sort?.let { put("sort", it) }
            limit?.let { put("limit", BsonInt64(it)) }
        }

    @PublishedApi
    internal suspend fun aggregate(pipeline: List<BsonDocument>): List<BsonValue> =
        call<BsonValue>("aggregate") { put("pipeline", BsonArray(pipeline)) }.asArray().toList()

    @PublishedApi
    internal suspend fun insertOne(document: BsonDocument): BsonValue =
        call<BsonValue>("insertOne") { put("document", document) }.asDocument()["insertedId"]!!

    @PublishedApi
    internal suspend fun insertMany(documents: List<BsonDocument>): List<BsonValue> =
        call<BsonValue>("insertMany") {
            put("documents", BsonArray(documents))
        }.asDocument().get("insertedIds")!!.asArray().toList()
}

@OptIn(ExperimentalKBsonSerializerApi::class)
@PublishedApi
internal inline fun <reified R> MongoCollectionImpl<*, *>.decodeFromBsonValue(bsonValue: BsonValue): R {
    return eJson.decodeFromBsonValue(bsonValue)
}
@OptIn(ExperimentalKBsonSerializerApi::class)
@PublishedApi
internal inline fun <reified R : Any> MongoCollectionImpl<*, *>.encodeToBsonValue(value: R): BsonValue {
    return eJson.encodeToBsonValue(value)
}
