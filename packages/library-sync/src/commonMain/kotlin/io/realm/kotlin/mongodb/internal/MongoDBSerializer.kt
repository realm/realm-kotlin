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

package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.ext.toRealmDictionary
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.ext.toRealmSet
import io.realm.kotlin.internal.RealmObjectCompanion
import io.realm.kotlin.internal.asBsonBinary
import io.realm.kotlin.internal.asBsonDateTime
import io.realm.kotlin.internal.asRealmInstant
import io.realm.kotlin.internal.asRealmUUID
import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.internal.schema.collectionType
import io.realm.kotlin.internal.util.Validation.sdkError
import io.realm.kotlin.schema.RealmProperty
import io.realm.kotlin.schema.RealmPropertyType
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.RealmAny
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
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonType
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.Decimal128
import org.mongodb.kbson.ObjectId
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

/**
 * Serializer that will encode and decode realm objects to and from EJSON sent and received
 * from the MongoClient APIs according to the schema definition of the realm objects.
 *
 * Serialization of links will only include primary key of the target and deserialization of
 * responses from MongoClient will create target link instances where only the primary key is set
 * (and all the other properties of the object will have default values).
 *
 * The target types of links in mixed fields cannot be derived from the schema definition of the
 * realm objects. To be able to deserialize and create the correct instance, the serializer needs to
 * know of all potential target types.
 */
