/*
 * Copyright 2024 Realm Inc.
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

import io.realm.kotlin.mongodb.internal.MongoClientCollection
import io.realm.kotlin.mongodb.internal.MongoClientImpl
import io.realm.kotlin.mongodb.mongo.MongoClient
import io.realm.kotlin.mongodb.mongo.MongoCollection
import io.realm.kotlin.types.BaseRealmObject
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson

/**
 * Get a [MongoCollection] that exposes methods to retrieve and update data from the remote
 * collection of objects of schema type [T].
 *
 * Serialization to and from EJSON is performed with [KBSON](https://github.com/mongodb/kbson)
 * and requires to opt-in to the experimental [ExperimentalKBsonSerializerApi]-feature.
 *
 * @param eJson the EJson serializer that the [MongoCollection] should use to convert objects and
 * primary keys with. Will default to the databases [EJson] instance.
 * @param T the schema type indicating which for which remote entities of the collection will be
 * serialized from and to.
 * @return a [MongoCollection] that will accept and return entities from the remote collection
 * as [T] values.
 */
@ExperimentalKBsonSerializerApi
public inline fun <reified T : BaseRealmObject> MongoClient.collection(eJson: EJson? = null): MongoCollection<T> {
    @Suppress("invisible_reference", "invisible_member")
    return MongoClientCollection(this as MongoClientImpl, io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow(T::class).io_realm_kotlin_className, eJson ?: this.eJson)
}
