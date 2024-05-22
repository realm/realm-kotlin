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

import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.mongodb.mongo.MongoCollection
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonBoolean
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonInt64
import org.mongodb.kbson.BsonNull
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonType
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson
import org.mongodb.kbson.serialization.decodeFromBsonValue
import org.mongodb.kbson.serialization.encodeToBsonValue

@PublishedApi
@OptIn(ExperimentalKBsonSerializerApi::class)
internal class MongoDatabaseCollection<T>(@PublishedApi internal val database: MongoDatabaseImpl, override val name: String, eJson: EJson) : MongoCollectionImpl<T>(database.client.functions, eJson) {
    override val defaults: Map<String, BsonValue> = mapOf(
        "database" to BsonString(database.name),
        "collection" to BsonString(name),
    )
    override fun <T> withDocumentClass(eJson: EJson?): MongoCollection<T> {
        return MongoDatabaseCollection(this.database, this.name, eJson ?: this.eJson)
    }
}

@PublishedApi
@OptIn(ExperimentalKBsonSerializerApi::class)
internal class MongoClientCollection<T>(@PublishedApi internal val clientImpl: MongoClientImpl, override val name: String, eJson: EJson) : MongoCollectionImpl<T>(clientImpl.functions, eJson) {
    override val defaults: Map<String, BsonValue> = mapOf(
        "schema_name" to BsonString(name),
    )
    override fun <T> withDocumentClass(eJson: EJson?): MongoCollection<T> {
        return MongoClientCollection(clientImpl, name, eJson ?: this.eJson)
    }
}

@PublishedApi
@OptIn(ExperimentalKBsonSerializerApi::class)
internal abstract class MongoCollectionImpl<T> constructor(
    val functions: FunctionsImpl,
    val eJson: EJson,
) : MongoCollection<T> {

    // Default entries for the argument document submitted for the function call.
    abstract val defaults: Map<String, BsonValue>

    private suspend fun call(name: String, arguments: MutableMap<String, BsonValue>.() -> Unit): BsonValue {
        val doc = defaults.toMutableMap()
        arguments(doc)
        val argument = BsonDocument(doc)
        return functions.callInternal(name, BsonArray(listOf(argument)))
    }

    @PublishedApi
    internal suspend fun count(filter: BsonDocument? = null, limit: Long? = null): Long {
        return decodeFromBsonValue(
            call("count") {
                filter?.let { put("query", it) }
                limit?.let<Long, Unit> { put("limit", BsonInt64(it)) }
            }
        )
    }

    @PublishedApi
    internal suspend fun findOne(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null): BsonValue {
        val call: BsonValue = call("findOne") {
            filter?.let { put("query", it) }
            projection?.let { put("project", it) }
            sort?.let { put("sort", it) }
        }
        return call
    }

    @PublishedApi
    internal suspend fun find(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null, limit: Long? = null): BsonValue =
        call("find") {
            filter?.let { put("query", it) }
            projection?.let { put("project", it) }
            sort?.let { put("sort", it) }
            limit?.let { put("limit", BsonInt64(it)) }
        }

    @PublishedApi
    internal suspend fun aggregate(pipeline: List<BsonDocument>): List<BsonValue> =
        call("aggregate") { put("pipeline", BsonArray(pipeline)) }.asArray().toList()

    @PublishedApi
    internal suspend fun insertOne(document: BsonDocument): BsonValue =
        call("insertOne") { put("document", document) }.asDocument()["insertedId"]!!

    @PublishedApi
    internal suspend fun insertMany(documents: List<BsonDocument>): List<BsonValue> =
        call("insertMany") {
            put("documents", BsonArray(documents))
        }.asDocument()["insertedIds"]!!.asArray().toList()

    @PublishedApi
    internal suspend fun deleteOne(filter: BsonDocument): Boolean {
        val deletedCountBson = call("deleteOne") {
            put("query", filter)
        }.asDocument()["deletedCount"]!!
        val deletedCount = decodeFromBsonValue<Long>(deletedCountBson)
        return when (deletedCount) {
            0L -> false
            1L -> true
            else -> throw ServiceException("Unexpected response from deleteOne: deletedCount=$deletedCount")
        }
    }

    @PublishedApi
    internal suspend fun deleteMany(filter: BsonDocument): Long {
        val deletedCountBson = call("deleteMany") {
            put("query", filter)
        }.asDocument()["deletedCount"]!!
        return decodeFromBsonValue(deletedCountBson)
    }

    @PublishedApi
    internal suspend fun updateOne(filter: BsonDocument, update: BsonDocument, upsert: Boolean = false): Pair<Boolean, BsonValue?> {
        val response: BsonValue = call("updateOne") {
            put("query", filter)
            put("update", update)
            put("upsert", BsonBoolean(upsert))
        }
        return response.asDocument().run {
            val modifiedCount: Long? = get("modifiedCount")?.let { decodeFromBsonValue<Long>(it) }
            val modified = when (modifiedCount) {
                0L -> false
                1L -> true
                else -> throw ServiceException("Unexpected response from updateOne: modifiedCount=$modifiedCount")
            }
            modified to (get("upsertedId"))
        }
    }

    @PublishedApi
    internal suspend fun updateMany(filter: BsonDocument, update: BsonDocument, upsert: Boolean = false): Pair<Long, BsonValue?> {
        val response = call("updateMany") {
            put("query", filter)
            put("update", update)
            put("upsert", BsonBoolean(upsert))
        }
        return response.asDocument().run {
            decodeFromBsonValue<Long>(get("modifiedCount")!!) to (get("upsertedId"))
        }
    }

    @Suppress("LongParameterList")
    @PublishedApi
    internal suspend fun findOneAndUpdate(
        filter: BsonDocument,
        update: BsonDocument,
        projection: BsonDocument? = null,
        sort: BsonDocument? = null,
        upsert: Boolean = false,
        returnNewDoc: Boolean = false,
    ): BsonValue = call("findOneAndUpdate") {
        put("filter", filter)
        put("update", update)
        projection?.let { put("projection", projection) }
        sort?.let { put("sort", sort) }
        put("upsert", BsonBoolean(upsert))
        put("returnNewDocument", BsonBoolean(returnNewDoc))
    }

    @Suppress("LongParameterList")
    @PublishedApi
    internal suspend fun findOneAndReplace(
        filter: BsonDocument,
        update: BsonDocument,
        projection: BsonDocument? = null,
        sort: BsonDocument? = null,
        upsert: Boolean = false,
        returnNewDoc: Boolean = false,
    ): BsonValue = call("findOneAndReplace") {
        put("filter", filter)
        put("update", update)
        projection?.let { put("projection", projection) }
        sort?.let { put("sort", sort) }
        put("upsert", BsonBoolean(upsert))
        put("returnNewDocument", BsonBoolean(returnNewDoc))
    }

    @PublishedApi
    internal suspend fun findOneAndDelete(
        filter: BsonDocument,
        projection: BsonDocument? = null,
        sort: BsonDocument? = null,
    ): BsonValue = call("findOneAndDelete") {
        put("filter", filter)
        projection?.let { put("projection", projection) }
        sort?.let { put("sort", sort) }
    }
}

