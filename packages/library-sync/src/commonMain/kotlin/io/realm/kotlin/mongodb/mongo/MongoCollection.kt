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

package io.realm.kotlin.mongodb.mongo

import io.realm.kotlin.internal.util.Validation.isType
import io.realm.kotlin.mongodb.internal.MongoCollectionImpl
import io.realm.kotlin.mongodb.internal.decodeFromBsonValue
import io.realm.kotlin.mongodb.internal.decodeFromBsonValueList
import io.realm.kotlin.mongodb.internal.encodeToBsonValue
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson
import kotlin.jvm.JvmName

/**
 * A __mongo collection__ provides access to retrieve and update data from the database's
 * collection with specific typed serialization.
 *
 * @param T the default type that remote entities of the collection will be serialized from and
 * to.
 * @param K the default type that primary keys will be serialized into.
 */
public interface MongoCollection<T, K> {

    /**
     * Name of the remote collection.
     */
    public val name: String

    /**
     * Get an instance of the same collection with a different set of default types serialization.
     */
    @ExperimentalKBsonSerializerApi
    public fun <T, K> withDocumentClass(eJson: EJson? = null): MongoCollection<T, K>
}

public suspend fun MongoCollection<*, *>.count(filter: BsonDocument? = null, limit: Long? = null): Long {
    isType<MongoCollectionImpl<*, *>>(this)
    return count(filter, limit)
}

public suspend inline fun < reified T, R : Any> MongoCollection<T, R>.findOne(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null): T? {
    isType<MongoCollectionImpl<*, *>>(this)
    val bsonValue: BsonValue = findOne(filter, projection, sort)
    return decodeFromBsonValue(bsonValue)
}

@JvmName("findOneTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.findOne(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null): T? {
    return (this as MongoCollection<T?, BsonValue>).findOne(filter, projection, sort)
}

public suspend inline fun <reified T, K : Any> MongoCollection<T, K>.find(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null, limit: Long? = null): List<T> {
    isType<MongoCollectionImpl<*, *>>(this)
    return decodeFromBsonValueList(find(filter, projection, sort, limit).asArray().toList())
}

@JvmName("findTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.find(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null, limit: Long? = null): List<T> {
    return (this as MongoCollection<T, BsonValue>).find(filter, projection, sort, limit)
}

public suspend inline fun <reified T, K : Any> MongoCollection<T, K>.aggregate(pipeline: List<BsonDocument>): List<T> {
    isType<MongoCollectionImpl<*, *>>(this)
    return decodeFromBsonValueList(aggregate(pipeline))
}

@JvmName("aggregateTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.aggregate(pipeline: List<BsonDocument>): List<T> {
    return (this as MongoCollection<T, BsonValue>).aggregate(pipeline)
}

public suspend inline fun <reified T : Any, reified R : Any> MongoCollection<T, R>.insertOne(document: T): R {
    isType<MongoCollectionImpl<*, *>>(this)
    return decodeFromBsonValue(insertOne(encodeToBsonValue(document).asDocument()))
}

@JvmName("insertOneTyped")
public suspend inline fun <reified T : Any, reified R : Any> MongoCollection<*, *>.insertOne(document: T): R {
    return (this as MongoCollection<T, R>).insertOne(document)
}

public suspend inline fun <reified T : Any, reified R : Any> MongoCollection<T, R>.insertMany(
    documents: Collection<T>,
): List<R> {
    isType<MongoCollectionImpl<*, *>>(this)
    return decodeFromBsonValueList(insertMany(documents.map { encodeToBsonValue(it).asDocument() }))
}

@JvmName("insertManyTyped")
public suspend inline fun <reified T : Any, reified R : Any> MongoCollection<*, *>.insertMany(documents: Collection<T>): List<R> {
    return (this as MongoCollection<T, R>).insertMany(documents)
}

public suspend fun MongoCollection<*, *>.deleteOne(filter: BsonDocument): Boolean {
    isType<MongoCollectionImpl<*, *>>(this)
    return deleteOne(filter)
}

public suspend fun MongoCollection<*, *>.deleteMany(filter: BsonDocument): Long {
    isType<MongoCollectionImpl<*, *>>(this)
    return deleteMany(filter)
}

