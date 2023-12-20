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

package io.realm.kotlin.mongodb.mongo

import io.realm.kotlin.mongodb.internal.MongoClientCollection
import io.realm.kotlin.mongodb.internal.MongoClientImpl
import io.realm.kotlin.types.BaseRealmObject
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson

/**
 * A **Mongo client** is used to access an App Service's Data Source directly without Sync support.
 *
 * This API corresponds to the Atlas App Service "MongoDB API". Please consult the
 * [MongoDB API Reference](https://www.mongodb.com/docs/atlas/app-services/functions/mongodb/api/)
 * for a detailed description of methods and arguments.
 */
public interface MongoClient {

    /**
     * The name of the data source that the [MongoClient] is connecting to.
     */
    public val serviceName: String

    /**
     * Get a [MongoDatabase] object to access data from the remote collections of the data source.
     *
     * Serialization to and from EJSON is performed with [KBSON](https://github.com/mongodb/kbson)
     * and requires to opt-in to the experimental [ExperimentalKBsonSerializerApi]-feature.
     *
     * @param databaseName name of the database from the data source.
     * @param eJson the EJson serializer that the [MongoDatabase] should use to convert objects and
     * primary keys with. Will default to the client's [EJson] instance.
     */
    @ExperimentalKBsonSerializerApi
    public fun database(databaseName: String, eJson: EJson? = null): MongoDatabase
}

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
 * @param K the default type that primary keys will be serialized into.
 * @return a [MongoCollection] that will accept and return entities from the remote collection
 * as [T] values.
 */
@ExperimentalKBsonSerializerApi
public inline fun <reified T : BaseRealmObject, K> MongoClient.collection(eJson: EJson? = null): MongoCollection<T, K> {
    @Suppress("invisible_reference", "invisible_member")
    return MongoClientCollection(this as MongoClientImpl, io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow(T::class).io_realm_kotlin_className, eJson ?: this.eJson)
}
