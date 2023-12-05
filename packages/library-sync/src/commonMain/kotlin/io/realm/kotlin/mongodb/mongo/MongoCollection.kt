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

import io.realm.kotlin.annotations.ExperimentalRealmSerializerApi
import io.realm.kotlin.mongodb.internal.MongoCollectionImpl
import io.realm.kotlin.mongodb.internal.call
import io.realm.kotlin.mongodb.internal.serializerOrRealmBuiltInSerializer
import kotlinx.serialization.KSerializer
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonBoolean
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonInt64
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson
import org.mongodb.kbson.serialization.decodeFromBsonValue
import kotlin.jvm.JvmName

public interface MongoCollection {

    public val name: String

    public suspend fun count(filter: BsonDocument? = null, limit: Long? = null): Int
//    public suspend fun findOne(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null): BsonValue {
//        TODO()
//    }
    @ExperimentalRealmSerializerApi
    public fun <T, K> typedCollection(): TypedMongoCollection<T, K> = TypedMongoCollectionImpl<T, K>(this as MongoCollectionImpl)
}

public interface TypedMongoCollection<T, K>: MongoCollection { }

@PublishedApi
internal class TypedMongoCollectionImpl<T, K>(collectionImpl: MongoCollectionImpl): TypedMongoCollection<T, K>, MongoCollectionImpl(collectionImpl.database, collectionImpl.name)

@OptIn(ExperimentalKBsonSerializerApi::class)
@ExperimentalRealmSerializerApi
public suspend inline fun <reified R> MongoCollection.findOne(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null, serializer: KSerializer<R>? = null): R {
    val value = (this as MongoCollectionImpl).call("findOne") {
        filter?.let { put("query", it) }
        projection?.let { put("projection", it) }
        sort?.let { put("sort", it) }
    }
    return decodeFromBsonValue(value, serializer)
}

@OptIn(ExperimentalKBsonSerializerApi::class)
@PublishedApi
internal inline fun <reified R> MongoCollection.decodeFromBsonValue(bsonValue: BsonValue, serializer: KSerializer<R>? = null): R {
    val serializer = serializer ?: (this as MongoCollectionImpl).functions.app.configuration.ejson.serializersModule.serializerOrRealmBuiltInSerializer()
    return (this as MongoCollectionImpl).functions.app.configuration.ejson.decodeFromBsonValue(serializer, bsonValue)
}


public class ClientOption {

}

@PublishedApi
internal interface FilterOptionInternal {
    var filter: BsonDocument?
}
@PublishedApi
internal interface LimitOptionInternal {
    var limit: Long?
}

public interface LimitOption { }
public fun LimitOption.limit(limit: Long) {
    (this as LimitOptionInternal).limit = limit
}
public interface FilterOption { }
public fun FilterOption.filter(json: String) {
    (this as FilterOptionInternal).filter = BsonDocument(json)
}
@ExperimentalKBsonSerializerApi
public inline fun <reified T : Any> FilterOption.filter(argument: T) {
    filter(argument, EJson.serializersModule.serializerOrRealmBuiltInSerializer())
}
@OptIn(ExperimentalKBsonSerializerApi::class)
public inline fun <reified T : Any> FilterOption.filter(argument: T, serializer: KSerializer<T>) {
    (this as FilterOptionInternal).filter = EJson.encodeToBsonValue(serializer, argument).asDocument()
}
public interface ProjectionOption
public interface SortOption

public interface CountOptions : LimitOption, FilterOption
public interface FindOneOptions : LimitOption, FilterOption, SortOption, ProjectionOption

@PublishedApi
internal class FindOptionsInternal: FindOneOptions, FilterOptionInternal, LimitOptionInternal {
    override var filter: BsonDocument? = null
    override var limit: Long? = null
}

@OptIn(ExperimentalKBsonSerializerApi::class)
@ExperimentalRealmSerializerApi
public suspend inline fun <T> MongoCollection.count(filter: BsonDocument, limit: Long): Long {
    return EJson.decodeFromBsonValue((this as MongoCollectionImpl).call("findOne") {
        put("query", filter)
        put("limit", BsonInt64(limit))
    })
}

//public suspend inline fun findOne(filter: Bson = {}, options: FindOptions(limit, projection, sort)): T
// query
// findoptions
//   limit
//   projection
//   sort

@OptIn(ExperimentalKBsonSerializerApi::class)
@ExperimentalRealmSerializerApi
public suspend inline fun <reified T> MongoCollection.findOne(configuration: FindOneOptions.() -> Unit): T {
    val options  = FindOptionsInternal()
    configuration(options)
    val response =  (this as MongoCollectionImpl).call("findOne") {
        options.filter?.let { put("query", it) }
//        options.projection?.let { put("projection", it) }
//        options.sort?.let { put("sort", it) }
    }
    return EJson.decodeFromBsonValue(response)
}

