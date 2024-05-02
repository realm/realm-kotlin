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
@file:Suppress("invisible_reference", "invisible_member")

package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.internal.ObjectIdImpl
import io.realm.kotlin.internal.toDuration
import io.realm.kotlin.internal.toRealmInstant
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
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
import org.mongodb.kbson.BsonInvalidOperationException
import org.mongodb.kbson.BsonJavaScript
import org.mongodb.kbson.BsonJavaScriptWithScope
import org.mongodb.kbson.BsonMaxKey
import org.mongodb.kbson.BsonMinKey
import org.mongodb.kbson.BsonNull
import org.mongodb.kbson.BsonNumber
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.BsonRegularExpression
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonSymbol
import org.mongodb.kbson.BsonTimestamp
import org.mongodb.kbson.BsonType
import org.mongodb.kbson.BsonUndefined
import org.mongodb.kbson.BsonValue
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.milliseconds

/**
 * Bson encoder that avoids using any experimental `KSerializer` APIs. To maximize compatibility it
 * only supports a limited type set:
 * - Primitives, Realm, Bson, Collections and Map types for encoding.
 * - Primitives, Realm and Bson types for decoding.
 */
@PublishedApi
internal object BsonEncoder {
    /**
     * Encodes a given value into a [BsonValue]. Only primitives, Realm, Bson, collections and maps types
     * are supported.
     *
     * @param value value to encode.
     * @return [BsonValue] representing [value].
     */
    @PublishedApi
    internal fun encodeToBsonValue(value: Any?): BsonValue = toBsonValue(value)

    /**
     * Decodes a [BsonValue] into a value of the [resultClass] class. Only primitives, Realm, Bson types are supported.
     *
     * @param resultClass class of the decoded value.
     * @param bsonValue value to decode.
     * @return decoded value.
     */
    @PublishedApi
    @Suppress("ComplexMethod", "LongMethod", "NestedBlockDepth")
    internal fun decodeFromBsonValue(
        resultClass: KClass<*>?,
        bsonValue: BsonValue,
    ): Any? {
        return when {
            resultClass == null || bsonValue == BsonNull && resultClass != BsonNull::class -> null
            else -> {
                try {
                    when (resultClass) {
                        Byte::class -> {
                            deserializeNumber(bsonValue) {
                                it.intValue().toByte()
                            }
                        }
                        Short::class -> {
                            deserializeNumber(bsonValue) {
                                it.intValue().toShort()
                            }
                        }
                        Int::class -> {
                            deserializeNumber(bsonValue) {
                                it.intValue()
                            }
                        }
                        Long::class -> {
                            deserializeNumber(bsonValue) {
                                it.longValue()
                            }
                        }
                        Float::class -> {
                            deserializeNumber(bsonValue) {
                                it.doubleValue().toFloat()
                            }
                        }
                        Double::class -> {
                            deserializeNumber(bsonValue) {
                                it.doubleValue()
                            }
                        }
                        Boolean::class -> {
                            bsonValue.asBoolean().value
                        }
                        String::class -> {
                            bsonValue.asString().value
                        }
                        Char::class -> {
                            bsonValue.asString().value[0]
                        }
                        ByteArray::class -> {
                            val bsonBinary = bsonValue.asBinary()
                            require(bsonBinary.type == BsonBinarySubType.BINARY.value) {
                                "A 'BsonBinary' with subtype 'BsonBinarySubType.BINARY' is required to deserialize a 'ByteArray'."
                            }
                            bsonBinary.data
                        }
                        BsonArray::class -> bsonValue.asArray()
                        BsonBinary::class -> bsonValue.asBinary()
                        BsonBoolean::class -> bsonValue.asBoolean()
                        BsonDBPointer::class -> bsonValue.asDBPointer()
                        BsonDateTime::class -> bsonValue.asDateTime()
                        BsonDecimal128::class -> bsonValue.asDecimal128()
                        BsonDocument::class -> bsonValue
                        BsonDouble::class -> BsonDouble(bsonValue.asNumber().doubleValue())
                        BsonInt32::class -> BsonInt32(bsonValue.asNumber().intValue())
                        BsonInt64::class -> BsonInt64(bsonValue.asNumber().longValue())
                        BsonJavaScript::class -> bsonValue.asJavaScript()
                        BsonJavaScriptWithScope::class -> bsonValue.asJavaScriptWithScope()
                        BsonMaxKey::class -> bsonValue.asBsonMaxKey()
                        BsonMinKey::class -> bsonValue.asBsonMinKey()
                        BsonNull::class -> bsonValue.asBsonNull()
                        BsonObjectId::class -> bsonValue.asObjectId()
                        BsonRegularExpression::class -> bsonValue.asRegularExpression()
                        BsonString::class -> bsonValue.asString()
                        BsonSymbol::class -> bsonValue.asSymbol()
                        BsonTimestamp::class -> bsonValue.asTimestamp()
                        BsonUndefined::class -> bsonValue.asBsonUndefined()
                        BsonValue::class -> bsonValue
                        MutableRealmInt::class -> {
                            deserializeNumber(bsonValue) {
                                MutableRealmInt.create(it.longValue())
                            }
                        }
                        RealmUUID::class -> {
                            val bsonBinary = bsonValue.asBinary()
                            require(bsonBinary.type == BsonBinarySubType.UUID_STANDARD.value) {
                                "A 'BsonBinary' with subtype 'BsonBinarySubType.UUID_STANDARD' is required to deserialize a 'RealmUUID'"
                            }
                            RealmUUID.from(bsonBinary.data)
                        }
                        ObjectId::class -> {
                            ObjectId.from(bsonValue.asObjectId().toByteArray())
                        }
                        RealmInstant::class -> {
                            bsonValue.asDateTime().value.milliseconds.toRealmInstant()
                        }
                        RealmAny::class -> {
                            when (bsonValue.bsonType) {
                                BsonType.BOOLEAN -> RealmAny.create(bsonValue.asBoolean().value)
                                BsonType.INT32 -> RealmAny.create(bsonValue.asInt32().value)
                                BsonType.INT64 -> RealmAny.create(bsonValue.asInt64().value)
                                BsonType.STRING -> RealmAny.create(bsonValue.asString().value)
                                BsonType.DOUBLE -> RealmAny.create(bsonValue.asDouble().value)
                                BsonType.BINARY -> {
                                    with(bsonValue.asBinary()) {
                                        when (this.type) {
                                            BsonBinarySubType.UUID_STANDARD.value ->
                                                RealmAny.create(RealmUUID.Companion.from(this.data))
                                            else -> RealmAny.create(this.data)
                                        }
                                    }
                                }
                                BsonType.OBJECT_ID -> RealmAny.create(bsonValue.asObjectId())
                                BsonType.DATE_TIME -> RealmAny.create(bsonValue.asDateTime().value)
                                else -> throw IllegalArgumentException("Cannot decode a ${bsonValue.bsonType} into RealmAny.")
                            }
                        }
                        else -> {
                            throw IllegalArgumentException("Unsupported type '${resultClass.simpleName}'. Only Bson, MutableRealmInt, RealmUUID, ObjectId, RealmInstant, RealmAny, and primitives are valid decoding types.")
                        }
                    }
                } catch (e: BsonInvalidOperationException) {
                    throw BsonInvalidOperationException("Cannot decode BsonValue \"$bsonValue\" of type ${bsonValue.bsonType} into ${resultClass.simpleName}", e)
                }
            }
        }
    }

