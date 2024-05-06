/*
 * Copyright 2024 Realm Inc.
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

package io.realm.kotlin.mongodb.ext

import io.realm.kotlin.internal.util.Validation
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.mongodb.internal.MongoCollectionImpl
import io.realm.kotlin.mongodb.internal.decodeFromBsonValue
import io.realm.kotlin.mongodb.internal.decodeFromBsonValueList
import io.realm.kotlin.mongodb.internal.encodeToBsonValue
import io.realm.kotlin.mongodb.internal.toAny
import io.realm.kotlin.mongodb.mongo.MongoCollection
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonValue
import kotlin.jvm.JvmName

/**
 * Returns the number of documents in the collection.
 *
 * @param filter a filter to select specific documents. If `null` then no filtering will be done.
 * @param limit an upper bound of the number of documents to consider. If `null` then no limit is
 * applied.
 * @throws ServiceException if the underlying App Service HTTP requests fails.
 */
public suspend fun MongoCollection<*>.count(filter: BsonDocument? = null, limit: Long? = null): Long {
    Validation.isType<MongoCollectionImpl<*>>(this)
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
 * @throws ServiceException if the underlying App Service HTTP requests fails.
 * @throws SerializationException if App Service response could not be deserialized to [T].
 */
public suspend inline fun <reified T> MongoCollection<T>.findOne(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null): T? {
    Validation.isType<MongoCollectionImpl<*>>(this)
    val bsonValue: BsonValue = findOne(filter, projection, sort)
    val decodeFromBsonValue: T? = decodeFromBsonValue<T?>(bsonValue)
    return decodeFromBsonValue
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
 * @throws ServiceException if the underlying App Service HTTP requests fails.
 * @throws SerializationException if App Service response could not be deserialized to `List<T>`.
 */
public suspend inline fun <reified T> MongoCollection<T>.find(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null, limit: Long? = null): List<T> {
    Validation.isType<MongoCollectionImpl<*>>(this)
    return find(filter, projection, sort, limit).asArray().map { decodeFromBsonValue(it) }
}

/**
 * Execute an aggregate pipeline on the remote collection.
 *
 * @param pipeline a list of aggregation pipeline stages.
 * @return the result of the remote `aggregate` invocation deserialized into a list of [T]-instances.
 * @throws ServiceException if the underlying App Service HTTP requests fails.
 * @throws SerializationException if App Service response could not be deserialized to `List<T>`.
 */
public suspend inline fun <reified T> MongoCollection<*>.aggregate(pipeline: List<BsonDocument>): List<T> {
    Validation.isType<MongoCollectionImpl<*>>(this)
    return decodeFromBsonValueList(aggregate(pipeline))
}

/**
 * Insert a single object into the remote collection.
 *
 * @param document the object to serialize and insert into the remote collection.
 * @return the `_id` value of the document insert in the collection deserialized to the most appropriate type.
 * @throws ServiceException if the underlying App Service HTTP requests fails.
 * @throws SerializationException if [document] could not be serialized into a EJson document or if
 * the App Service response could not be parsed into a reasonable type.
 */
public suspend inline fun <reified T : Any> MongoCollection<T>.insertOne(document: T): Any {
    Validation.isType<MongoCollectionImpl<*>>(this)
    return insertOne(encodeToBsonValue(document).asDocument()).toAny() ?: throw ServiceException("No primary key for inserted document")
}

/**
 * Insert a list of object into the remote collection.
 *
 * @param documents the objects to serialize and insert into the remote collection.
 * @param T the type of object that should be serializer and inserted to the collection.
 * @param R the type that the returned `_id` values should be deserialized into.
 * @return the `_id` values of the documents inserted in the collection deserialized to a [R]-instance.
 * @throws ServiceException if the underlying App Service HTTP requests fails.
 * @throws SerializationException if [documents] could not be serialized into a EJson document or if
 * the App Service response could not be deserialized to `List<R>`.
 */
@JvmName("insertManyTyped")
public suspend inline fun <reified T : Any> MongoCollection<*>.insertMany(documents: Collection<T>): List<Any> {
    Validation.isType<MongoCollectionImpl<*>>(this)
    val bsonValues: List<BsonValue> = insertMany(documents.map { encodeToBsonValue(it).asDocument() })
    return bsonValues.map { it.toAny() ?: throw ServiceException("Response should not contain null values: $bsonValues") }
}

/**
 * Delete a single object from the remote collection.
 *
 * @param filter a filter to specify the documents to delete.
 * @return a boolean indicating if a document was deleted or not.
 * @throws ServiceException if the underlying App Service HTTP requests fails.
 */
public suspend fun MongoCollection<*>.deleteOne(filter: BsonDocument): Boolean {
    Validation.isType<MongoCollectionImpl<*>>(this)
    return deleteOne(filter)
}

/**
 * Delete multiple objects from the remote collection.
 *
 * @param filter a filter to specify the documents to delete.
 * @return the number of documents that have been deleted.
 * @throws ServiceException if the underlying App Service HTTP requests fails.
 */
public suspend fun MongoCollection<*>.deleteMany(filter: BsonDocument): Long {
    Validation.isType<MongoCollectionImpl<*>>(this)
    return deleteMany(filter)
}

/**
 * Wrapper of results of an [updateOne] call.
 *
 * @param updated boolean indicating that a document was updated.
 * @param upsertedId primary key of the new document if created.
 */
public data class UpdateOneResult(val updated: Boolean, val upsertedId: Any?)

/**
 * Update or insert a single object in the remote collection.
 *
 * @param filter a filter to select the document to update.
 * @param update a BsonDocument specifying the updates that should be applied to the document.
 * @param upsert a boolean indicating if a new document should be inserted if the [filter] does not
 * match any existing documents in the collection.
 * @return the result of the `updateOne` operation.
 * @throws ServiceException if the underlying App Service HTTP requests fails.
 * @throws SerializationException if App Service response could not be deserialized to
 * [UpdateOneResult<R>].
 */
public suspend inline fun MongoCollection<*>.updateOne(
    filter: BsonDocument,
    update: BsonDocument,
    upsert: Boolean = false
): UpdateOneResult {
    Validation.isType<MongoCollectionImpl<*>>(this)
    return updateOne(filter, update, upsert).let { (updated, upsertedId) ->
        UpdateOneResult(updated, upsertedId?.let { it.toAny() })
    }
}

/**
 * Wrapper of results of an [updateMany] call.
 *
 * @param modifiedCount number of documents that was updated by the operation.
 * @param upsertedId primary key of the new document if created.
 */
public data class UpdateManyResult(val modifiedCount: Long, val upsertedId: Any?)

/**
 * Update multiple objects or insert a single new object in the remote collection.
 *
 * @param filter a filter to select the documents to update.
 * @param update a BsonDocument specifying the updates that should be applied to the documents.
 * @param upsert a boolean indicating if a new document should be inserted if the [filter] does not
 * match any existing documents in the collection.
 * @return the result of the `updateMany` operation.
 * @throws ServiceException if the underlying App Service HTTP requests fails.
 * @throws SerializationException if App Service response could not be deserialized to
 * [UpdateManyResult<R>].
 */
public suspend inline fun MongoCollection<*>.updateMany(
    filter: BsonDocument,
    update: BsonDocument,
    upsert: Boolean = false
): UpdateManyResult {
    Validation.isType<MongoCollectionImpl<*>>(this)
    return updateMany(filter, update, upsert).let { (updatedCount, upsertedId) ->
        UpdateManyResult(updatedCount, upsertedId?.toAny())
    }
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
 * @throws ServiceException if the underlying App Service HTTP requests fails.
 * @throws SerializationException if App Service response could not be deserialized to [T].
 */
@Suppress("LongParameterList")
public suspend inline fun <reified T> MongoCollection<T>.findOneAndUpdate(
    filter: BsonDocument,
    update: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
    upsert: Boolean = false,
    returnNewDoc: Boolean = false,
): T? {
    Validation.isType<MongoCollectionImpl<*>>(this)
    return decodeFromBsonValue(findOneAndUpdate(filter, update, projection, sort, upsert, returnNewDoc))
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
 * @return the result of the remote `findOneAndReplace` invocation deserialized into a [T]-instance.
 * @throws ServiceException if the underlying App Service HTTP requests fails.
 * @throws SerializationException if App Service response could not be deserialized to [T].
 */
@Suppress("LongParameterList")
public suspend inline fun <reified T> MongoCollection<T>.findOneAndReplace(
    filter: BsonDocument,
    document: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
    upsert: Boolean = false,
    returnNewDoc: Boolean = false,
): T? {
    Validation.isType<MongoCollectionImpl<*>>(this)
    return decodeFromBsonValue(findOneAndReplace(filter, document, projection, sort, upsert, returnNewDoc))
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
 * @throws ServiceException if the underlying App Service HTTP requests fails.
 * @throws SerializationException if App Service response could not be deserialized to [T].
 */
public suspend inline fun <reified T> MongoCollection<T>.findOneAndDelete(
    filter: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
): T? {
    Validation.isType<MongoCollectionImpl<*>>(this)
    return decodeFromBsonValue(findOneAndDelete(filter, projection, sort))
}