@OptIn(ExperimentalKBsonSerializerApi::class)
@ExperimentalRealmSerializerApi
public suspend inline fun <reified T> MongoCollection.find(filter: BsonDocument? = null, limit: Long? = null, projection: BsonDocument? = null, sort: BsonDocument? = null): List<T> {
    val objects =  (this as MongoCollectionImpl).call("find") {
        filter?.let { put("query", it) }
        projection?.let { put("projection", it) }
        sort?.let { put("sort", it) }
        limit?.let { put("limit", BsonInt64(limit)) }
    }.asArray().toList()
    return if (T::class == BsonValue::class) { objects as List<T> } else {objects.map {EJson.decodeFromBsonValue(it) } }
}

//@ExperimentalRealmSerializerApi
//@OptIn(ExperimentalKBsonSerializerApi::class)
//public suspend inline fun <reified T, reified R> MongoCollection.insertOne(document: T): R {
//    val value: KSerializer<T> = EJson.serializersModule.serializerOrRealmBuiltInSerializer<T>()
//    val x: BsonValue = EJson.encodeToBsonValue(value, document)
//    return (this as MongoCollectionImpl).call("insertOne") {
//        put("document", x)
//    }
//}

//@ExperimentalRealmSerializerApi
//@OptIn(ExperimentalKBsonSerializerApi::class)
//public suspend inline fun <reified T> MongoCollection.insertOneReturnId(document: T): ObjectId {
//    val encodedDocument: BsonValue = EJson.encodeToBsonValue(
//        EJson.serializersModule.serializerOrRealmBuiltInSerializer(),
//        document
//    )
//    return (this as MongoCollectionImpl).call<BsonValue>("insertOne") {
//        put("document", encodedDocument)
//    }.asDocument().get("insertedId")!!.asObjectId()
//}

@ExperimentalRealmSerializerApi
@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun <reified T> MongoCollection.aggregate(pipeline: List<BsonDocument>): List<T> {
    val insertedId: List<BsonValue> = (this as MongoCollectionImpl).call("aggregate") {
        put("pipeline", BsonArray(pipeline))
    }.asArray().toList()
    return if (T::class == BsonValue::class) { insertedId as List<T> } else { insertedId.map { EJson.decodeFromBsonValue(it) } }
}

@ExperimentalRealmSerializerApi
@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun <reified T, reified R : Any> MongoCollection.insertOne(document: T): R {
    val encodedDocument: BsonValue = EJson.encodeToBsonValue( EJson.serializersModule.serializerOrRealmBuiltInSerializer(), document )
    val insertedId: BsonValue = (this as MongoCollectionImpl).call("insertOne") {
        put("document", encodedDocument)
    }.asDocument().get("insertedId")!!
    return if (insertedId is R) { insertedId } else { EJson.decodeFromBsonValue(insertedId) }
}


// insertMany(docList): Map<Index, insertedId)
// documents

// Annoying that you cannot to this without assignment/return type
// collection.insertMany(listOf(SyncDog()))
@OptIn(ExperimentalKBsonSerializerApi::class)
@JvmName("asdf")
@ExperimentalRealmSerializerApi
public suspend inline fun <reified T, reified R : Any> TypedMongoCollection<T, R>.insertMany(
    documents: Collection<T>,
): List<R> {
    val encodedDocument: BsonValue = EJson.encodeToBsonValue(
        EJson.serializersModule.serializerOrRealmBuiltInSerializer(),
        documents
    )
    val insertedId: List<BsonValue> = (this as MongoCollectionImpl).call("insertMany") {
        put("documents", encodedDocument)
    }.asDocument().get("insertedIds")!!.asArray().toList()
    return if (R::class == BsonValue::class) {
        insertedId as List<R>
    } else {
        insertedId.map { decodeFromBsonValue(it) }
    }
}

@ExperimentalRealmSerializerApi
public suspend inline fun <reified T, reified R : Any> TypedMongoCollection<*, *>.insertMany(documents: Collection<T>): List<R> =
    (this as TypedMongoCollectionImpl<T, R>).insertMany<T, R>(documents)

@ExperimentalRealmSerializerApi
@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun <reified T, reified R : Any> MongoCollection.insertMany(documents: Collection<T>): List<R> {
    val encodedDocument: BsonValue = EJson.encodeToBsonValue(EJson.serializersModule.serializerOrRealmBuiltInSerializer(), documents)
    val insertedId: List<BsonValue> = (this as MongoCollectionImpl).call("insertMany") {
        put("documents", encodedDocument)
    }.asDocument().get("insertedIds")!!.asArray().toList()
    return if (R::class == BsonValue::class) { insertedId as List<R> } else { insertedId.map { EJson.decodeFromBsonValue(it) } }
}

