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

import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson

/**
 * A handle to a remote **Atlas App Service database** that provides access to its [MongoCollection]s.
 */
public interface MongoDatabase {

    /**
     * Name of the remote database.
     */
    public val name: String

    /**
     * Get a [MongoCollection] that exposed methods to retrieve and update data from the database's
     * collection.
     *
     * Serialization to and from EJSON is performed with [KBSON](https://github.com/mongodb/kbson)
     * and requires to opt-in to the experimental [ExperimentalKBsonSerializerApi]-feature.
     *
     * @param collectionName the name of the collection name that the [MongoCollection] will
     * connect to.
     * @return a [MongoCollection] that will accept and return entities from the remote collection
     * as [BsonDocument] values.
     */
    public fun collection(collectionName: String): MongoCollection<BsonDocument>

    /**
     * Get a [MongoCollection] that exposed methods to retrieve and update data from the database's
     * collection with specific typed serialization.
     *
     * Serialization to and from EJSON is performed with [KBSON](https://github.com/mongodb/kbson)
     * and requires to opt-in to the experimental [ExperimentalKBsonSerializerApi]-feature.
     *
     * @param collectionName the name of the collection name that the [MongoCollection] will
     * connect to.
     * @param eJson the EJson serializer that the [MongoCollection] should use to convert objects and
     * primary keys with. Will default to the databases [EJson] instance. For details on
     * configuration of serialization see [MongoClient].
     * @param T the default type that remote entities of the collection will be serialized from and
     * to.
     * @return a [MongoCollection] that will accept and return entities from the remote collection
     * as [T] values.
     */
    @ExperimentalKBsonSerializerApi
    public fun <T> collection(collectionName: String, eJson: EJson? = null): MongoCollection<T>
}
