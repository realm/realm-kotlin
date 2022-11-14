/*
 * Copyright 2022 Realm Inc.
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
package io.realm.kotlin.internal

import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonBinary
import org.mongodb.kbson.BsonBinarySubType
import org.mongodb.kbson.BsonBoolean
import org.mongodb.kbson.BsonDBPointer
import org.mongodb.kbson.BsonDateTime
import org.mongodb.kbson.BsonDecimal128
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonDouble
import org.mongodb.kbson.BsonElement
import org.mongodb.kbson.BsonInt32
import org.mongodb.kbson.BsonInt64
import org.mongodb.kbson.BsonJavaScript
import org.mongodb.kbson.BsonJavaScriptWithScope
import org.mongodb.kbson.BsonMaxKey
import org.mongodb.kbson.BsonMinKey
import org.mongodb.kbson.BsonNull
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.BsonRegularExpression
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonSymbol
import org.mongodb.kbson.BsonTimestamp
import org.mongodb.kbson.BsonType
import org.mongodb.kbson.BsonUndefined
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.serialization.Bson

/**
 * TODO Document
 */
public object BsonEncoderHelper {
    /**
     * TODO Document
     */
    public fun encodeToString(value: Any?): String = Bson.toJson(toBsonValue(value))

    /**
     * TODO Document
     */
    @Suppress("UNCHECKED_CAST", "IMPLICIT_CAST_TO_ANY", "ComplexMethod", "LongMethod")
    public fun <T : Any?> decodeFromBsonValue(
        serializersModule: SerializersModule,
        deserializationStrategy: DeserializationStrategy<T>,
        bsonValue: BsonValue,
    ): T {
        return if (bsonValue == BsonNull) {
            null
        } else {
            with(serializersModule) {
                when (deserializationStrategy) {
                    serializer<Byte>() -> {
                        require(bsonValue.bsonType == BsonType.INT32) {
                            "A 'BsonInt32' is required to deserialize a 'Byte'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonInt32
                        bsonValue.value.toByte()
                    }
                    serializer<Short>() -> {
                        require(bsonValue.bsonType == BsonType.INT32) {
                            "A 'BsonInt32' is required to deserialize a 'Short'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonInt32
                        bsonValue.value.toShort()
                    }
                    serializer<Int>() -> {
                        require(bsonValue.bsonType == BsonType.INT32) {
                            "A 'BsonInt32' is required to deserialize a 'Int'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonInt32
                        bsonValue.value
                    }
                    serializer<Long>() -> {
                        require(bsonValue.bsonType == BsonType.INT64) {
                            "A 'BsonInt64' is required to deserialize a 'Long'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonInt64
                        bsonValue.value
                    }
                    serializer<Float>() -> {
                        require(bsonValue.bsonType == BsonType.DOUBLE) {
                            "A 'BsonDouble' is required to deserialize a 'Float'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonDouble
                        bsonValue.value.toFloat()
                    }
                    serializer<Double>() -> {
                        require(bsonValue.bsonType == BsonType.DOUBLE) {
                            "A 'BsonDouble' is required to deserialize a 'Double'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonDouble
                        bsonValue.value
                    }
                    serializer<Boolean>() -> {
                        require(bsonValue.bsonType == BsonType.BOOLEAN) {
                            "A 'BsonBoolean' is required to deserialize a 'Boolean'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonBoolean
                        bsonValue.value
                    }
                    serializer<String>() -> {
                        require(bsonValue.bsonType == BsonType.STRING) {
                            "A 'BsonString' is required to deserialize a 'String'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonString
                        bsonValue.value
                    }
                    serializer<Char>() -> {
                        require(bsonValue.bsonType == BsonType.STRING) {
                            "A 'BsonString' is required to deserialize a 'Char'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonString
                        bsonValue.value[0]
                    }
                    serializer<ByteArray>() -> {
                        require(bsonValue.bsonType == BsonType.BINARY) {
                            "A 'BsonBinary' is required to deserialize a 'ByteArray'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonBinary
                        require(bsonValue.type == BsonBinarySubType.BINARY.value) {
                            "A 'BsonBinary' with subtype 'BsonBinarySubType.BINARY' is required to deserialize a 'ByteArray'."
                        }
                        bsonValue.data
                    }
                    serializer<BsonArray>(),
                    serializer<BsonBinary>(),
                    serializer<BsonBoolean>(),
                    serializer<BsonDBPointer>(),
                    serializer<BsonDateTime>(),
                    serializer<BsonDecimal128>(),
                    serializer<BsonDocument>(),
                    serializer<BsonDouble>(),
                    serializer<BsonInt32>(),
                    serializer<BsonInt64>(),
                    serializer<BsonJavaScript>(),
                    serializer<BsonJavaScriptWithScope>(),
                    serializer<BsonMaxKey>(),
                    serializer<BsonMinKey>(),
                    serializer<BsonNull>(),
                    serializer<BsonObjectId>(),
                    serializer<BsonRegularExpression>(),
                    serializer<BsonString>(),
                    serializer<BsonSymbol>(),
                    serializer<BsonTimestamp>(),
                    serializer<BsonUndefined>(),
                    serializer<BsonValue>() -> bsonValue
                    serializer<UnmanagedMutableRealmInt>() -> {
                        require(bsonValue.bsonType == BsonType.INT64) {
                            "A 'BsonInt64' is required to deserialize a 'MutableRealmInt'. Type '${bsonValue.bsonType}' found."
                        }
                        MutableRealmInt.create(bsonValue.asInt64().value)
                    }
                    serializer<RealmUUIDImpl>() -> {
                        require(bsonValue.bsonType == BsonType.BINARY) {
                            "A 'BsonBinary' is required to deserialize a 'RealmUUID'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonBinary
                        require(bsonValue.type == BsonBinarySubType.UUID_STANDARD.value) {
                            "A 'BsonBinary' with subtype 'BsonBinarySubType.UUID_STANDARD' is required to deserialize a 'RealmUUID'"
                        }
                        RealmUUID.from(bsonValue.data)
                    }
                    serializer<ObjectIdImpl>() -> {
                        require(bsonValue.bsonType == BsonType.OBJECT_ID) {
                            "A 'BsonObjectId' is required to deserialize an 'ObjectId'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonObjectId
                        ObjectId.from(bsonValue.toByteArray())
                    }
                    serializer<RealmInstantImpl>() -> {
                        require(bsonValue.bsonType == BsonType.DATE_TIME) {
                            "A 'BsonDateTime' is required to deserialize a 'RealmInstant'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonDateTime
                        bsonValue.value.toRealmInstant()
                    }
                    else -> {
                        throw IllegalArgumentException("Unsupported deserializer. Only Bson and primitives deserializers are supported.")
                    }
                }
            }
        } as T
    }

    private fun List<*>.asBsonArray(): BsonArray = BsonArray(map { toBsonValue(it) })

    private fun Map<*, *>.asBsonDocument() = BsonDocument(
        castOrThrow<Map<String, Any?>>().map { entry ->
            BsonElement(entry.key, toBsonValue(entry.value))
        }
    )

    // Casts a value as the type parameter, throws argument exception otherwise
    private inline fun <reified T> Any.castOrThrow(): T = if (this is T) {
        this
    } else {
        throw IllegalArgumentException("Failed to convert arguments, could not cast map to ${T::class.simpleName}")
    }

    @Suppress("ComplexMethod")
    private fun toBsonValue(value: Any?): BsonValue {
        return when (value) {
            is Byte -> BsonInt32(value.toInt())
            is Short -> BsonInt32(value.toInt())
            is Int -> BsonInt32(value.toInt())
            is Long -> BsonInt64(value)
            is Float -> BsonDouble(value.toDouble())
            is Double -> BsonDouble(value)
            is Boolean -> BsonBoolean(value)
            is String -> BsonString(value)
            is Char -> BsonString(value.toString())
            is ByteArray -> BsonBinary(BsonBinarySubType.BINARY, value)
            is MutableRealmInt -> BsonInt64(value.toLong())
            is RealmUUID -> BsonBinary(BsonBinarySubType.UUID_STANDARD, value.bytes)
            is ObjectId -> BsonObjectId((value as ObjectIdImpl).bytes)
            is RealmInstant -> BsonDateTime(value.toMillis())
            is BsonValue -> value
            null -> BsonNull
            is List<*> -> value.asBsonArray()
            is Map<*, *> -> value.asBsonDocument()
            else -> throw IllegalArgumentException("Failed to convert arguments, type '${value::class.simpleName}' not supported. Only Bson, primitives, lists and maps are valid arguments types.")
        }
    }
}
