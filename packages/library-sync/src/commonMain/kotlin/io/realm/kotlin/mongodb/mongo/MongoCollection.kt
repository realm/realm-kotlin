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
import io.realm.kotlin.mongodb.internal.decodeFromBsonValue
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson
import org.mongodb.kbson.serialization.decodeFromBsonValue
import org.mongodb.kbson.serialization.encodeToBsonValue
import kotlin.jvm.JvmName

public interface MongoCollection<T, K> {

    public val name: String

    @OptIn(ExperimentalKBsonSerializerApi::class)
    public fun <T, K> typedCollection(eJson: EJson? = null): MongoCollection<T, K> {
        val source = this as MongoCollectionImpl<*, *>
        return MongoCollectionImpl(source.database, source.name, eJson ?: source.eJson)
    }
}

public suspend inline fun MongoCollection<*, *>.count(filter: BsonDocument? = null, limit: Long? = null): Long {
    return (this as MongoCollectionImpl).count(filter, limit)
}

public suspend inline fun <reified T, reified R: Any> MongoCollection<T, R>.findOne(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null): T {
    return (this as MongoCollectionImpl).decodeFromBsonValue(findOne(filter, projection, sort))
}

@JvmName("findOneTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.findOne(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null): T {
    return (this as MongoCollection<T, BsonValue>).findOne(filter, projection, sort)
}

@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun <reified T, reified K: Any> MongoCollection<T, K>.find(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null, limit: Long? = null): List<T> {
    Validation.isType<MongoCollectionImpl<T, K>>(this, "")
    val objects = find(filter, projection, sort, limit).asArray().toList()
    return if (T::class == BsonValue::class) { objects as List<T> } else {objects.map {EJson.decodeFromBsonValue(it) } }
}
@JvmName("findTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.find(filter: BsonDocument? = null, projection: BsonDocument? = null, sort: BsonDocument? = null, limit: Long? = null ): List<T> {
    return (this as MongoCollection<T, BsonValue>).find(filter, projection, sort, limit)
}

@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun <reified T, K: Any> MongoCollection<T, K>.aggregate(pipeline: List<BsonDocument>): List<T> {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "")
    val objects = aggregate(pipeline)
    return if (T::class == BsonValue::class) { objects as List<T> } else { objects.map { EJson.decodeFromBsonValue(it) } }
}

@JvmName("aggregateTyped")
public suspend inline fun <reified T> MongoCollection<*, *>.aggregate(pipeline: List<BsonDocument>): List<T> {
    return (this as MongoCollection<T, BsonValue>).aggregate(pipeline)
}

@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun <reified T : Any, reified R : Any> MongoCollection<T, R>.insertOne(document: T): R {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "")
    val encodedDocument: BsonDocument = eJson.encodeToBsonValue(document).asDocument()
    val insertedId = insertOne(encodedDocument)
    return if (insertedId is R) { insertedId } else { EJson.decodeFromBsonValue(insertedId) }
}

@JvmName("insertOneTyped")
public suspend inline fun <reified T : Any, reified R : Any> MongoCollection<*, *>.insertOne(document: T): R {
    return (this as MongoCollection<T, R>).insertOne(document)
}


@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun <reified T : Any, reified R : Any> MongoCollection<T, R>.insertMany(
    documents: Collection<T>,
): List<R> {
    Validation.isType<MongoCollectionImpl<*, *>>(this, "")
    val encodedDocuments: List<BsonDocument> = documents.map { eJson.encodeToBsonValue(it).asDocument() }
    val insertedIds: List<BsonValue> = insertMany(encodedDocuments)
    return if (R::class == BsonValue::class) {
        insertedIds as List<R>
    } else {
        insertedIds.map { (this as MongoCollectionImpl<T, R>).decodeFromBsonValue(it) }
    }
}

@JvmName("insertManyTyped")
public suspend inline fun <reified T: Any, reified R : Any> MongoCollection<*, *>.insertMany(documents: Collection<T>): List<R> {
    return (this as MongoCollection<T, R>).insertMany(documents)
}

