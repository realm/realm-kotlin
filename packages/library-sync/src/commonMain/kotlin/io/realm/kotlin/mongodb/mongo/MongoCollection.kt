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

// TODO - QUESTIONS
//  - should we allow serialization of update, sort and projection arguments?

package io.realm.kotlin.mongodb.mongo

import io.realm.kotlin.internal.util.Validation
import io.realm.kotlin.mongodb.internal.MongoCollectionImpl
import io.realm.kotlin.mongodb.internal.encodeToBsonValue
import io.realm.kotlin.mongodb.internal.decodeFromBsonValue
import io.realm.kotlin.mongodb.internal.decodeFromBsonValueList
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson
import kotlin.jvm.JvmName

public interface MongoCollection<T, K> {

    public val name: String

    @OptIn(ExperimentalKBsonSerializerApi::class)
    public fun <T, K> collection(eJson: EJson? = null): MongoCollection<T, K>
}

public suspend fun MongoCollection<*, *>.count(filter: BsonDocument? = null, limit: Long? = null): Long {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "MongoCollection should be an instance of MongoCollectionImpl")
    return count(filter, limit)
}

public suspend inline fun < reified T, R: Any> MongoCollection<T, R>.findOne(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null): T {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "MongoCollection should be an instance of MongoCollectionImpl")
    return decodeFromBsonValue(findOne(filter, projection, sort))
}

@JvmName("findOneTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.findOne(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null): T {
    return (this as MongoCollection<T, BsonValue>).findOne(filter, projection, sort)
}

public suspend inline fun <reified T, K: Any> MongoCollection<T, K>.find(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null, limit: Long? = null): List<T> {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "MongoCollection should be an instance of MongoCollectionImpl")
    val objects = find(filter, projection, sort, limit).asArray().toList()
    return decodeFromBsonValueList(objects)
}
@JvmName("findTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.find(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null, limit: Long? = null ): List<T> {
    return (this as MongoCollection<T, BsonValue>).find(filter, projection, sort, limit)
}

public suspend inline fun <reified T, K: Any> MongoCollection<T, K>.aggregate(pipeline: List<BsonDocument>): List<T> {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "MongoCollection should be an instance of MongoCollectionImpl")
    val objects: List<BsonValue> = aggregate(pipeline)
    return decodeFromBsonValueList(objects)
}

@JvmName("aggregateTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.aggregate(pipeline: List<BsonDocument>): List<T> {
    return (this as MongoCollection<T, BsonValue>).aggregate(pipeline)
}

public suspend inline fun <reified T : Any, reified R : Any> MongoCollection<T, R>.insertOne(document: T): R {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "MongoCollection should be an instance of MongoCollectionImpl")
    val encodedDocument: BsonDocument = encodeToBsonValue(document).asDocument()
    val insertedId = insertOne(encodedDocument)
    return if (insertedId is R) { insertedId } else { decodeFromBsonValue(insertedId) }
}

@JvmName("insertOneTyped")
public suspend inline fun <reified T : Any, reified R : Any> MongoCollection<*, *>.insertOne(document: T): R {
    return (this as MongoCollection<T, R>).insertOne(document)
}

public suspend inline fun <reified T : Any, reified R : Any> MongoCollection<T, R>.insertMany(
    documents: Collection<T>,
): List<R> {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "MongoCollection should be an instance of MongoCollectionImpl")
    val encodedDocuments: List<BsonDocument> = documents.map { encodeToBsonValue(it).asDocument() }
    val insertedIds: List<BsonValue> = insertMany(encodedDocuments)
    return if (R::class == BsonValue::class) {
        insertedIds as List<R>
    } else {
        insertedIds.map { decodeFromBsonValue(it) }
    }
}

@JvmName("insertManyTyped")
public suspend inline fun <reified T: Any, reified R : Any> MongoCollection<*, *>.insertMany(documents: Collection<T>): List<R> {
    return (this as MongoCollection<T, R>).insertMany(documents)
}

