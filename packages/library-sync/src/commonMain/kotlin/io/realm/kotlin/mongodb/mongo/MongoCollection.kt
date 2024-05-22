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

import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson

/**
 * A __mongo collection__ provides access to retrieve and update data from the database's
 * collection with specific typed serialization.
 *
 * This API corresponds to the Atlas App Service "MongoDB API". Please consult the
 * [MongoDB API Reference](https://www.mongodb.com/docs/atlas/app-services/functions/mongodb/api/)
 * for a detailed description of methods and arguments.
 *
 * Input arguments and responses to the App Service HTTP requests will be serialized from and to
 * the type [T] using [Kotlin's Serialization framework](https://kotlinlang.org/docs/serialization.html)
 * and can be customized by [Serializable]-annotations or customizing the [EJson]-serializer passed
 * to the various [MongoClient], [MongoDatabase] and [MongoCollection]-factory methods. For details
 * on configuring the serialization see [MongoClient].
 *
 * All operations on a [MongoCollection] will throw an:
 * - [ServiceException] if the underlying App Service HTTP requests fails
 * - [SerializationException] if input arguments cannot be serialized to a valid EJson document
 *   or if the App Service response could not be deserialized to the return types.
 *
 * @param T the default type that remote entities of the collection will be serialized from and
 * to.
 */
public interface MongoCollection<T> {

    /**
     * Name of the remote collection.
     */
    public val name: String

    /**
     * Get an instance of the same collection with a different set of default types serialization.
     *
     * @param eJson the EJson serializer that the [MongoCollection] should use to convert objects and
     * primary keys with. Will default to the databases [EJson] instance. For details on
     * configuration of serialization see [MongoClient].
     */
    @ExperimentalKBsonSerializerApi
    public fun <T> withDocumentClass(eJson: EJson? = null): MongoCollection<T>
}
