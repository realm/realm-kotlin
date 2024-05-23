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
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson

@PublishedApi
@OptIn(ExperimentalKBsonSerializerApi::class)
internal class MongoDatabaseImpl constructor(
    @PublishedApi
    internal val client: MongoClientImpl,
    override val name: String,
    val eJson: EJson,
) : MongoDatabase {

    override fun collection(collectionName: String): MongoCollection<BsonDocument> {
        return MongoDatabaseCollection(this, collectionName, this.eJson)
    }

    override fun <T> collection(collectionName: String, eJson: EJson?): MongoCollection<T> =
        MongoDatabaseCollection(this, collectionName, eJson ?: this.eJson)
}
