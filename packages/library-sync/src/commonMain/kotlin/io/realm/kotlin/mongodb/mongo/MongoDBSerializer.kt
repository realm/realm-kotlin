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

package io.realm.kotlin.mongodb.mongo

import io.realm.kotlin.internal.ObjectIdImpl
import io.realm.kotlin.internal.RealmObjectCompanion
import io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow
import io.realm.kotlin.internal.realmObjectCompanionOrNull
import io.realm.kotlin.internal.util.Validation
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonBinary
import org.mongodb.kbson.BsonBoolean
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonDouble
import org.mongodb.kbson.BsonInt32
import org.mongodb.kbson.BsonInt64
import org.mongodb.kbson.BsonNull
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonType
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.Decimal128
import org.mongodb.kbson.ObjectId
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

public open class MongoDBSerializer<T : BaseRealmObject>(clazz: KClass<T>) : KSerializer<T> {
    override val descriptor: SerialDescriptor = BsonDocument.serializer().descriptor
    private val companion = realmObjectCompanionOrThrow(clazz as KClass<out BaseRealmObject>)
    override fun deserialize(decoder: Decoder): T {
        val x: BsonDocument = decoder.decodeSerializableValue(BsonDocument.serializer())
        return bsonToObject(companion, x)
    }

    @Suppress("NestedBlockDepth")
    private fun bsonToObject(companion: RealmObjectCompanion, bsonDocument: BsonDocument): T {
        val instance = companion.io_realm_kotlin_newInstance() as T
        val fields: Map<String, Pair<KClass<*>, KProperty1<BaseRealmObject, Any?>>> = companion.io_realm_kotlin_fields
        val schema = companion.io_realm_kotlin_schema()
        bsonDocument.keys.forEach {
            // FIXME Test exception path
            val fieldsDescriptor = fields[it] ?: throw SerializationException("Unknown field $it for type ${companion.io_realm_kotlin_className}")
            val type = schema[it]?.type
            val value: Any? = if (type?.storageType == RealmStorageType.OBJECT) {
                // FIXME Should we rather embed targetCompanion directly and make it nullable for non RealmObjects
                val targetCompanion = fieldsDescriptor.first.realmObjectCompanionOrNull()!!
                val primaryKeyAccessor =
                    (targetCompanion.io_realm_kotlin_primaryKey as KMutableProperty1<BaseRealmObject, Any?>?)
                when (primaryKeyAccessor) {
                    // Embedded objects does not have primary keys
                    null -> {
                        val bsonValue: BsonValue? = bsonDocument[it]
                        if (bsonValue != null && bsonValue != BsonNull) {
                            bsonToObject(targetCompanion, bsonValue.asDocument())
                        } else {
                            null
                        }
                    }
                    else -> {
                        val targetInstance = (targetCompanion.io_realm_kotlin_newInstance() as BaseRealmObject)
                        primaryKeyAccessor.set(targetInstance, bsonDocument[it]?.asPrimitiveValue())
                        targetInstance
                    }
                }
            } else {
                bsonDocument[it]?.asPrimitiveValue()
            }
            // FIXME Check validity of nullability
            (fieldsDescriptor.second as KMutableProperty1<T, Any?>).set(instance, value)
        }
        return instance
    }

    // FIXME Serialize null values?
    // FIXME @Ignore
    override fun serialize(encoder: Encoder, value: T) {
        val document = objectToBson(companion, value)
        encoder.encodeSerializableValue(BsonDocument.serializer(), document)
    }

