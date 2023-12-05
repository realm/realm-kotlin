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

import io.realm.kotlin.annotations.ExperimentalRealmSerializerApi
import io.realm.kotlin.mongodb.ext.CallBuilder
import io.realm.kotlin.mongodb.ext.call
import io.realm.kotlin.mongodb.mongo.MongoCollection
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonInt64
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonValue

@PublishedApi
internal open class MongoCollectionImpl(

    @PublishedApi internal val database: MongoDatabaseImpl,
    override val name: String,
) : MongoCollection {

    val client = this.database.client
    val user = client.user
    val functions = user.functions

    val defaults: Map<String, BsonValue> = mapOf(
            "database" to BsonString(database.name),
            "collection" to BsonString(name),
        )

    override suspend fun count(filter: BsonDocument?, limit: Long?): Int {
        @OptIn(ExperimentalRealmSerializerApi::class)
        return user.functions.call("count") {
            serviceName(client.serviceName)
            val args = defaults.toMutableMap()
            limit?.let { args.put("limit", BsonInt64(limit)) }
            filter?.let { args.put("query", it) }
            add(BsonDocument(args))
        }
    }
}

@ExperimentalRealmSerializerApi
@PublishedApi
internal suspend inline fun MongoCollectionImpl.call(name: String, crossinline document: MutableMap<String, BsonValue>.()-> Unit): BsonValue {
    return user.functions.call(name) {
        serviceName(client.serviceName)
        val doc = this@call.defaults.toMutableMap()
        document(doc)
        add(doc)
    }
}
