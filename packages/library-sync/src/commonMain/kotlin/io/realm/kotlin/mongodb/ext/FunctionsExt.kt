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

import io.realm.kotlin.annotations.ExperimentalRealmSerializerApi
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.Functions
import io.realm.kotlin.mongodb.exceptions.AppException
import io.realm.kotlin.mongodb.exceptions.FunctionExecutionException
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.mongodb.internal.BsonEncoder
import io.realm.kotlin.mongodb.internal.FunctionsImpl
import io.realm.kotlin.mongodb.internal.serializerOrRealmBuiltInSerializer
import kotlinx.serialization.KSerializer
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.Bson
import org.mongodb.kbson.serialization.EJson

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
 * Invokes an Atlas function using the EJson encoder defined in [AppConfiguration.ejson].
 *
 * Due to some particularities of the serialization engine the call is defined with a builder available
 * in [callBuilderBlock]. If required, during the build phase you can define any serializers for the
 * arguments or return types.
 *
 * Example:
 *
 * ```
 * val dog: Dog = user.functions.call("RetrieveDog") {
 *     add("a parameter")
 *     add(1.5)
 *     returnValueSerializer = DogSerializer
 * }
 * ```
 *
 * @param name name of the function to call.
 * @param callBuilderBlock code block that sets the call arguments and serializers.
 * @param T the function return value type.
 * @return result of the function call.
 */
@ExperimentalRealmSerializerApi
@OptIn(ExperimentalKBsonSerializerApi::class)
public suspend inline fun <reified T : Any?> Functions.call(
    name: String,
    callBuilderBlock: CallBuilder<T>.() -> Unit
): T = with(this as FunctionsImpl) {
    CallBuilder<T>(app.configuration.ejson)
        .apply(callBuilderBlock)
        .run {
            val serializedEjsonArgs = Bson.toJson(arguments)

            val encodedResult = callInternal(name, serializedEjsonArgs)

            val returnValueSerializer =
                returnValueSerializer
                    ?: ejson.serializersModule.serializerOrRealmBuiltInSerializer()

            ejson.decodeFromString(returnValueSerializer, encodedResult)
        }
}

/**
 * Builder used to construct a call defining serializers for the different arguments and return value.
 */
@ExperimentalRealmSerializerApi
@OptIn(ExperimentalKBsonSerializerApi::class)
public class CallBuilder<T>
@PublishedApi
internal constructor(
    @PublishedApi
    internal val ejson: EJson,
) {
    /**
     * Contains all given arguments transformed as [BsonValue]. The encoding is done on each [add] call
     * as in that context we have type information from the reified type.
     *
     * Usually we would store the arguments in a `List<Any>` and would serialize just before invoking
     * the call, but that would require to do a runtime look up of the argument serializers an operation
     * that unfortunately is internal to kserializer and not stable cross all platforms.
     */
    @PublishedApi
    internal val arguments: BsonArray = BsonArray()

    /**
     * Serializer that would be used to deserialize the returned value, null by default.
     *
     * If null, the return value will be deserialized using the embedded type serializer.
     */
    public var returnValueSerializer: KSerializer<T>? = null

    /**
     * Adds an argument with the default serializer for its type to the function call.
     *
     * @param T argument type.
     * @param argument value.
     */
    public inline fun <reified T : Any> add(argument: T) {
        add(argument, ejson.serializersModule.serializerOrRealmBuiltInSerializer())
    }

    /**
     * Adds an argument with a user defined serializer to the function call.
     *
     * @param T argument type.
     * @param argument value.
     * @param serializer argument serializer.
     */
    public inline fun <reified T : Any> add(argument: T, serializer: KSerializer<T>) {
        arguments.add(ejson.encodeToBsonValue(serializer, argument))
    }
}