//@OptIn(ExperimentalKBsonSerializerApi::class)
//public suspend inline fun MongoCollection<*, *>.deleteOne(filter: BsonDocument): Boolean {
////    val encodedDocument: BsonValue = EJson.encodeToBsonValue(EJson.serializersModule.serializerOrRealmBuiltInSerializer(), documents)
//    val insertedId: BsonValue = (this as BsonMongoCollection).call("deleteOne") {
//        put("query", filter)
//    }.asDocument().get("deletedCount")!!
//    val decodeFromBsonValue = EJson.decodeFromBsonValue<Long>(insertedId)
//    return when(decodeFromBsonValue) {
//        0L -> false
//        1L -> true
//        else -> TODO("Unexpected $decodeFromBsonValue")
//    }
//}
//
//
//// deleteMany(filter): Count
//// query
//@ExperimentalRealmSerializerApi
//@OptIn(ExperimentalKBsonSerializerApi::class)
//public suspend inline fun MongoCollection<*, *>.deleteMany(filter: BsonDocument): Long {
//    val insertedId: BsonValue = (this as BsonMongoCollection).call("deleteMany") {
//        put("query", filter)
//    }.asDocument().get("deletedCount")!!
//    return EJson.decodeFromBsonValue<Long>(insertedId)
//}
//
//// updateOne(filter, updateDoc, updateOptions(upsert: Boolean)): UpdateResult(matchCount, modifiedCount, upsertedId)
//// updateMany(filter, updateDoc, updateOptions(upsert: Boolean)): UpdateResult(matchCount, modifiedCount, upsertedId)
//// query
//// update : BsonDocument
//// upsert: Boolean
//
//// FIXME Would we also allow filter and update to be serializables?
//// FIXME Could just return Boolean, since matchedCount=1,modifiedCount=1 even if multiple documents should be matching :thinking:
//// FIXME Should we split into upsertOne, since response only contains 'upsertedId' if call has 'upsert:true`
//@ExperimentalRealmSerializerApi
//@OptIn(ExperimentalKBsonSerializerApi::class)
//public suspend inline fun <reified R : Any> MongoCollection<*, *>.updateOne(
//    filter: BsonDocument,
//    update: BsonDocument,
//    upsert: Boolean = false
//): R {
//    val insertedId: BsonValue = (this as BsonMongoCollection).call("updateOne") {
//        put("query", filter)
//        put("update", update)
//        put("upsert", BsonBoolean(upsert))
//    }.asDocument()//.get("insertedId")!!.asArray().toList()
//    // {"matchedCount":{"$numberInt":"0"},"modifiedCount":{"$numberInt":"0"}}
//
//    return if (R::class == BsonValue::class) { insertedId as R } else { EJson.decodeFromBsonValue(insertedId) }
//}
//@ExperimentalRealmSerializerApi
//@OptIn(ExperimentalKBsonSerializerApi::class)
//public suspend inline fun <reified R : Any> MongoCollection<*, *>.updateMany(
//    filter: BsonDocument,
//    update: BsonDocument,
//    upsert: Boolean = false
//): R {
//    val insertedId: BsonValue = (this as BsonMongoCollection).call("updateMany") {
//        put("query", filter)
//        put("update", update)
//        put("upsert", BsonBoolean(upsert))
//    }.asDocument().get("upsertedId")!!
//    // {"matchedCount":{"$numberInt":"0"},"modifiedCount":{"$numberInt":"0"}}
//
//    return if (R::class == BsonValue::class) { insertedId as R } else { EJson.decodeFromBsonValue(insertedId) }
//}
//
//@ExperimentalRealmSerializerApi
//@OptIn(ExperimentalKBsonSerializerApi::class)
//public suspend inline fun <reified R : Any> MongoCollection<*, *>.findOneAndUpdate(
//    filter: BsonDocument,
//    update: BsonDocument,
//    projection: BsonDocument? = null,
//    sort: BsonDocument? = null,
//    upsert: Boolean = false,
//    returnNewDoc: Boolean = false,
//): R {
//    val updatedDocument: BsonValue = (this as BsonMongoCollection).call("findOneAndUpdate") {
//        put("filter", filter)
//        put("update", update)
//        projection?.let { put("projection", projection)}
//        sort?.let { put("sort", sort)}
//        put("upsert", BsonBoolean(upsert))
//        put("returnNewDoc", BsonBoolean(returnNewDoc))
//    }
//    return if (R::class == BsonValue::class) {
//        updatedDocument as R
//    } else {
//        EJson.decodeFromBsonValue(updatedDocument)
//    }
//}
//
//@ExperimentalRealmSerializerApi
//@OptIn(ExperimentalKBsonSerializerApi::class)
//public suspend inline fun <reified R : Any> MongoCollection<*, *>.findOneAndReplace(
//    filter: BsonDocument,
//    update: BsonDocument,
//    projection: BsonDocument? = null,
//    sort: BsonDocument? = null,
//    upsert: Boolean = false,
//    returnNewDoc: Boolean = false,
//): R {
//    // If returnNewDoc==true then the returned document is after the update otherwise it is from
//    // before the update
//    val updatedDocument: BsonValue = (this as BsonMongoCollection).call("findOneAndReplace") {
//        put("filter", filter)
//        put("update", update)
//        projection?.let { put("projection", projection)}
//        sort?.let { put("sort", sort)}
//        put("upsert", BsonBoolean(upsert))
//        put("returnNewDoc", BsonBoolean(returnNewDoc))
//    }
//    return if (R::class == BsonValue::class) {
//        updatedDocument as R
//    } else {
//        EJson.decodeFromBsonValue(updatedDocument)
//    }
//}
//
//@ExperimentalRealmSerializerApi
//@OptIn(ExperimentalKBsonSerializerApi::class)
//public suspend inline fun <reified R : Any> MongoCollection<*, *>.findOneAndDelete(
//    filter: BsonDocument,
//    projection: BsonDocument? = null,
//    sort: BsonDocument? = null,
//): R {
//    val deletedDocument: BsonValue = (this as BsonMongoCollection).call("findOneAndDelete") {
//        put("filter", filter)
//        projection?.let { put("projection", projection)}
//        sort?.let { put("sort", sort)}
//    }
//    return if (R::class == BsonValue::class) {
//        deletedDocument as R
//    } else {
//        EJson.decodeFromBsonValue(deletedDocument)
//    }
//}