public class MongoDBSerializer internal constructor(
    clazz: KClass<out BaseRealmObject>,
    internal val schema: Map<String, RealmObjectCompanion> = emptyMap()
) : KSerializer<BaseRealmObject> {
    override val descriptor: SerialDescriptor = BsonDocument.serializer().descriptor
    @Suppress("invisible_reference", "invisible_member")
    private val companion = io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow(clazz)

    override fun deserialize(decoder: Decoder): BaseRealmObject {
        return bsonToObject(companion, decoder.decodeSerializableValue(BsonDocument.serializer()))
    }

    private fun bsonToObject(companion: RealmObjectCompanion, bsonDocument: BsonDocument): BaseRealmObject {
        val instance = companion.io_realm_kotlin_newInstance() as BaseRealmObject
        val fields: Map<String, Pair<KClass<*>, KProperty1<BaseRealmObject, Any?>>> =
            companion.io_realm_kotlin_fields
        val schema = companion.io_realm_kotlin_schema()
        bsonDocument.keys.forEach { key ->
            val (kClass, accessor) = fields[key]
                ?: throw SerializationException("Unknown field '$key' for type ${companion.io_realm_kotlin_className}")
            val type = schema[key]?.type
                ?: throw SerializationException("Unknown field '$key' for type ${companion.io_realm_kotlin_className}")
            val value = bsonValueToStorageType(type.collectionType, type.storageType, kClass, bsonDocument[key])
            (accessor as KMutableProperty1<BaseRealmObject, Any?>).set(instance, value)
        }
        return instance
    }

    override fun serialize(encoder: Encoder, value: BaseRealmObject) {
        encoder.encodeSerializableValue(BsonDocument.serializer(), objectToBson(companion, value))
    }

    private fun objectToBson(
        companion: RealmObjectCompanion,
        realmObject: BaseRealmObject,
    ): BsonDocument {
        val fields: Map<String, Pair<KClass<*>, KProperty1<BaseRealmObject, Any?>>> =
            companion.io_realm_kotlin_fields
        val schema = companion.io_realm_kotlin_schema()
        val document = BsonDocument()
        fields.forEach { (fieldName, fieldDetails) ->
            val (_, accessor) = fieldDetails
            val type: RealmPropertyType =
                schema[fieldName]?.type ?: sdkError("Schema does not contain property $fieldName")
            storageTypeToBsonValue(type.collectionType, type.storageType, accessor.get(realmObject)).let {
                document[fieldName] = it
            }
        }
        return document
    }

    @Suppress("LongMethod", "ComplexMethod")
    private fun storageTypeToBsonValue(
        collectionType: CollectionType,
        elementType: RealmStorageType,
        value: Any?,
    ): BsonValue {
        if (value == null) return BsonNull
        return when (collectionType) {
            CollectionType.RLM_COLLECTION_TYPE_NONE -> {
                when (elementType) {
                    RealmStorageType.BOOL -> BsonBoolean(value as Boolean)
                    RealmStorageType.INT -> when (value) {
                        is Byte -> BsonInt32(value.toInt())
                        is Char -> BsonInt32(value.code)
                        is Short -> BsonInt32(value.toInt())
                        is Int -> BsonInt32(value)
                        is Long -> BsonInt64(value)
                        is MutableRealmInt -> BsonInt64(value.toLong())
                        else -> sdkError("Unexpected value of type ${value::class.simpleName} for field with storage type $elementType")
                    }
                    RealmStorageType.STRING -> BsonString(value as String)
                    RealmStorageType.BINARY -> BsonBinary(value as ByteArray)
                    RealmStorageType.OBJECT -> {
                        @Suppress("UNCHECKED_CAST")
                        val targetCompanion =
                            @Suppress("invisible_reference", "invisible_member")
                            io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow(value::class as KClass<BaseRealmObject>)
                        @Suppress("UNCHECKED_CAST")
                        val primaryKeyProperty: KMutableProperty1<BaseRealmObject, Any>? =
                            targetCompanion.io_realm_kotlin_primaryKey as KMutableProperty1<BaseRealmObject, Any>?
                        when (primaryKeyProperty) {
                            // Embedded objects does not have a primary key, so serialize to full documents
                            null -> objectToBson(targetCompanion, value as BaseRealmObject)
                            else -> {
                                val targetStorageType =
                                    targetCompanion.io_realm_kotlin_schema().primaryKey!!.type.storageType
                                val primaryKey = primaryKeyProperty.get(value as BaseRealmObject)
                                storageTypeToBsonValue(CollectionType.RLM_COLLECTION_TYPE_NONE, targetStorageType, primaryKey)
                            }
                        }
                    }

                    RealmStorageType.FLOAT -> BsonDouble((value as Float).toDouble())
                    RealmStorageType.DOUBLE -> BsonDouble(value as Double)
                    RealmStorageType.DECIMAL128 -> value as Decimal128
                    RealmStorageType.TIMESTAMP -> (value as RealmInstant).asBsonDateTime()
                    RealmStorageType.OBJECT_ID -> value as ObjectId
                    RealmStorageType.UUID -> (value as RealmUUID).asBsonBinary()
                    RealmStorageType.ANY -> { realmAnyToBsonValue(value as RealmAny) }
                }
            }
            CollectionType.RLM_COLLECTION_TYPE_LIST -> {
                BsonArray(
                    (value as List<*>).map {
                        storageTypeToBsonValue(
                            CollectionType.RLM_COLLECTION_TYPE_NONE,
                            elementType,
                            it
                        )
                    }
                )
            }
            CollectionType.RLM_COLLECTION_TYPE_SET -> {
                BsonArray(
                    (value as Set<*>).map {
                        storageTypeToBsonValue(
                            CollectionType.RLM_COLLECTION_TYPE_NONE,
                            elementType,
                            it
                        )
                    }
                )
            }
            CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> {
                @Suppress("UNCHECKED_CAST")
                val map = value as Map<String, *>
                BsonDocument(
                    map.mapValues { (_, v) ->
                        storageTypeToBsonValue(
                            CollectionType.RLM_COLLECTION_TYPE_NONE,
                            elementType,
                            v
                        )
                    }
                )
            }
            else -> sdkError("Unknown collection type: $collectionType")
        }
    }

    @Suppress("ComplexMethod")
    private fun realmAnyToBsonValue(realmAny: RealmAny?): BsonValue = when (realmAny?.type) {
        null -> BsonNull
        RealmAny.Type.BOOL -> BsonBoolean(realmAny.asBoolean())
        RealmAny.Type.INT -> BsonInt64(realmAny.asLong())
        RealmAny.Type.STRING -> BsonString(realmAny.asString())
        RealmAny.Type.BINARY -> BsonBinary(realmAny.asByteArray())
        RealmAny.Type.TIMESTAMP -> realmAny.asRealmInstant().asBsonDateTime()
        RealmAny.Type.FLOAT -> BsonDouble(realmAny.asFloat().toDouble())
        RealmAny.Type.DOUBLE -> BsonDouble(realmAny.asDouble())
        RealmAny.Type.DECIMAL128 -> realmAny.asDecimal128()
        RealmAny.Type.OBJECT_ID -> realmAny.asObjectId()
        RealmAny.Type.UUID -> realmAny.asRealmUUID().asBsonBinary()
        RealmAny.Type.OBJECT -> {
            // Objects in RealmAny cannot be EmbeddedObjects
            val target = realmAny.asRealmObject(BaseRealmObject::class)
            @Suppress("invisible_reference", "invisible_member")
            val targetCompanion = io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow(target::class)
            val primaryKeySchemaProperty: RealmProperty = targetCompanion.io_realm_kotlin_schema().primaryKey ?: throw SerializationException(
                "Cannot serialize class without primary key: '${targetCompanion.io_realm_kotlin_className}'"
            )
            val (_, primaryKeyAccessor) = targetCompanion.io_realm_kotlin_fields[primaryKeySchemaProperty.name] ?: throw SerializationException(
                "Cannot serialize class without primary key: '${targetCompanion.io_realm_kotlin_className}'"
            )
            val primaryKey: BsonValue = storageTypeToBsonValue(
                CollectionType.RLM_COLLECTION_TYPE_NONE,
                primaryKeySchemaProperty.type.storageType,
                primaryKeyAccessor.get(target)
            )
            BsonDocument(
                "\$ref" to BsonString(targetCompanion.io_realm_kotlin_className),
                "\$id" to primaryKey
            )
        }
        RealmAny.Type.LIST -> {
            BsonArray(realmAny.asList().map { realmAnyToBsonValue(it) })
        }
        RealmAny.Type.DICTIONARY -> {
            BsonDocument(realmAny.asDictionary().mapValues { (_, v) -> realmAnyToBsonValue(v) })
        }
    }

    @Suppress("LongMethod", "ComplexMethod")
    internal fun bsonValueToStorageType(
        collectionType: CollectionType,
        elementType: RealmStorageType,
        kClass: KClass<*>,
        bsonValue: BsonValue?,
    ): Any? {
        if (bsonValue == null || bsonValue == BsonNull) return null
        return when (collectionType) {
            CollectionType.RLM_COLLECTION_TYPE_NONE -> when (elementType) {
                RealmStorageType.BOOL -> bsonValue.asBoolean().value
                RealmStorageType.INT -> when (kClass) {
                    Byte::class -> bsonValue.asNumber().longValue().toByte()
                    Char::class -> bsonValue.asNumber().intValue().toChar()
                    Short::class -> bsonValue.asNumber().intValue().toShort()
                    Int::class -> bsonValue.asNumber().intValue()
                    Long::class -> bsonValue.asNumber().longValue()
                    MutableRealmInt::class -> MutableRealmInt.create(
                        bsonValue.asNumber().longValue()
                    )
                    else -> sdkError("Unexpected KClass ('${kClass.simpleName}') for element with storage type '$elementType'")
                }
                RealmStorageType.STRING -> bsonValue.asString().value
                RealmStorageType.BINARY -> bsonValue.asBinary().data
                RealmStorageType.OBJECT -> {
                    @Suppress("invisible_reference", "invisible_member")
                    val targetCompanion =
                        io.realm.kotlin.internal.platform.realmObjectCompanionOrNull(kClass)
                            ?: sdkError("Unexpected kClass ('${kClass::simpleName}') without realm companion")
                    when (val primaryKeySchemaProperty = targetCompanion.io_realm_kotlin_schema().primaryKey) {
                        // Embedded objects does not have primary keys
                        null -> bsonToObject(targetCompanion, bsonValue.asDocument())
                        else -> {
                            val (targetKClass, primaryKeyAccessor) = targetCompanion.io_realm_kotlin_fields[primaryKeySchemaProperty.name]
                                ?: throw SerializationException(
                                    "Target class does not have a primary key: '${targetCompanion.io_realm_kotlin_className}'"
                                )
                            val targetInstance =
                                (targetCompanion.io_realm_kotlin_newInstance() as BaseRealmObject)
                            (primaryKeyAccessor as KMutableProperty1<BaseRealmObject, Any?>).set(
                                targetInstance,
                                bsonValueToStorageType(
                                    CollectionType.RLM_COLLECTION_TYPE_NONE,
                                    primaryKeySchemaProperty.type.storageType,
                                    targetKClass,
                                    bsonValue
                                )
                            )
                            targetInstance
                        }
                    }
                }

                RealmStorageType.FLOAT -> bsonValue.asDouble().value.toFloat()
                RealmStorageType.DOUBLE -> bsonValue.asDouble().value
                RealmStorageType.DECIMAL128 -> bsonValue.asDecimal128()
                RealmStorageType.TIMESTAMP -> bsonValue.asDateTime().asRealmInstant()
                RealmStorageType.OBJECT_ID -> bsonValue.asObjectId()
                RealmStorageType.UUID -> bsonValue.asBinary().asRealmUUID()
                RealmStorageType.ANY -> bsonValueToRealmAny(bsonValue)
            }
            CollectionType.RLM_COLLECTION_TYPE_LIST -> {
                (
                    bsonValue.asArray().map {
                        bsonValueToStorageType(
                            CollectionType.RLM_COLLECTION_TYPE_NONE, elementType, kClass, it
                        )
                    }
                    ).toRealmList()
            }
            CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> {
                (
                    bsonValue.asDocument().mapValues {
                        bsonValueToStorageType(
                            CollectionType.RLM_COLLECTION_TYPE_NONE, elementType, kClass, it.value
                        )
                    }
                    ).toRealmDictionary()
            }

            CollectionType.RLM_COLLECTION_TYPE_SET -> {
                (
                    bsonValue.asArray().map {
                        bsonValueToStorageType(
                            CollectionType.RLM_COLLECTION_TYPE_NONE, elementType, kClass, it
                        )
                    }
                    ).toRealmSet()
            }
            else -> sdkError("Unknown collection type: $collectionType")
        }
    }

    @Suppress("ComplexMethod", "LongMethod")
    private fun bsonValueToRealmAny(
        bsonValue: BsonValue?,
    ): RealmAny? {
        return when (bsonValue?.bsonType) {
            null,
            BsonType.NULL -> null
            // RealmAny.Type.FLOAT
            // RealmAny.Type.DOUBLE
            BsonType.DOUBLE -> RealmAny.create(bsonValue.asDouble().value)
            // RealmAny.Type.STRING
            BsonType.STRING -> RealmAny.create(bsonValue.asString().value)
            // RealmAny.Type.INT
            BsonType.INT32 -> RealmAny.create(bsonValue.asInt32().value)
            BsonType.INT64 -> RealmAny.create(bsonValue.asInt64().value)
            // RealmAny.Type.DECIMAL128
            BsonType.DECIMAL128 -> RealmAny.create(bsonValue.asDecimal128())
            // RealmAny.Type.BINARY
            // RealmAny.Type.UUID handled as binary, we can't distinguish it
            BsonType.BINARY -> RealmAny.create(bsonValue.asBinary().data)
            // RealmAny.Type.OBJECT_ID
            BsonType.OBJECT_ID -> RealmAny.Companion.create(bsonValue.asObjectId())
            // RealmAny.Type.BOOL
            BsonType.BOOLEAN -> RealmAny.create(bsonValue.asBoolean().value)
            // RealmAny.Type.TIMESTAMP
            BsonType.DATE_TIME -> RealmAny.Companion.create(bsonValue.asDateTime().asRealmInstant())
            BsonType.DOCUMENT -> {
                val document = bsonValue.asDocument()
                val type: String? = document["\$ref"]?.asString()?.value
                val targetCompanion = schema[type]
                val primaryKey = document["\$id"]
                if (targetCompanion != null && primaryKey != null) {
                    val primaryKeySchemaProperty =
                        targetCompanion.io_realm_kotlin_schema().primaryKey
                            ?: throw SerializationException(
                                "Target class does not have a primary key: '${"$"}ref=$type'"
                            )
                    val (primaryKeyType, primaryKeyAccessor) = targetCompanion.io_realm_kotlin_fields[primaryKeySchemaProperty.name]
                        ?: throw SerializationException(
                            "Target class does not have a primary key: '${"$"}ref=$type'"
                        )
                    val instance: RealmObject =
                        targetCompanion.io_realm_kotlin_newInstance() as RealmObject
                    (primaryKeyAccessor as KMutableProperty1<BaseRealmObject, Any?>).set(
                        instance,
                        bsonValueToStorageType(
                            CollectionType.RLM_COLLECTION_TYPE_NONE,
                            primaryKeySchemaProperty.type.storageType,
                            primaryKeyType,
                            primaryKey
                        )
                    )
                    RealmAny.create(instance)
                } else {
                    RealmAny.create(
                        document.mapValues { (_, v) -> bsonValueToRealmAny(v) }
                            .toRealmDictionary()
                    )
                }
            }
            BsonType.ARRAY -> {
                RealmAny.create(bsonValue.asArray().map { bsonValueToRealmAny(it) }.toRealmList())
            }
            BsonType.TIMESTAMP,
            BsonType.END_OF_DOCUMENT,
            BsonType.UNDEFINED,
            BsonType.REGULAR_EXPRESSION,
            BsonType.DB_POINTER,
            BsonType.JAVASCRIPT,
            BsonType.SYMBOL,
            BsonType.JAVASCRIPT_WITH_SCOPE,
            BsonType.MIN_KEY,
            BsonType.MAX_KEY
            -> throw SerializationException("Deserializer does not support ${bsonValue.bsonType}")
        }
    }
}