    @Suppress("NestedBlockDepth")
    private fun objectToBson(companion: RealmObjectCompanion, realmObject: BaseRealmObject): BsonDocument {
        val fields: Map<String, Pair<KClass<*>, KProperty1<BaseRealmObject, Any?>>> = companion.io_realm_kotlin_fields
        val schema = companion.io_realm_kotlin_schema()
        val document = BsonDocument()
        fields.forEach { (fieldName, fieldDetails) ->
            val (_, accessor) = fieldDetails
            val type = schema[fieldName]?.type ?: Validation.sdkError("Schema does not contain property $fieldName")
            val value = if (type.storageType == RealmStorageType.OBJECT) {
                val target = accessor.get(realmObject)
                if (target != null && target != BsonNull) {
                    val targetCompanion =
                        realmObjectCompanionOrThrow((target as BaseRealmObject)::class)
                    val primaryKeyProperty: KMutableProperty1<BaseRealmObject, Any>? = targetCompanion.io_realm_kotlin_primaryKey as KMutableProperty1<BaseRealmObject, Any>?
                    when (primaryKeyProperty) {
                        null -> objectToBson(targetCompanion, target)
                        else -> BsonValue(primaryKeyProperty.get(target))
                    }
                } else return@forEach
            } else {
                BsonValue(accessor.get(realmObject))
            }
            document[fieldName] = value
        }
        return document
    }
}

@Suppress("ComplexMethod")
internal operator fun BsonValue.Companion.invoke(any: Any?): BsonValue {
    return when (any) {
        null -> BsonNull
        is String -> BsonString(any)
        is Boolean -> BsonBoolean(any)
        is Byte -> BsonInt32(any.toInt())
        is Char -> BsonInt32(any.code)
        is Short -> BsonInt32(any.toInt())
        is Int -> BsonInt32(any)
        is Long -> BsonInt64(any)
        is Float -> BsonDouble(any.toDouble())
        is Double -> BsonDouble(any)
        is ObjectId -> any
        is io.realm.kotlin.types.ObjectId -> BsonObjectId((any as ObjectIdImpl).bytes)
        is BsonValue -> any
        is Decimal128 -> any
        is ByteArray -> BsonBinary(any)
        is MutableRealmInt -> BsonInt64(any.get())
        is RealmAny -> TODO()
        is RealmObject -> TODO()
        is RealmInstant -> TODO()
        is RealmUUID -> TODO()
        // RealmSet and RealmList ends up here
        is Collection<*> -> BsonArray(any.map { BsonValue(it) })
        // RealmDictionaries
        is RealmDictionary<*> -> BsonDocument(any.mapValues { BsonValue(it.value) })
        else -> TODO("Serializer does not support object of type $any")
//        BsonType.TIMESTAMP -> asTimestamp()
//        BsonType.BINARY -> asBinary()
//        BsonType.DATE_TIME -> asDateTime()
//        BsonType.DOCUMENT,
//        BsonType.END_OF_DOCUMENT,
//        BsonType.ARRAY,
//        BsonType.UNDEFINED,
//        BsonType.REGULAR_EXPRESSION,
//        BsonType.DB_POINTER,
//        BsonType.JAVASCRIPT,
//        BsonType.SYMBOL,
//        BsonType.JAVASCRIPT_WITH_SCOPE,
//        BsonType.MIN_KEY,
//        BsonType.MAX_KEY -> TODO()
    }
}

internal fun BsonValue.asPrimitiveValue(): Any? {

    return when (this.bsonType) {
        BsonType.DOUBLE -> asDouble().value
        BsonType.STRING -> asString().value
        BsonType.INT32 -> asInt32().value
        BsonType.TIMESTAMP -> asTimestamp()
        BsonType.INT64 -> asInt64().value
        BsonType.DECIMAL128 -> asDecimal128()
        BsonType.BINARY -> asBinary()
        BsonType.OBJECT_ID -> asObjectId()
        BsonType.BOOLEAN -> asBoolean().value
        BsonType.DATE_TIME -> asDateTime()
        BsonType.NULL -> null
        BsonType.DOCUMENT,
        BsonType.END_OF_DOCUMENT,
        BsonType.ARRAY,
        BsonType.UNDEFINED,
        BsonType.REGULAR_EXPRESSION,
        BsonType.DB_POINTER,
        BsonType.JAVASCRIPT,
        BsonType.SYMBOL,
        BsonType.JAVASCRIPT_WITH_SCOPE,
        BsonType.MIN_KEY,
        BsonType.MAX_KEY -> TODO("Deserializer does not support ${this.bsonType}")
    }
}