//
//
//public class ClientOption {
//
//}
//
//@PublishedApi
//internal interface FilterOptionInternal {
//    var filter: BsonDocument?
//}
//@PublishedApi
//internal interface LimitOptionInternal {
//    var limit: Long?
//}
//
//public interface LimitOption { }
//public fun LimitOption.limit(limit: Long) {
//    (this as LimitOptionInternal).limit = limit
//}
//public interface FilterOption { }
//public fun FilterOption.filter(json: String) {
//    (this as FilterOptionInternal).filter = BsonDocument(json)
//}
//@ExperimentalKBsonSerializerApi
//public inline fun <reified T : Any> FilterOption.filter(argument: T) {
//    filter(argument, EJson.serializersModule.serializerOrRealmBuiltInSerializer())
//}
//@OptIn(ExperimentalKBsonSerializerApi::class)
//public inline fun <reified T : Any> FilterOption.filter(argument: T, serializer: KSerializer<T>) {
//    (this as FilterOptionInternal).filter = EJson.encodeToBsonValue(serializer, argument).asDocument()
//}
//public interface ProjectionOption
//public interface SortOption
//
//public interface CountOptions : LimitOption, FilterOption
//public interface FindOneOptions : LimitOption, FilterOption, SortOption, ProjectionOption
//
//@PublishedApi
//internal class FindOptionsInternal: FindOneOptions, FilterOptionInternal, LimitOptionInternal {
//    override var filter: BsonDocument? = null
//    override var limit: Long? = null
//}

//public suspend inline fun findOne(filter: Bson = {}, options: FindOptions(limit, projection, sort)): T
// query
// findoptions
//   limit
//   projection
//   sort

//@OptIn(ExperimentalKBsonSerializerApi::class)
//@ExperimentalRealmSerializerApi
//public suspend inline fun <reified T> MongoCollection.findOne(configuration: FindOneOptions.() -> Unit): T {
//    val options  = FindOptionsInternal()
//    configuration(options)
//    val response =  (this as BsonMongoCollection).call("findOne") {
//        options.filter?.let { put("query", it) }
////        options.projection?.let { put("projection", it) }
////        options.sort?.let { put("sort", it) }
//    }
//    return EJson.decodeFromBsonValue(response)
//}

