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

package io.realm.kotlin.test.mongodb.common.serializer

import io.realm.kotlin.internal.toDuration
import io.realm.kotlin.mongodb.internal.BsonEncoder
import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonBinary
import org.mongodb.kbson.BsonBinarySubType
import org.mongodb.kbson.BsonBoolean
import org.mongodb.kbson.BsonDateTime
import org.mongodb.kbson.BsonDecimal128
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonDouble
import org.mongodb.kbson.BsonInt32
import org.mongodb.kbson.BsonInt64
import org.mongodb.kbson.BsonInvalidOperationException
import org.mongodb.kbson.BsonNull
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
        val type: KClass<*>,
        val value: Any? = null,
        val bsonValue: BsonValue
    )

    private val primitiveAsserters = TypeDescriptor.classifiers
        .filter { (key, _) ->
            key != RealmAny::class && // Tested by `realmAnyEncoding`
                key != RealmObject::class // Non-primitives are not supported
        }.map { (key, _) ->
            when (key) {
                Byte::class -> DecoderAsserter(
                    type = Byte::class,
                    value = 10.toByte(),
                    bsonValue = BsonInt32(10.toByte().toInt()),
                )
                Short::class -> DecoderAsserter(
                    type = Short::class,
                    value = 20.toShort(),
                    bsonValue = BsonInt32(20.toShort().toInt())
                )
                Int::class -> DecoderAsserter(
                    type = Int::class,
                    value = 30,
                    bsonValue = BsonInt32(30)
                )
                Long::class -> DecoderAsserter(
                    type = Long::class,
                    value = 40L,
                    bsonValue = BsonInt64(40L)
                )
                Float::class -> DecoderAsserter(
                    type = Float::class,
                    value = 50F,
                    bsonValue = BsonDouble(50F.toDouble())
                )
                Double::class -> DecoderAsserter(
                    type = Double::class,
                    value = 2.0,
                    bsonValue = BsonDouble(2.0)
                )
                BsonDecimal128::class -> DecoderAsserter(
                    type = BsonDecimal128::class,
                    value = BsonDecimal128("1.2345E678"),
                    bsonValue = BsonDecimal128("1.2345E678")
                )
                Boolean::class -> DecoderAsserter(
                    type = Boolean::class,
                    value = true,
                    bsonValue = BsonBoolean.TRUE_VALUE
                )
                String::class -> DecoderAsserter(
                    type = String::class,
                    value = "hello world",
                    bsonValue = BsonString("hello world")
                )
                Char::class -> DecoderAsserter(
                    type = Char::class,
                    value = 'c',
                    bsonValue = BsonString('c'.toString())
                )
                ByteArray::class -> DecoderAsserter(
                    type = ByteArray::class,
                    value = byteArrayOf(0x00, 0x01, 0x03),
                    bsonValue = BsonBinary(
                        BsonBinarySubType.BINARY,
                        byteArrayOf(0x00, 0x01, 0x03)
                    )
                )
                ByteArray::class -> DecoderAsserter(
                    type = ByteArray::class,
                    value = null,
                    bsonValue = BsonNull
                )
                MutableRealmInt::class -> DecoderAsserter(
                    type = MutableRealmInt::class,
                    value = MutableRealmInt.create(15),
                    bsonValue = BsonInt64(15),
                )
                RealmUUID::class -> RealmUUID.from("ffffffff-ffff-ffff-ffff-ffffffffffff")
                    .let { uuid ->
                        DecoderAsserter(
                            type = RealmUUID::class,
                            value = uuid,
                            bsonValue = BsonBinary(BsonBinarySubType.UUID_STANDARD, uuid.bytes),
                        )
                    }
                BsonObjectId::class -> DecoderAsserter(
                    type = BsonObjectId::class,
                    value = BsonObjectId("507f191e810c19729de860ea"),
                    bsonValue = BsonObjectId("507f191e810c19729de860ea")
                )
                RealmInstant::class -> RealmInstant.from(
                    epochSeconds = 1668425451,
                    nanosecondAdjustment = 862000000
                ).let { instant ->
                    DecoderAsserter(
                        type = RealmInstant::class,
                        value = instant,
                        bsonValue = BsonDateTime(instant.toDuration().inWholeMilliseconds),
                    )
                }
                else -> throw IllegalStateException("classifier $key not implemented")
            }
        }

    private val listValueAsserter = DecoderAsserter(
        type = List::class,
        value = primitiveAsserters.map { it.value },
        bsonValue = BsonArray(primitiveAsserters.map { it.bsonValue })
    )

    // Map containing all BsonValues defined in primitiveAsserters with "$index" -> BsonValue
    private val mapValueAsserter = DecoderAsserter(
        type = Map::class,
        value = primitiveAsserters.mapIndexed { index, asserter ->
            "$index" to asserter.value
        }.toMap(),
        bsonValue = BsonDocument(
            primitiveAsserters.mapIndexed { index, asserter ->
                "$index" to asserter.bsonValue
            }.toMap()
        )
    )

    @Test
    fun encodeToBsonValue() {
        (primitiveAsserters + listValueAsserter + mapValueAsserter).forEach { asserter ->
            assertEquals(
                asserter.bsonValue,
                BsonEncoder.encodeToBsonValue(asserter.value)
            )
        }
    }

    @Test
    fun encodeBsonValueToBsonValue() {
        (primitiveAsserters + listValueAsserter + mapValueAsserter).forEach { asserter ->
            assertEquals(
                asserter.bsonValue,
                BsonEncoder.encodeToBsonValue(asserter.bsonValue)
            )
        }
    }

    @Test
    fun decodeFromBsonElement() {
        primitiveAsserters.forEach { asserter ->
            when (asserter.value) {
                null -> assertNull(
                    BsonEncoder.decodeFromBsonValue(
                        resultClass = asserter.type, // Arbitrary class to encode to null
                        bsonValue = asserter.bsonValue
                    )
                )
                is ByteArray -> assertContentEquals(
                    asserter.value,
                    BsonEncoder.decodeFromBsonValue(
                        resultClass = asserter.type,
                        bsonValue = asserter.bsonValue
                    ) as ByteArray,
                    "Failed to validate types ${asserter.type.simpleName} and ${asserter.bsonValue::class.simpleName}"
                )
                else -> assertEquals(
                    asserter.value,
                    BsonEncoder.decodeFromBsonValue(
                        resultClass = asserter.type,
                        bsonValue = asserter.bsonValue
                    ),
                    "Failed to validate types ${asserter.type.simpleName} and ${asserter.bsonValue::class.simpleName}"
                )
            }
        }
    }

    @Test
    fun decodeBsonElementFromBsonElement() {
        primitiveAsserters.forEach { asserter ->
            BsonEncoder.decodeFromBsonValue(
                resultClass = asserter.bsonValue::class,
                bsonValue = asserter.bsonValue
            )
        }
    }

    @Test
    fun encodeToString_throwsUnsupportedType() {
        assertFailsWithMessage<IllegalArgumentException>("Failed to convert arguments, type 'SerializableClass' not supported. Only Bson, MutableRealmInt, RealmUUID, ObjectId, RealmInstant, RealmAny, Array, Collection, Map and primitives are valid arguments types.") {
            BsonEncoder.encodeToBsonValue(SerializableClass())
        }
    }

    @Test
    fun decodeFromBsonElement_throwsUnsupportedType() {
        assertFailsWithMessage<IllegalArgumentException>("Unsupported type 'SerializableClass'. Only Bson, MutableRealmInt, RealmUUID, ObjectId, RealmInstant, RealmAny, and primitives are valid decoding types.") {
            BsonEncoder.decodeFromBsonValue(
                resultClass = SerializableClass::class,
                bsonValue = BsonString("")
            )
        }
    }

    @Test
    fun decodeFromBsonElement_throwsWrongType() {
        primitiveAsserters.forEach { asserter ->
            assertFailsWithMessage<BsonInvalidOperationException>("Cannot decode BsonValue") {
                BsonEncoder.decodeFromBsonValue(
                    resultClass = asserter.type,
                    bsonValue = BsonRegularExpression("")
                )
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
                resultClass = clazz,
                bsonValue = bsonValue
            )
        }
    }

    @Test
    fun convertNumbersLoosingPrecision() {
        numericalClassifiers.map {
            it.key as KClass<*> to BsonDouble(1.3)
        }.forEach { (clazz: KClass<*>, bsonValue: BsonValue) ->
            BsonEncoder.decodeFromBsonValue(
                resultClass = clazz,
                bsonValue = bsonValue
            )
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
        assertFailsWithMessage<IllegalArgumentException>("Cannot decode a REGULAR_EXPRESSION into RealmAny.") {
            BsonEncoder.decodeFromBsonValue(
                resultClass = RealmAny::class,
                bsonValue = BsonRegularExpression("")
            )
        }
    }
}
