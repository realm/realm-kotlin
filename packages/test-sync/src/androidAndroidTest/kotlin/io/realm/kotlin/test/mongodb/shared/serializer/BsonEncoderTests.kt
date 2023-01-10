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

package io.realm.kotlin.test.mongodb.shared.serializer

import io.realm.kotlin.ext.asBsonObjectId
import io.realm.kotlin.internal.toDuration
import io.realm.kotlin.mongodb.internal.BsonEncoder
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonBinary
import org.mongodb.kbson.BsonBinarySubType
import org.mongodb.kbson.BsonBoolean
import org.mongodb.kbson.BsonDateTime
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonDouble
import org.mongodb.kbson.BsonInt32
import org.mongodb.kbson.BsonInt64
import org.mongodb.kbson.BsonInvalidOperationException
import org.mongodb.kbson.BsonNull
import org.mongodb.kbson.BsonNumber
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.BsonRegularExpression
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonValue
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BsonEncoderTests {
    @kotlinx.serialization.Serializable
    private class SerializableClass

    private class DecoderAsserter(
        val valueType: KClass<*>,
        val value: Any? = null,
        val bsonType: KClass<*>,
        val bsonValue: BsonValue
    )

    private val primitiveAsserters = TypeDescriptor.classifiers
        .filter { (key, _) ->
            key != RealmAny::class && // Tested by `realmAnyEncoding`
                key != BsonObjectId::class && // Tested by `encodeBsonValueToBsonValue`
                key != RealmObject::class // Not supported
        }.map { (key, _) ->
            when (key) {
                Byte::class -> DecoderAsserter(
                    valueType = Byte::class,
                    value = 10.toByte(),
                    bsonType = BsonNumber::class,
                    bsonValue = BsonInt32(10.toByte().toInt()),
                )
                Short::class -> DecoderAsserter(
                    valueType = Short::class,
                    value = 20.toShort(),
                    bsonType = BsonNumber::class,
                    bsonValue = BsonInt32(20.toShort().toInt())
                )
                Int::class -> DecoderAsserter(
                    valueType = Int::class,
                    value = 30,
                    bsonType = BsonNumber::class,
                    bsonValue = BsonInt32(30)
                )
                Long::class -> DecoderAsserter(
                    valueType = Long::class,
                    value = 40L,
                    bsonType = BsonNumber::class,
                    bsonValue = BsonInt64(40L)
                )
                Float::class -> DecoderAsserter(
                    valueType = Float::class,
                    value = 50F,
                    bsonType = BsonNumber::class,
                    bsonValue = BsonDouble(50F.toDouble())
                )
                Double::class -> DecoderAsserter(
                    valueType = Double::class,
                    value = 2.0,
                    bsonType = BsonNumber::class,
                    bsonValue = BsonDouble(2.0)
                )
                Boolean::class -> DecoderAsserter(
                    valueType = Boolean::class,
                    value = true,
                    bsonType = BsonBoolean::class,
                    bsonValue = BsonBoolean.TRUE_VALUE
                )
                String::class -> DecoderAsserter(
                    valueType = String::class,
                    value = "hello world",
                    bsonType = BsonString::class,
                    bsonValue = BsonString("hello world")
                )
                Char::class -> DecoderAsserter(
                    valueType = Char::class,
                    value = 'c',
                    bsonType = BsonString::class,
                    bsonValue = BsonString('c'.toString())
                )
                ByteArray::class -> DecoderAsserter(
                    valueType = ByteArray::class,
                    value = byteArrayOf(0x00, 0x01, 0x03),
                    bsonType = BsonBinary::class,
                    bsonValue = BsonBinary(
                        BsonBinarySubType.BINARY,
                        byteArrayOf(0x00, 0x01, 0x03)
                    )
                )
                ByteArray::class -> DecoderAsserter(
                    valueType = ByteArray::class,
                    value = null,
                    bsonType = BsonNull::class,
                    bsonValue = BsonNull
                )
                MutableRealmInt::class -> DecoderAsserter(
                    valueType = MutableRealmInt::class,
                    value = MutableRealmInt.create(15),
                    bsonType = BsonNumber::class,
                    bsonValue = BsonInt64(15),
                )
                RealmUUID::class -> RealmUUID.from("ffffffff-ffff-ffff-ffff-ffffffffffff")
                    .let { uuid ->
                        DecoderAsserter(
                            valueType = RealmUUID::class,
                            value = uuid,
                            bsonType = BsonBinary::class,
                            bsonValue = BsonBinary(BsonBinarySubType.UUID_STANDARD, uuid.bytes),
                        )
                    }
                ObjectId::class -> ObjectId.create().let { objectId ->
                    DecoderAsserter(
                        valueType = ObjectId::class,
                        value = objectId,
                        bsonType = BsonObjectId::class,
                        bsonValue = objectId.asBsonObjectId(),
                    )
                }
                RealmInstant::class -> RealmInstant.from(
                    epochSeconds = 1668425451,
                    nanosecondAdjustment = 862000000
                ).let { instant ->
                    DecoderAsserter(
                        valueType = RealmInstant::class,
                        value = instant,
                        bsonType = BsonDateTime::class,
                        bsonValue = BsonDateTime(instant.toDuration().inWholeMilliseconds),
                    )
                }
                else -> throw IllegalStateException("classifier $key not implemented")
            }
        }

    private val listValueAsserter = DecoderAsserter(
        valueType = List::class,
        value = primitiveAsserters.map { it.value },
        bsonType = BsonArray::class,
        bsonValue = BsonArray(primitiveAsserters.map { it.bsonValue })
    )

    // Map containing all BsonValues defined in primitiveAsserters with "$index" -> BsonValue
    private val mapValueAsserter = DecoderAsserter(
        valueType = Map::class,
        value = primitiveAsserters.mapIndexed { index, asserter ->
            "$index" to asserter.value
        }.toMap(),
        bsonType = BsonDocument::class,
        bsonValue = BsonDocument(
            primitiveAsserters.mapIndexed { index, asserter ->
                "$index" to asserter.bsonValue
            }.toMap()
        )
    )

    @Test
    fun encodeToBsonValue() {
        (primitiveAsserters + listValueAsserter + mapValueAsserter).forEach { asserter ->
            with(asserter) {
                assertEquals(
                    bsonValue,
                    BsonEncoder.encodeToBsonValue(value)
                )
            }
        }
    }

    @Test
    fun encodeBsonValueToBsonValue() {
        (primitiveAsserters + listValueAsserter + mapValueAsserter).forEach { asserter ->
            with(asserter) {
                assertEquals(
                    bsonValue,
                    BsonEncoder.encodeToBsonValue(bsonValue)
                )
            }
        }
    }

    @Test
    fun decodeFromBsonElement() {
        primitiveAsserters.forEach { asserter: DecoderAsserter ->
            with(asserter) {
                when (value) {
                    null -> assertNull(
                        BsonEncoder.decodeFromBsonValue(
                            kClass = valueType, // Arbitrary class to encode to null
                            bsonValue = bsonValue
                        )
                    )
                    is ByteArray -> assertContentEquals(
                        value,
                        BsonEncoder.decodeFromBsonValue(
                            kClass = valueType,
                            bsonValue = bsonValue
                        ) as ByteArray,
                        "Failed to validate types ${valueType.simpleName} and ${bsonValue::class.simpleName}"
                    )
                    else -> assertEquals(
                        value,
                        BsonEncoder.decodeFromBsonValue(
                            kClass = valueType,
                            bsonValue = bsonValue
                        ),
                        "Failed to validate types ${valueType.simpleName} and ${bsonValue::class.simpleName}"
                    )
                }
            }
        }
    }

    @Test
    fun decodeBsonElementFromBsonElement() {
        primitiveAsserters.forEach { asserter: DecoderAsserter ->
            with(asserter) {
                BsonEncoder.decodeFromBsonValue(
                    kClass = bsonValue::class,
                    bsonValue = bsonValue
                )
            }
        }
    }

    @Test
    fun encodeToString_throwsUnsupportedType() {
        assertFailsWithMessage<IllegalArgumentException>("Failed to convert arguments, type 'SerializableClass' not supported. Only Bson, primitives, lists and maps are valid arguments types.") {
            BsonEncoder.encodeToBsonValue(SerializableClass())
        }
    }

    @Test
    fun decodeFromBsonElement_throwsUnsupportedType() {
        assertFailsWithMessage<IllegalArgumentException>("Unsupported type. Only Bson and primitives types are supported.") {
            BsonEncoder.decodeFromBsonValue(
                kClass = SerializableClass::class,
                bsonValue = BsonString("")
            )
        }
    }

    @Test
    fun decodeFromBsonElement_throwsWrongType() {
        primitiveAsserters.forEach { asserter ->
            with(asserter) {
                assertFailsWithMessage<IllegalArgumentException>("A '${bsonType.simpleName}' is required to deserialize a '${valueType.simpleName}'. Type 'REGULAR_EXPRESSION' found.") {
                    BsonEncoder.decodeFromBsonValue(
                        kClass = valueType,
                        bsonValue = BsonRegularExpression("")
                    )
                }
            }
        }
    }

    private val numericalClassifiers = TypeDescriptor.classifiers.filter {
        it.value == TypeDescriptor.CoreFieldType.INT ||
            it.value == TypeDescriptor.CoreFieldType.MUTABLE_REALM_INT ||
            it.value == TypeDescriptor.CoreFieldType.FLOAT
//            it.value == TypeDescriptor.CoreFieldType.DOUBLE // conversion from Decimal128 to Double not supported yet
    }.filter {
        it.key != Char::class // Char is encoded as a BsonString
    }

    @Test
    fun convertNumbersWithoutLoosingPrecision() {
        numericalClassifiers.map {
            it.key as KClass<*> to BsonDouble(1.0)
        }.forEach { (clazz: KClass<*>, bsonValue: BsonValue) ->
            BsonEncoder.decodeFromBsonValue(
                kClass = clazz,
                bsonValue = bsonValue
            )
        }
    }

    @Test
    fun convertNumbersLoosingPrecision_throw() {
        numericalClassifiers.map {
            it.key as KClass<*> to BsonDouble(1.3)
        }.forEach { (clazz: KClass<*>, bsonValue: BsonValue) ->
            assertFailsWithMessage<BsonInvalidOperationException>("Could not convert DOUBLE to a ${clazz.simpleName} without losing precision") {
                BsonEncoder.decodeFromBsonValue(
                    kClass = clazz,
                    bsonValue = bsonValue
                )
            }
        }
    }

    @Test
    fun convertWrongMapToBsonDocument_throw() {
        assertFailsWithMessage<IllegalArgumentException>("Failed to convert Map to BsonDocument. Keys don't support null values.") {
            BsonEncoder.encodeToBsonValue(mapOf(null to 1))
        }

        assertFailsWithMessage<IllegalArgumentException>("Failed to convert Map to BsonDocument. Key type must be String, Int found.") {
            BsonEncoder.encodeToBsonValue(mapOf(1 to 1))
        }
    }

    @Test
    fun realmAnyEncoding() {
        listOf(
            RealmAny.create(30) to BsonInt64(30),
            RealmAny.create(true) to BsonBoolean.TRUE_VALUE,
            RealmAny.create("30") to BsonString("30"),
            RealmAny.create(0.5f) to BsonDouble(0.5f.toDouble()),
            RealmAny.create(0.5) to BsonDouble(0.5),
            BsonObjectId().let {
                RealmAny.create(it) to it
            },
            RealmInstant.now().let {
                RealmAny.create(it) to BsonDateTime(it.toDuration().inWholeMilliseconds)
            },
            ByteArray(0x00).let {
                RealmAny.create(it) to BsonBinary(it)
            },
            RealmUUID.random().let {
                RealmAny.create(it) to BsonBinary(BsonBinarySubType.UUID_STANDARD, it.bytes)
            }
        ).forEach { (value, expected) ->
            assertEquals(expected, BsonEncoder.encodeToBsonValue(value))
        }
    }

    @Test
    fun realmAny_decodeUnsupportedTypeThrows() {
        assertFailsWithMessage<IllegalArgumentException>("") {
            BsonEncoder.decodeFromBsonValue(
                kClass = RealmAny::class,
                bsonValue = BsonRegularExpression("")
            )
        }
    }
}
