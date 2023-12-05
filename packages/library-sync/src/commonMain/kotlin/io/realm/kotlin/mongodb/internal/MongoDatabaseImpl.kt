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

package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.mongodb.mongo.MongoCollection
import io.realm.kotlin.mongodb.mongo.MongoDatabase
import io.realm.kotlin.mongodb.mongo.TypedMongoCollection
import io.realm.kotlin.mongodb.mongo.TypedMongoCollectionImpl
import org.mongodb.kbson.BsonValue

@PublishedApi
internal class MongoDatabaseImpl(
    @PublishedApi
    internal val client: MongoClientImpl,
    override val name: String,
) : MongoDatabase {
    override fun collection(collectionName: String): MongoCollection =
        MongoCollectionImpl(this, collectionName)

    override fun typedCollectionbson(collectionName: String): TypedMongoCollection<BsonValue, BsonValue> {
        return TypedMongoCollectionImpl(collection(collectionName) as MongoCollectionImpl)
    }

    override fun <T, R> typedCollection(collectionName: String): TypedMongoCollection<T, R> {
        return TypedMongoCollectionImpl(collection(collectionName) as MongoCollectionImpl)
    }
}
