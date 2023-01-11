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
import io.realm.kotlin.mongodb.exceptions.FunctionExecutionException
import io.realm.kotlin.mongodb.internal.FunctionsImpl
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonDocument

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
 * @param name name of the function to call.
 * @param args arguments to the function.
 * @param T the function return value type.
 * @return result of the function call.
 *
 * @throws FunctionExecutionException if the function failed in some way.
 */
public suspend inline fun <reified T : Any?> Functions.call(
    name: String,
    vararg args: Any?
): T = (this as FunctionsImpl).callInternal(name, T::class, args) as T