public suspend fun MongoCollection<*, *>.deleteOne(filter: BsonDocument): Boolean {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "MongoCollection should be an instance of MongoCollectionImpl")
    return deleteOne(filter)
}

public suspend fun MongoCollection<*, *>.deleteMany(filter: BsonDocument): Long {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "MongoCollection should be an instance of MongoCollectionImpl")
    return deleteMany(filter)
}

// FIXME Could just return Boolean, since matchedCount=1,modifiedCount=1 even if multiple documents should be matching :thinking:
// FIXME Should we split into upsertOne, since response only contains 'upsertedId' if call has 'upsert:true`
public suspend inline fun <T : Any, reified R : Any> MongoCollection<T, R>.updateOne(
    filter: BsonDocument,
    update: BsonDocument,
    upsert: Boolean = false
): List<R> {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "MongoCollection should be an instance of MongoCollectionImpl")
    return decodeFromBsonValue(updateOne(filter, update, upsert))
}

@JvmName("updateOneTyped")
public suspend inline fun <reified R : Any> MongoCollection<*, *>.updateOne(
    filter: BsonDocument,
    update: BsonDocument,
    upsert: Boolean = false
): List<R> {
    return (this as MongoCollection<BsonValue, R>).updateOne(filter, update, upsert)
}

public suspend inline fun <T : Any, reified R : Any> MongoCollection<T, R>.updateMany(
    filter: BsonDocument,
    update: BsonDocument,
    upsert: Boolean = false
): List<R> {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "MongoCollection should be an instance of MongoCollectionImpl")
    return decodeFromBsonValue(updateMany(filter, update, upsert))
}

@JvmName("updateManyTyped")
public suspend inline fun <reified R : Any> MongoCollection<*, *>.updateMany(
    filter: BsonDocument,
    update: BsonDocument,
    upsert: Boolean = false
    ): List<R> {
    return (this as MongoCollection<BsonValue, R>).updateMany(filter, update, upsert)
}

public suspend inline fun <reified T, R : Any> MongoCollection<T, R>.findOneAndUpdate(
    filter: BsonDocument,
    update: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
    upsert: Boolean = false,
    returnNewDoc: Boolean = false,
): T {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "MongoCollection should be an instance of MongoCollectionImpl")
    return decodeFromBsonValue(findOneAndUpdate(filter, update, projection, sort, upsert, returnNewDoc))
}

@JvmName("findAndUpdateTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.findOneAndUpdate(
    filter: BsonDocument,
    update: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
    upsert: Boolean = false,
    returnNewDoc: Boolean = false,
): T {
    return (this as MongoCollection<T, BsonValue>).findOneAndUpdate(filter, update, projection, sort, upsert, returnNewDoc)
}

public suspend inline fun <reified T, R : Any> MongoCollection<T, R>.findOneAndReplace(
    filter: BsonDocument,
    update: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
    upsert: Boolean = false,
    returnNewDoc: Boolean = false,
): T {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "MongoCollection should be an instance of MongoCollectionImpl")
    return decodeFromBsonValue(findOneAndReplace(filter, update, projection, sort, upsert, returnNewDoc))
}

@JvmName("findAndReplaceTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.findOneAndReplace(
    filter: BsonDocument,
    update: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
    upsert: Boolean = false,
    returnNewDoc: Boolean = false,
): T {
    return (this as MongoCollection<T, BsonValue>).findOneAndReplace(filter, update, projection, sort, upsert, returnNewDoc)
}

public suspend inline fun <reified T, R : Any> MongoCollection<T, R>.findOneAndDelete(
    filter: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
): T {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "MongoCollection should be an instance of MongoCollectionImpl")
    return decodeFromBsonValue(findOneAndDelete(filter, projection, sort))
}

@JvmName("findAndDeleteTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.findOneAndDelete(
    filter: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
): T {
    return (this as MongoCollection<T, BsonValue>).findOneAndDelete(filter, projection, sort)
}
