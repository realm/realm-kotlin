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
 * Bson encoder that avoids using any experimental `KSerializer` APIs, to maximize compatibility it
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
     * Uses the given serialization strategy to perform a manual decode of the [BsonValue].
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
                when (resultClass) {
                    Byte::class -> {
                        deserializeNumber(bsonValue, "Byte") {
                            it.intValue().toByte()
                        }
                    }
                    Short::class -> {
                        deserializeNumber(bsonValue, "Short") {
                            it.intValue().toShort()
                        }
                    }
                    Int::class -> {
                        deserializeNumber(bsonValue, "Int") {
                            it.intValue()
                        }
                    }
                    Long::class -> {
                        deserializeNumber(bsonValue, "Long") {
                            it.longValue()
                        }
                    }
                    Float::class -> {
                        deserializeNumber(bsonValue, "Float") {
                            it.doubleValue().toFloat()
                        }
                    }
                    Double::class -> {
                        deserializeNumber(bsonValue, "Double") {
                            it.doubleValue()
                        }
                    }
                    Boolean::class -> {
                        require(bsonValue.bsonType == BsonType.BOOLEAN) {
                            "A 'BsonBoolean' is required to deserialize a 'Boolean'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonBoolean
                        bsonValue.value
                    }
                    String::class -> {
                        require(bsonValue.bsonType == BsonType.STRING) {
                            "A 'BsonString' is required to deserialize a 'String'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonString
                        bsonValue.value
                    }
                    Char::class -> {
                        require(bsonValue.bsonType == BsonType.STRING) {
                            "A 'BsonString' is required to deserialize a 'Char'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonString
                        bsonValue.value[0]
                    }
                    ByteArray::class -> {
                        require(bsonValue.bsonType == BsonType.BINARY) {
                            "A 'BsonBinary' is required to deserialize a 'ByteArray'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonBinary
                        require(bsonValue.type == BsonBinarySubType.BINARY.value) {
                            "A 'BsonBinary' with subtype 'BsonBinarySubType.BINARY' is required to deserialize a 'ByteArray'."
                        }
                        bsonValue.data
                    }
                    BsonArray::class,
                    BsonBinary::class,
                    BsonBoolean::class,
                    BsonDBPointer::class,
                    BsonDateTime::class,
                    BsonDecimal128::class,
                    BsonDocument::class,
                    BsonDouble::class,
                    BsonInt32::class,
                    BsonInt64::class,
                    BsonJavaScript::class,
                    BsonJavaScriptWithScope::class,
                    BsonMaxKey::class,
                    BsonMinKey::class,
                    BsonNull::class,
                    BsonObjectId::class,
                    BsonRegularExpression::class,
                    BsonString::class,
                    BsonSymbol::class,
                    BsonTimestamp::class,
                    BsonUndefined::class,
                    Any::class,
                    BsonValue::class -> bsonValue
                    MutableRealmInt::class -> {
                        deserializeNumber(bsonValue, "MutableRealmInt") {
                            MutableRealmInt.create(it.longValue())
                        }
                    }
                    RealmUUID::class -> {
                        require(bsonValue.bsonType == BsonType.BINARY) {
                            "A 'BsonBinary' is required to deserialize a 'RealmUUID'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonBinary
                        require(bsonValue.type == BsonBinarySubType.UUID_STANDARD.value) {
                            "A 'BsonBinary' with subtype 'BsonBinarySubType.UUID_STANDARD' is required to deserialize a 'RealmUUID'"
                        }
                        RealmUUID.from(bsonValue.data)
                    }
                    ObjectId::class -> {
                        require(bsonValue.bsonType == BsonType.OBJECT_ID) {
                            "A 'BsonObjectId' is required to deserialize a 'ObjectId'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonObjectId
                        ObjectId.from(bsonValue.toByteArray())
                    }
                    RealmInstant::class -> {
                        require(bsonValue.bsonType == BsonType.DATE_TIME) {
                            "A 'BsonDateTime' is required to deserialize a 'RealmInstant'. Type '${bsonValue.bsonType}' found."
                        }
                        bsonValue as BsonDateTime
                        bsonValue.value.milliseconds.toRealmInstant()
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
            }
        }
    }

    private inline fun <T : Number> deserializeNumber(
        bsonValue: BsonValue,
        type: String,
        block: (BsonNumber) -> T
    ): T {
        require(
            bsonValue.bsonType == BsonType.INT32 ||
                bsonValue.bsonType == BsonType.INT64 ||
                bsonValue.bsonType == BsonType.DOUBLE
        ) {
            "A 'BsonNumber' is required to deserialize a '$type'. Type '${bsonValue.bsonType}' found."
        }

        return block(bsonValue as BsonNumber).also {
            if (bsonValue.doubleValue() != it.toDouble()) {
                throw BsonInvalidOperationException("Could not convert ${bsonValue.bsonType} to a $type without losing precision")
            }
        }
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
            RealmAny.Type.OBJECT_ID -> asObjectId()
            RealmAny.Type.UUID -> asRealmUUID()
            RealmAny.Type.OBJECT -> asRealmObject()
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
