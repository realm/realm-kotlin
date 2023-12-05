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

import io.realm.kotlin.annotations.ExperimentalRealmSerializerApi
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson
import kotlin.jvm.JvmName

/**
 * A handle to a remote **Atlas App Service database** that provides access to its [MongoCollection]s.
 */
public interface MongoDatabase {

    /**
     * Name of the remote database.
     */
    public val name: String

    public fun collection(collectionName: String): MongoCollection<BsonValue, BsonValue>

    @OptIn(ExperimentalKBsonSerializerApi::class)
    public fun <T, K> collection(collectionName: String, eJson: EJson? = null): MongoCollection<T, K>

}