/**
 * Wrapper of results of an [updateOne] call.
 *
 * @param updated boolean indicating that a document was updated.
 * @param upsertedId primary key of the new document if created.
 */
public data class UpdateOneResult<R>(val updated: Boolean, val upsertedId: R?)
public suspend inline fun <T : Any, reified R> MongoCollection<T, R>.updateOne(
    filter: BsonDocument,
    update: BsonDocument,
    upsert: Boolean = false
): UpdateOneResult<R> {
    isType<MongoCollectionImpl<*, *>>(this)
    return updateOne(filter, update, upsert).let { (updated, upsertedId) ->
        UpdateOneResult(updated, upsertedId?.let { decodeFromBsonValue(it) })
    }
}

@JvmName("updateOneTyped")
public suspend inline fun <reified R> MongoCollection<*, *>.updateOne(
    filter: BsonDocument,
    update: BsonDocument,
    upsert: Boolean = false
): UpdateOneResult<R> {
    return (this as MongoCollection<BsonValue, R>).updateOne(filter, update, upsert)
}

/**
 * Wrapper of results of an [updateMany] call.
 *
 * @param modifiedCount number of documents that was updated by the operation.
 * @param upsertedId primary key of the new document if created.
 */
public data class UpdateManyResult<R>(val modifiedCount: Long, val upsertedId: R?)
public suspend inline fun <T : Any, reified R : Any> MongoCollection<T, R>.updateMany(
    filter: BsonDocument,
    update: BsonDocument,
    upsert: Boolean = false
): UpdateManyResult<R> {
    isType<MongoCollectionImpl<*, *>>(this)
    return updateMany(filter, update, upsert).let { (updatedCount, upsertedId) ->
        UpdateManyResult(updatedCount, upsertedId?.let { decodeFromBsonValue(it) })
    }
}

@JvmName("updateManyTyped")
public suspend inline fun <reified R : Any> MongoCollection<*, *>.updateMany(
    filter: BsonDocument,
    update: BsonDocument,
    upsert: Boolean = false
): UpdateManyResult<R> {
    return (this as MongoCollection<BsonValue, R>).updateMany(filter, update, upsert)
}

@Suppress("LongParameterList")
public suspend inline fun <reified T, R : Any> MongoCollection<T, R>.findOneAndUpdate(
    filter: BsonDocument,
    update: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
    upsert: Boolean = false,
    returnNewDoc: Boolean = false,
): T? {
    isType<MongoCollectionImpl<*, *>>(this)
    return decodeFromBsonValue(findOneAndUpdate(filter, update, projection, sort, upsert, returnNewDoc))
}

@Suppress("LongParameterList")
@JvmName("findAndUpdateTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.findOneAndUpdate(
    filter: BsonDocument,
    update: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
    upsert: Boolean = false,
    returnNewDoc: Boolean = false,
): T? {
    return (this as MongoCollection<T, BsonValue>).findOneAndUpdate(filter, update, projection, sort, upsert, returnNewDoc)
}

@Suppress("LongParameterList")
public suspend inline fun <reified T, R : Any> MongoCollection<T, R>.findOneAndReplace(
    filter: BsonDocument,
    document: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
    upsert: Boolean = false,
    returnNewDoc: Boolean = false,
): T? {
    isType<MongoCollectionImpl<*, *>>(this)
    return decodeFromBsonValue(findOneAndReplace(filter, document, projection, sort, upsert, returnNewDoc))
}

@Suppress("LongParameterList")
@JvmName("findAndReplaceTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.findOneAndReplace(
    filter: BsonDocument,
    update: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
    upsert: Boolean = false,
    returnNewDoc: Boolean = false,
): T? {
    return (this as MongoCollection<T, BsonValue>).findOneAndReplace(filter, update, projection, sort, upsert, returnNewDoc)
}

public suspend inline fun <reified T, R : Any> MongoCollection<T, R>.findOneAndDelete(
    filter: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
): T? {
    isType<MongoCollectionImpl<*, *>>(this)
    return decodeFromBsonValue(findOneAndDelete(filter, projection, sort))
}

@JvmName("findAndDeleteTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.findOneAndDelete(
    filter: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
): T? {
    return (this as MongoCollection<T, BsonValue>).findOneAndDelete(filter, projection, sort)
}
