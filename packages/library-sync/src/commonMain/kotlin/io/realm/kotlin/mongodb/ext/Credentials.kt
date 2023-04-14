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

import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.internal.AppImpl
import io.realm.kotlin.mongodb.internal.CustomEJsonCredentialsImpl
import kotlinx.serialization.serializer
import org.mongodb.kbson.ExperimentalKSerializerApi

/**
 * TODO document
 */
@OptIn(ExperimentalKSerializerApi::class)
public inline fun <reified T> Credentials.Companion.customFunction(payload: T): Credentials {
    return CustomEJsonCredentialsImpl { app: AppImpl ->
        val serializer = app.configuration.ejson.serializersModule.serializer<T>()
        app.configuration.ejson.encodeToString(serializer, payload)
    }
}