// deleteOne(filter): Count
// query
@ExperimentalRealmSerializerApi
@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun MongoCollection.deleteOne(filter: BsonDocument): Boolean {
//    val encodedDocument: BsonValue = EJson.encodeToBsonValue(EJson.serializersModule.serializerOrRealmBuiltInSerializer(), documents)
    val insertedId: BsonValue = (this as MongoCollectionImpl).call("deleteOne") {
        put("query", filter)
    }.asDocument().get("deletedCount")!!
    val decodeFromBsonValue = EJson.decodeFromBsonValue<Long>(insertedId)
    return when(decodeFromBsonValue) {
        0L -> false
        1L -> true
        else -> TODO("Unexpected $decodeFromBsonValue")
    }
}


// deleteMany(filter): Count
// query
@ExperimentalRealmSerializerApi
@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun MongoCollection.deleteMany(filter: BsonDocument): Long {
    val insertedId: BsonValue = (this as MongoCollectionImpl).call("deleteMany") {
        put("query", filter)
    }.asDocument().get("deletedCount")!!
    return EJson.decodeFromBsonValue<Long>(insertedId)
}

// updateOne(filter, updateDoc, updateOptions(upsert: Boolean)): UpdateResult(matchCount, modifiedCount, upsertedId)
// updateMany(filter, updateDoc, updateOptions(upsert: Boolean)): UpdateResult(matchCount, modifiedCount, upsertedId)
// query
// update : BsonDocument
// upsert: Boolean

// FIXME Would we also allow filter and update to be serializables?
// FIXME Could just return Boolean, since matchedCount=1,modifiedCount=1 even if multiple documents should be matching :thinking:
// FIXME Should we split into upsertOne, since response only contains 'upsertedId' if call has 'upsert:true`
@ExperimentalRealmSerializerApi
@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun <reified R : Any> MongoCollection.updateOne(
    filter: BsonDocument,
    update: BsonDocument,
    upsert: Boolean = false
): R {
    val insertedId: BsonValue = (this as MongoCollectionImpl).call("updateOne") {
        put("query", filter)
        put("update", update)
        put("upsert", BsonBoolean(upsert))
    }.asDocument()//.get("insertedId")!!.asArray().toList()
    // {"matchedCount":{"$numberInt":"0"},"modifiedCount":{"$numberInt":"0"}}

    return if (R::class == BsonValue::class) { insertedId as R } else { EJson.decodeFromBsonValue(insertedId) }
}
@ExperimentalRealmSerializerApi
@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun <reified R : Any> MongoCollection.updateMany(
    filter: BsonDocument,
    update: BsonDocument,
    upsert: Boolean = false
): R {
    val insertedId: BsonValue = (this as MongoCollectionImpl).call("updateMany") {
        put("query", filter)
        put("update", update)
        put("upsert", BsonBoolean(upsert))
    }.asDocument().get("upsertedId")!!
    // {"matchedCount":{"$numberInt":"0"},"modifiedCount":{"$numberInt":"0"}}

    return if (R::class == BsonValue::class) { insertedId as R } else { EJson.decodeFromBsonValue(insertedId) }
}

@ExperimentalRealmSerializerApi
@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun <reified R : Any> MongoCollection.findOneAndUpdate(
    filter: BsonDocument,
    update: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
    upsert: Boolean = false,
    returnNewDoc: Boolean = false,
): R {
    val updatedDocument: BsonValue = (this as MongoCollectionImpl).call("findOneAndUpdate") {
        put("filter", filter)
        put("update", update)
        projection?.let { put("projection", projection)}
        sort?.let { put("sort", sort)}
        put("upsert", BsonBoolean(upsert))
        put("returnNewDoc", BsonBoolean(returnNewDoc))
    }
    return if (R::class == BsonValue::class) {
        updatedDocument as R
    } else {
        EJson.decodeFromBsonValue(updatedDocument)
    }
}

@ExperimentalRealmSerializerApi
@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun <reified R : Any> MongoCollection.findOneAndReplace(
    filter: BsonDocument,
    update: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
    upsert: Boolean = false,
    returnNewDoc: Boolean = false,
): R {
    // If returnNewDoc==true then the returned document is after the update otherwise it is from
    // before the update
    val updatedDocument: BsonValue = (this as MongoCollectionImpl).call("findOneAndReplace") {
        put("filter", filter)
        put("update", update)
        projection?.let { put("projection", projection)}
        sort?.let { put("sort", sort)}
        put("upsert", BsonBoolean(upsert))
        put("returnNewDoc", BsonBoolean(returnNewDoc))
    }
    return if (R::class == BsonValue::class) {
        updatedDocument as R
    } else {
        EJson.decodeFromBsonValue(updatedDocument)
    }
}

@ExperimentalRealmSerializerApi
@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun <reified R : Any> MongoCollection.findOneAndDelete(
    filter: BsonDocument,
    projection: BsonDocument? = null,
    sort: BsonDocument? = null,
): R {
    val deletedDocument: BsonValue = (this as MongoCollectionImpl).call("findOneAndDelete") {
        put("filter", filter)
        projection?.let { put("projection", projection)}
        sort?.let { put("sort", sort)}
    }
    return if (R::class == BsonValue::class) {
        deletedDocument as R
    } else {
        EJson.decodeFromBsonValue(deletedDocument)
    }
}
