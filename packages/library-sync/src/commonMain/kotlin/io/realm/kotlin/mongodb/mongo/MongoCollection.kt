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
 * This API corresponds to the Atlas App Service "MongoDB API". Please consult the
 * [MongoDB API Reference](https://www.mongodb.com/docs/atlas/app-services/functions/mongodb/api/)
 * for a detailed description of methods and arguments.
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

/**
 * Returns the number of documents in the collection.
 *
 * @param filter a filter to select specific documents. If `null` then no filtering will be done.
 * @param limit an upper bound of the number of documents to consider. If `null` then no limit is
 * applied.
 */
public suspend fun MongoCollection<*, *>.count(filter: BsonDocument? = null, limit: Long? = null): Long {
    isType<MongoCollectionImpl<*, *>>(this)
    return count(filter, limit)
}

/**
 * Retrieve a single object from the remote collection.
 *
 * @param filter a filter to select specific documents. If `null` then no filtering will be done.
 * @param projection a BsonDocument that describes which fields that are returned from the server.
 * If `null` then all fields will be returned.
 * @param sort a document describing one or more fields used to sort documents before selecting the
 * single document to return. If `null` then no sorting will be applied.
 * @return the result of the remote `findOne` invocation deserialized into a [T]-instance.
 */
public suspend inline fun <reified T, R : Any> MongoCollection<T, R>.findOne(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null): T? {
    isType<MongoCollectionImpl<*, *>>(this)
    val bsonValue: BsonValue = findOne(filter, projection, sort)
    return decodeFromBsonValue(bsonValue)
}

/**
 * Retrieve a single object from the remote collection.
 *
 * @param filter a filter to select specific documents. If `null` then no filtering will be done.
 * @param projection a BsonDocument that describes which fields that are returned from the server.
 * If `null` then all fields will be returned.
 * @param sort a document describing one or more fields used to sort documents before selecting the
 * single document to return. If `null` then no sorting will be applied.
 * @param T the type that the result of the remote `findOne` invocation should be deserialized into.
 * @return the result of the remote `findOne` invocation deserialized into a [T]-instance.
 */
@JvmName("findOneTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.findOne(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null): T? {
    return (this as MongoCollection<T?, BsonValue>).findOne(filter, projection, sort)
}

/**
 * Retrieve multiple object from the remote collection.
 *
 * @param filter a filter to select specific documents. If `null` then no filtering will be done.
 * @param projection a BsonDocument that describes which fields that are returned from the server.
 * If `null` then all fields will be returned.
 * @param sort a document describing one or more fields used to sort documents before selecting the
 * single document to return. If `null` then no sorting will be applied.
 * @param limit an upper bound of the number of documents to consider. If `null` then no limit is
 * applied.
 * @return the result of the remote `find` invocation deserialized into a list of [T]-instances.
 */
public suspend inline fun <reified T, K : Any> MongoCollection<T, K>.find(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null, limit: Long? = null): List<T> {
    isType<MongoCollectionImpl<*, *>>(this)
    return decodeFromBsonValueList(find(filter, projection, sort, limit).asArray().toList())
}

/**
 * Retrieve multiple object from the remote collection.
 *
 * @param filter a filter to select specific documents. If `null` then no filtering will be done.
 * @param projection a BsonDocument that describes which fields that are returned from the server.
 * If `null` then all fields will be returned.
 * @param sort a document describing one or more fields used to sort documents before selecting the
 * single document to return. If `null` then no sorting will be applied.
 * @param limit an upper bound of the number of documents to consider. If `null` then no limit is
 * applied.
 * @param T the type that the results of the remote `find` invocation should be deserialized into.
 * @return the result of the remote `find` invocation deserialized into a list of [T]-instances.
 */
@JvmName("findTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.find(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null, limit: Long? = null): List<T> {
    return (this as MongoCollection<T, BsonValue>).find(filter, projection, sort, limit)
}

/**
 * Execute an aggregate pipeline on the remote collection.
 *
 * @param pipeline a list of aggregation pipeline stages.
 * @return the result of the remote `aggregate` invocation deserialized into a list of [T]-instances.
 */
public suspend inline fun <reified T, K : Any> MongoCollection<T, K>.aggregate(pipeline: List<BsonDocument>): List<T> {
    isType<MongoCollectionImpl<*, *>>(this)
    return decodeFromBsonValueList(aggregate(pipeline))
}

/**
 * Execute an aggregate pipeline on the remote collection.
 *
 * @param pipeline a list of aggregation pipeline stages.
 * @param T the type that the results of the remote `find` invocation should be deserialized into.
 * @return the result of the remote `aggregate` invocation deserialized into a list of [T]-instances.
 */
@JvmName("aggregateTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.aggregate(pipeline: List<BsonDocument>): List<T> {
    return (this as MongoCollection<T, BsonValue>).aggregate(pipeline)
}

/**
 * Insert a single object into the remote collection.
 *
 * @param document the object to serialize and insert into the remote collection.
 * @return the `_id` value of the document insert in the collection deserialized to a [R]-instance.
 */
public suspend inline fun <reified T : Any, reified R : Any> MongoCollection<T, R>.insertOne(document: T): R {
    isType<MongoCollectionImpl<*, *>>(this)
    return decodeFromBsonValue(insertOne(encodeToBsonValue(document).asDocument()))
}

/**
 * Insert a single object into the remote collection.
 *
 * @param document the object to serialize and insert into the remote collection.
 * @param T the type of object that should be serializer and inserted to the collection.
 * @param R the type that the returned `_id` value should be deserialized into.
 * @return the `_id` value of the document inserted in the collection deserialized to a [R]-instance.
 */
@JvmName("insertOneTyped")
public suspend inline fun <reified T : Any, reified R : Any> MongoCollection<*, *>.insertOne(document: T): R {
    return (this as MongoCollection<T, R>).insertOne(document)
}

/**
 * Insert a list of object into the remote collection.
 *
 * @param documents the objects to serialize and insert into the remote collection.
 * @return the `_id` values of the documents inserted in the collection deserialized to a [R]-instance.
 */
public suspend inline fun <reified T : Any, reified R : Any> MongoCollection<T, R>.insertMany(
    documents: Collection<T>,
): List<R> {
    isType<MongoCollectionImpl<*, *>>(this)
    return decodeFromBsonValueList(insertMany(documents.map { encodeToBsonValue(it).asDocument() }))
}

/**
 * Insert a list of object into the remote collection.
 *
 * @param documents the objects to serialize and insert into the remote collection.
 * @param T the type of object that should be serializer and inserted to the collection.
 * @param R the type that the returned `_id` values should be deserialized into.
 * @return the `_id` values of the documents inserted in the collection deserialized to a [R]-instance.
 */
@JvmName("insertManyTyped")
public suspend inline fun <reified T : Any, reified R : Any> MongoCollection<*, *>.insertMany(documents: Collection<T>): List<R> {
    return (this as MongoCollection<T, R>).insertMany(documents)
}

/**
 * Delete a single object from the remote collection.
 *
 * @param filter a filter to specify the documents to delete.
 * @return a boolean indicating if a document was delete or not.
 */
public suspend fun MongoCollection<*, *>.deleteOne(filter: BsonDocument): Boolean {
    isType<MongoCollectionImpl<*, *>>(this)
    return deleteOne(filter)
}

/**
 * Delete multiple objects from the remote collection.
 *
 * @param filter a filter to specify the documents to delete.
 * @return the number of documents that have been deleted.
 */
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

/**
 * Update or insert a single object in the remote collection.
 *
 * @param filter a filter to select the document to update.
 * @param update a BsonDocument specifying the updates that should be applied to the document.
 * @param upsert a boolean indicating if a new document should be inserted if the [filter] does not
 * match any existing documents in the collection.
 * @return the result of the `updateOne` operation.
 */
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

/**
 * Update or insert a single object in the remote collection.
 *
 * @param filter a filter to select the document to update.
 * @param update a BsonDocument specifying the updates that should be applied to the document.
 * @param upsert a boolean indicating if a new document should be inserted if the [filter] does not
 * match any existing documents in the collection.
 * @param R the type that the returned `_id` of a newly insert document should be deserialized into.
 * @return the result of the `updateOne` operation.
 */
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

/**
 * Update multiple objects or insert a single new object in the remote collection.
 *
 * @param filter a filter to select the documents to update.
 * @param update a BsonDocument specifying the updates that should be applied to the documents.
 * @param upsert a boolean indicating if a new document should be inserted if the [filter] does not
 * match any existing documents in the collection.
 * @return the result of the `updateMany` operation.
 */
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

/**
 * Update multiple objects or insert a single new object in the remote collection.
 *
 * @param filter a filter to select the documents to update.
 * @param update a BsonDocument specifying the updates that should be applied to the documents.
 * @param upsert a boolean indicating if a new document should be inserted if the [filter] does not
 * match any existing documents in the collection.
 * @param R the type that the returned `_id` of a newly insert document should be deserialized into.
 * @return the result of the `updateMany` operation.
 */
@JvmName("updateManyTyped")
public suspend inline fun <reified R : Any> MongoCollection<*, *>.updateMany(
    filter: BsonDocument,
    update: BsonDocument,
    upsert: Boolean = false
): UpdateManyResult<R> {
    return (this as MongoCollection<BsonValue, R>).updateMany(filter, update, upsert)
}

/**
 * Find and update or insert a single new object in the remote collection.
 *
 * @param filter a filter to select the documents to update.
 * @param update a BsonDocument specifying the updates that should be applied to the documents.
 * @param projection a BsonDocument that describes which fields that are returned from the server.
 * If `null` then all fields will be returned.
 * @param sort a document describing one or more fields used to sort documents before selecting the
 * single document to return. If `null` then no sorting will be applied.
 * @param upsert a boolean indicating if a new document should be inserted if the [filter] does not
 * match any existing documents in the collection.
 * @param returnNewDoc a boolean indicating whether to return the document before or after the update.
 * @return the result of the remote `findOneAndUpdate` invocation deserialized into a [T]-instance.
 */
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

/**
 * Find and update or insert a single new object in the remote collection.
 *
 * @param filter a filter to select the documents to update.
 * @param update a BsonDocument specifying the updates that should be applied to the documents.
 * @param projection a BsonDocument that describes which fields that are returned from the server.
 * If `null` then all fields will be returned.
 * @param sort a document describing one or more fields used to sort documents before selecting the
 * single document to return. If `null` then no sorting will be applied.
 * @param upsert a boolean indicating if a new document should be inserted if the [filter] does not
 * match any existing documents in the collection.
 * @param returnNewDoc a boolean indicating whether to return the document before or after the update.
 * @param T the type that the result of the remote `findOne` invocation should be deserialized into.
 * @return the result of the remote `findOneAndUpdate` invocation deserialized into a [T]-instance.
 */
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

/**
 * Find and replace or insert a single new object in the remote collection.
 *
 * @param filter a filter to select the documents to update.
 * @param document a BsonDocument specifying the updates that should be applied to the documents.
 * @param projection a BsonDocument that describes which fields that are returned from the server.
 * If `null` then all fields will be returned.
 * @param sort a document describing one or more fields used to sort documents before selecting the
 * single document to return. If `null` then no sorting will be applied.
 * @param upsert a boolean indicating if a new document should be inserted if the [filter] does not
 * match any existing documents in the collection.
 * @param returnNewDoc a boolean indicating whether to return the document before or after the update.
 * @return the result of the remote `findOneAndUpdate` invocation deserialized into a [T]-instance.
 */
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

/**
 * Find and replace or insert a single new object in the remote collection.
 *
 * @param filter a filter to select the documents to update.
 * @param document a BsonDocument specifying the updates that should be applied to the documents.
 * @param projection a BsonDocument that describes which fields that are returned from the server.
 * If `null` then all fields will be returned.
 * @param sort a document describing one or more fields used to sort documents before selecting the
 * single document to return. If `null` then no sorting will be applied.
 * @param upsert a boolean indicating if a new document should be inserted if the [filter] does not
 * match any existing documents in the collection.
 * @param returnNewDoc a boolean indicating whether to return the document before or after the update.
 * @param T the type that the result of the remote `findOne` invocation should be deserialized into.
 * @return the result of the remote `findOneAndUpdate` invocation deserialized into a [T]-instance.
 */
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

/**
 * Find and delete a single object in the remote collection.
 *
 * @param filter a filter to select the documents to update.
 * @param projection a BsonDocument that describes which fields that are returned from the server.
 * If `null` then all fields will be returned.
 * @param sort a document describing one or more fields used to sort documents before selecting the
 * single document to return. If `null` then no sorting will be applied.
 * @return the result of the remote `findOneAndDelete` invocation deserialized into a [T]-instance.
 */
public suspend inline fun <reified T, R : Any> MongoCollection<T, R>.findOneAndDelete(
    filter: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
): T? {
    isType<MongoCollectionImpl<*, *>>(this)
    return decodeFromBsonValue(findOneAndDelete(filter, projection, sort))
}

/**
 * Find and delete a single object in the remote collection.
 *
 * @param filter a filter to select the documents to update.
 * @param projection a BsonDocument that describes which fields that are returned from the server.
 * If `null` then all fields will be returned.
 * @param sort a document describing one or more fields used to sort documents before selecting the
 * single document to return. If `null` then no sorting will be applied.
 * @param T the type that the result of the remote `findOne` invocation should be deserialized into.
 * @return the result of the remote `findOneAndDelete` invocation deserialized into a [T]-instance.
 */
@JvmName("findAndDeleteTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.findOneAndDelete(
    filter: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
): T? {
    return (this as MongoCollection<T, BsonValue>).findOneAndDelete(filter, projection, sort)
}
