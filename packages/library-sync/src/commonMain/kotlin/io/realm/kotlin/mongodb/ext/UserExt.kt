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

package io.realm.kotlin.mongodb.ext

import io.realm.kotlin.annotations.ExperimentalRealmSerializerApi
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.internal.UserImpl
import io.realm.kotlin.mongodb.internal.serializerOrRealmBuiltInSerializer
import kotlinx.serialization.KSerializer
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.Bson
import org.mongodb.kbson.serialization.EJson

/**
 * Returns the profile for this user as [BsonDocument].
 *
 * @return The profile for this user.
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun User.profileAsBsonDocument(): BsonDocument =
    (this as UserImpl).profileInternal { ejson ->
        Bson(ejson) as BsonDocument
    }

/**
 * Return the custom user data associated with the user in the Realm App as [BsonDocument].
 *
 * The data is only refreshed when the user's access token is refreshed or when explicitly
 * calling [User.refreshCustomData].
 *
 * @return The custom user data associated with the user.
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun User.customDataAsBsonDocument(): BsonDocument? =
    (this as UserImpl).customDataInternal { ejson ->
        Bson(ejson) as BsonDocument
    }

/**
 * Returns the profile for this user as a [T].
 *
 * **Note** This method supports full document serialization. The user profile will be deserialized with
 * [serializer] and decoded with [AppConfiguration.ejson].
 *
 * @param T the type to decoded the user profile.
 * @param serializer deserialization strategy for [T].
 * @return The profile for this user.
 */
@ExperimentalRealmSerializerApi
@OptIn(ExperimentalKBsonSerializerApi::class)
public inline fun <reified T> User.profile(serializer: KSerializer<T> = (this as UserImpl).app.configuration.ejson.serializersModule.serializerOrRealmBuiltInSerializer()): T =
    (this as UserImpl).app.configuration.ejson.let { ejson: EJson ->
        profileInternal { ejsonEncodedProfile ->
            ejson.decodeFromString(serializer, ejsonEncodedProfile)
        }
    }

/**
 * Returns the custom user data associated with the user in the Realm App as [T].
 *
 * The data is only refreshed when the user's access token is refreshed or when explicitly
 * calling [User.refreshCustomData].
 *
 * **Note** This method supports full document serialization. Custom data will be deserialized
 * with [serializer] and decoded with [AppConfiguration.ejson].
 *
 * @param T the type to decoded the user custom data.
 * @param serializer deserialization strategy for [T].
 * @return The custom user data associated with the user.
 */
@ExperimentalRealmSerializerApi
@OptIn(ExperimentalKBsonSerializerApi::class)
public inline fun <reified T> User.customData(serializer: KSerializer<T> = (this as UserImpl).app.configuration.ejson.serializersModule.serializerOrRealmBuiltInSerializer()): T? =
    (this as UserImpl).app.configuration.ejson.let { ejson: EJson ->
        customDataInternal { ejsonEncodedCustomData ->
            ejson.decodeFromString(
                deserializer = serializer,
                string = ejsonEncodedCustomData
            )
        }
    }