    private inline fun <T : Number> deserializeNumber(
        bsonValue: BsonValue,
        block: (BsonNumber) -> T
    ): T {
        return block(bsonValue.asNumber())
    }

    private fun Collection<*>.asBsonArray(): BsonArray = BsonArray(map { toBsonValue(it) })

    private fun Array<*>.asBsonArray(): BsonArray = BsonArray(map { toBsonValue(it) })

    private fun Map<*, *>.asBsonDocument() = BsonDocument(
        map { (key, value) ->
            if (key == null) {
                throw IllegalArgumentException("Failed to convert Map to BsonDocument. Keys don't support null values.")
            }
            if (!String::class.isInstance(key)) {
                throw IllegalArgumentException("Failed to convert Map to BsonDocument. Key type must be String, ${key::class.simpleName} found.")
            }
            BsonElement(key as String, toBsonValue(value))
        }
    )

    private fun RealmAny.asBsonValue(): BsonValue = toBsonValue(
        when (type) {
            RealmAny.Type.INT -> asLong()
            RealmAny.Type.BOOL -> asBoolean()
            RealmAny.Type.STRING -> asString()
            RealmAny.Type.BINARY -> asByteArray()
            RealmAny.Type.TIMESTAMP -> asRealmInstant()
            RealmAny.Type.FLOAT -> asFloat()
            RealmAny.Type.DOUBLE -> asDouble()
            RealmAny.Type.DECIMAL128 -> asDecimal128()
            RealmAny.Type.OBJECT_ID -> asObjectId()
            RealmAny.Type.UUID -> asRealmUUID()
            RealmAny.Type.OBJECT -> asRealmObject()
            else -> TODO("Unsupported type $type")
        }
    )

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
            is RealmInstant -> BsonDateTime(value.toDuration().inWholeMilliseconds)
            is BsonValue -> value
            null -> BsonNull
            is RealmAny -> value.asBsonValue()
            is Array<*> -> value.asBsonArray()
            is Collection<*> -> value.asBsonArray()
            is Map<*, *> -> value.asBsonDocument()
            else -> throw IllegalArgumentException("Failed to convert arguments, type '${value::class.simpleName}' not supported. Only Bson, MutableRealmInt, RealmUUID, ObjectId, RealmInstant, RealmAny, Array, Collection, Map and primitives are valid arguments types.")
        }
    }
}