@OptIn(ExperimentalKBsonSerializerApi::class)
@PublishedApi
internal inline fun <reified R : Any> MongoCollectionImpl<*>.encodeToBsonValue(value: R): BsonValue {
    return eJson.encodeToBsonValue(value)
}

@OptIn(ExperimentalKBsonSerializerApi::class)
@PublishedApi
internal inline fun <reified R> MongoCollectionImpl<*>.decodeFromBsonValue(bsonValue: BsonValue): R =
    when {
        bsonValue == BsonNull -> null as R
        R::class == BsonValue::class -> bsonValue as R
        else -> eJson.decodeFromBsonValue(bsonValue)
    }

@OptIn(ExperimentalKBsonSerializerApi::class)
@PublishedApi
internal inline fun <reified R> MongoCollectionImpl<*>.decodeFromBsonValueList(bsonValues: List<BsonValue>): List<R> {
    return if (R::class == BsonValue::class) {
        bsonValues as List<R>
    } else {
        bsonValues.map { eJson.decodeFromBsonValue(it) }
    }
}

@Suppress("ComplexMethod")
@PublishedApi
internal fun BsonValue.toAny(): Any? {
    return when (this.bsonType) {
        BsonType.NULL -> null
        BsonType.INT32 -> asInt32().value
        BsonType.INT64 -> asInt64().value
        BsonType.OBJECT_ID -> this.asObjectId()
        BsonType.STRING -> this.asString().value
        BsonType.DOUBLE -> this.asDouble().value
        BsonType.BINARY -> this.asBinary().data
        BsonType.BOOLEAN -> this.asBoolean().value
        BsonType.DATE_TIME -> this.asDateTime()
        BsonType.ARRAY -> this.asArray().values.map { it.toAny() }
        BsonType.DOCUMENT -> this.asDocument().mapValues { (k, v) -> v.toAny() }
        BsonType.TIMESTAMP -> asTimestamp()
        BsonType.DECIMAL128 -> asDecimal128()
        BsonType.DB_POINTER,
        BsonType.JAVASCRIPT,
        BsonType.SYMBOL,
        BsonType.JAVASCRIPT_WITH_SCOPE,
        BsonType.REGULAR_EXPRESSION,
        BsonType.MIN_KEY,
        BsonType.MAX_KEY,
        BsonType.END_OF_DOCUMENT,
        BsonType.UNDEFINED -> this
    }
}
