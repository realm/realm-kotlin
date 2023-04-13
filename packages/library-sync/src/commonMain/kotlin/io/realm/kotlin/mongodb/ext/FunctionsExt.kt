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
package io.realm.kotlin.mongodb.ext

import io.realm.kotlin.mongodb.Functions
import io.realm.kotlin.mongodb.exceptions.AppException
import io.realm.kotlin.mongodb.exceptions.FunctionExecutionException
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.mongodb.internal.BsonEncoder
import io.realm.kotlin.mongodb.internal.FunctionsImpl
import kotlinx.serialization.decodeFromString
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.ExperimentalKSerializerApi
import org.mongodb.kbson.serialization.Bson
import org.mongodb.kbson.serialization.EJson
import org.mongodb.kbson.serialization.encodeToBsonValue

/**
 * Invokes an Atlas function.
 *
 * Since the serialization engine [does not support third-party libraries yet](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/formats.md), there are some
 * limitations in what types can be used as arguments and return types:
 *
 * - Primitives, Bson, MutableRealmInt, RealmUUID, ObjectId, RealmInstant, RealmAny, Array, Collection, and Map are valid argument types.
 * - Results can only be deserialized to Bson, MutableRealmInt, RealmUUID, ObjectId, RealmInstant, RealmAny and primitive types
 *
 * The Bson implementations for arrays or maps are [BsonArray] and [BsonDocument], and they can be
 * used as valid return types.
 *
 * @param name name of the function to call.
 * @param args arguments to the function.
 * @param T the function return value type.
 * @return result of the function call.
 *
 * @throws FunctionExecutionException if the function failed in some way.
 * @throws ServiceException for other failures that can happen when communicating with App Services.
 * See [AppException] for details.
 */
public suspend inline fun <reified T : Any?> Functions.call(
    name: String,
    vararg args: Any?
): T = with(this as FunctionsImpl) {
    val serializedEjsonArgs = Bson.toJson(BsonEncoder.encodeToBsonValue(args.toList()))
    val encodedResult = callInternal(name, serializedEjsonArgs)

    BsonEncoder.decodeFromBsonValue(
        resultClass = T::class,
        bsonValue = Bson(encodedResult)
    ) as T
}

/**
 * TODO Document there are restrictions on arguments, deserialization works with experimental.
 *
 * Serialization of type arguments must be done with [EJson.encodeToBsonValue].
 *
 * Write some usage example.
 */
@OptIn(ExperimentalKSerializerApi::class)
public suspend inline fun <reified T : Any?> Functions.call(
    name: String,
    argumentBuilderBlock: CallArgumentBuilder.() -> Unit
): T = with(this as FunctionsImpl) {
    val serializedEjsonArgs = Bson.toJson(
        CallArgumentBuilder(app.configuration.ejson)
            .apply(argumentBuilderBlock)
            .arguments
    )

    val encodedResult = callInternal(name, serializedEjsonArgs)
    app.configuration.ejson.decodeFromString(encodedResult)
}

@OptIn(ExperimentalKSerializerApi::class)
public class CallArgumentBuilder
@PublishedApi
internal constructor(
    @PublishedApi
    internal val ejson: EJson
) {
    @PublishedApi
    internal val arguments: BsonArray = BsonArray()

    public inline fun <reified T : Any> add(argument: T) {
        arguments.add(ejson.encodeToBsonValue(argument))
    }
}