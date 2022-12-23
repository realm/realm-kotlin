/*
 * Copyright 2022 Realm Inc.
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
package io.realm.kotlin.mongodb

import io.realm.kotlin.ExperimentalApi
import io.realm.kotlin.mongodb.exceptions.FunctionExecutionException
import io.realm.kotlin.mongodb.internal.BsonEncoder
import io.realm.kotlin.mongodb.internal.DefaultConverter
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonValue
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

/**
 * A Functions manager to call remote Atlas Functions for the associated Atlas App services Application.
 *
 * Since the serialization engine [does not support third-party libraries yet](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/formats.md), there are some
 * limitations in what types can be used as arguments and return types:
 *
 * - Primitives, Bson, lists, and maps are valid argument types.
 * - Results can only be deserialized to primitives or Bson types.
 *
 * @see [User.functions]
 */
public interface Functions {
    /**
     * The [App] that this function manager is associated with.
     */
    public val app: App

    /**
     * The [User] that this function manager is authenticated with.
     */
    public val user: User

    /**
     * Invokes an Atlas function.
     *
     * Since the serialization engine [does not support third-party libraries yet](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/formats.md), there are some
     * limitations in what types can be used as arguments and return types:
     *
     * - Primitives, Bson, lists, and maps are valid argument types.
     * - Results can only be deserialized to primitives or Bson types.
     *
     * The Bson implementations for arrays or maps are [BsonArray] and [BsonDocument], and they can be
     * used as valid return types.
     *
     * @param name Name of the function to call.
     * @param args Arguments to the function.
     * @param deserializationStrategy Deserialization strategy for decoding the results.
     * @param T The type for the functions response.
     * @return Result of the function.
     *
     * @throws FunctionExecutionException if the function failed in some way.
     */
    public suspend fun <T : Any?> call(
        name: String,
        deserializationStrategy: DeserializationStrategy<T>,
        vararg args: Any?,
    ): T

    @ExperimentalApi
    public suspend fun call(name: String, arg: BsonValue): BsonValue
    @ExperimentalApi
    public suspend fun <T : Any?> call(name: String, vararg args: Any?, clazz: KClass<T & Any>, converter: BsonConverter = DefaultConverter): T
}

@ExperimentalApi
public interface BsonConverter {
    public fun <T : Any?> fromBsonValue(bsonValue: BsonValue, clazz: KClass<T & Any>): T
    public fun toBsonValue(value: Any?): BsonValue
}

/**
 * Invokes an Atlas Function.
 *
 * Reified convenience wrapper of [Functions.call].
 */
@ExperimentalApi
public suspend inline fun <reified T : Any> Functions.call(
    name: String,
    vararg args: Any?
): T = call<T>(
    name = name,
    args = *args,
    clazz = T::class
)
// FIXME Discuss if we really want to expose deserializerStrategy based interface when we basically
//  don't use the serializers. Could might as well just do a KType based interface or use the above
//  simplified BsonConverter ... but somehow mark it @Beta/@OptIn
public suspend inline fun <reified T : Any> Functions.call2(
    name: String,
    vararg args: Any?
): T = call<T>(
    name = name,
    deserializationStrategy = if (T::class == Any::class) {
        BsonEncoder.serializersModule.serializer<BsonValue>()
    } else {
        BsonEncoder.serializersModule.serializer<T>()
    } as KSerializer<T>,
    args = *args,
)

/**
 * Convenience helper that returns a predefined serializer when T class doesn't have a defined
 * serializer, or [T]'s serializer otherwise.
 */
public inline fun <reified T : Any?> serializerOrDefault(
    default: KSerializer<*>
): KSerializer<T> =
    (BsonEncoder.serializersModule.serializerOrNull(typeOf<T>()) ?: default) as KSerializer<T>
