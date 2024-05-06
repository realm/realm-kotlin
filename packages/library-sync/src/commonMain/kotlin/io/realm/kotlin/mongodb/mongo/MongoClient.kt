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

import io.realm.kotlin.internal.RealmObjectCompanion
import io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow
import io.realm.kotlin.mongodb.internal.MongoDBSerializer
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson
import kotlin.reflect.KClass

/**
 * A **Mongo client** is used to access an App Service's Data Source directly without Sync support.
 *
 * This API corresponds to the Atlas App Service "MongoDB API". Please consult the
 * [MongoDB API Reference](https://www.mongodb.com/docs/atlas/app-services/functions/mongodb/api/)
 * for a detailed description of methods and arguments.
 *
 * Serialization to and from EJSON is performed with [KBSON](https://github.com/mongodb/kbson)
 * that supports the [Kotlin Serialization framework](https://github.com/Kotlin/kotlinx.serialization)
 * and handles serialization to and from classes marked with [Serializable]. Serialization can be
 * customized by customizing the [EJson]-serializer passed to the various [MongoClient],
 * [MongoDatabase] and [MongoCollection]-factory methods.
 *
 * Object references (links) are serialized solely by their primary keys, so to serialize the
 * MongoDB API requests and responses to and from realm objects ([RealmObject],
 * [EmbeddedRealmObject] and [AsymmetricRealmObject]) the serialization framework must be
 * configured with special serializers for those. This can be done with
 * ```
 *   val user = app.currentUser
 *   val client = user.mongoClient(
 *       "serviceName",
 *       EJson(
 *           serializersModule = realmSerializerModule(
 *               setOf(
 *                  MongoDBCollectionDataType1::class,
 *                  MongoDBCollectionDataType2::class
 *               )
 *           )
 *       )
 * ```
 *
 * *NOTE* Since the MongoDB API responses only includes primary key information for links,
 * serialization of responses into realm objects ([RealmObject], [EmbeddedRealmObject] and
 * [AsymmetricRealmObject]) will create instances of the target objects with only the primary key
 * property set. All other properties from the realm objects will have the default values specified
 * in the class definition.
 *
 * *NOTE* The EJSON serializer requires to opt-in to the experimental [ExperimentalKBsonSerializerApi].
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
     * primary keys with. Will default to the client's [EJson] instance. For details on
     * configuration of serialization see
     * [MongoClient].
     */
    @ExperimentalKBsonSerializerApi
    public fun database(databaseName: String, eJson: EJson? = null): MongoDatabase
}

/**
 * Creates [SerializersModule] with MongoDB API compliant serializers for all the realm objects
 * ([RealmObject], [EmbeddedRealmObject] and [AsymmetricRealmObject]) in the given [schema].
 *
 * The target types of links in mixed fields cannot be derived from the schema definition of the
 * realm objects. To be able to deserialize and create the correct instance of links in mixed
 * fields, all serializers needs to know of the full set of realm objects. This means that the
 * serializer module must be constructed with knowledge of all references classes in the schema.
 */
public fun realmSerializerModule(schema: Set<KClass<out BaseRealmObject>>): SerializersModule {
    val companions: Map<String, RealmObjectCompanion> =
        schema.associate { kClass -> realmObjectCompanionOrThrow(kClass).let { it.io_realm_kotlin_className to it } }
    val serializers: List<Pair<KClass<out BaseRealmObject>, KSerializer<*>>> = schema.map {
        it to MongoDBSerializer(it, companions)
    }

    return SerializersModule {
        serializers.forEach {
            @Suppress("UNCHECKED_CAST")
            contextual(it.first as KClass<RealmObject>, it.second as KSerializer<RealmObject>)
        }
    }
}
