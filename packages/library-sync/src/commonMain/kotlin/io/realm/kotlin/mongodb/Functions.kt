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

import io.realm.kotlin.mongodb.exceptions.FunctionExecutionException
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonDocument
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A Functions manager to call remote Atlas Functions for the associated Atlas App Services Application.
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
     * @param resultType The KType of the return value.
     * @param T The type of the return value.
     * @return Result of the function.
     *
     * @throws FunctionExecutionException if the function failed in some way.
     */
    public suspend fun <T> call(
        name: String,
        resultType: KType,
        vararg args: Any?
    ): T
}

/**
 * Invokes an Atlas Function.
 *
 * Reified convenience wrapper of [Functions.call].
 */
public suspend inline fun <reified T> Functions.call(
    name: String,
    vararg args: Any?
): T = call<T>(
    name = name,
    resultType = typeOf<T>(),
    args = *args,
)
