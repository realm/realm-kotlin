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
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
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
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonValue
import kotlin.reflect.KClass
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Ignore
class BsonEncoderTests {
    @kotlinx.serialization.Serializable
    class SerializableClass

    private val primitiveAsserters = listOf(
        DecoderAsserter(
            deserializedType = Byte::class,
            bsonType = BsonNumber::class,
            value = 10.toByte(),
            bsonValue = BsonInt32(10.toByte().toInt()),
        ),
        DecoderAsserter(
            deserializedType = Short::class,
            bsonType = BsonNumber::class,
            value = 20.toShort(),
            bsonValue = BsonInt32(20.toShort().toInt())
        ),
        DecoderAsserter(
            deserializedType = Int::class,
            bsonType = BsonNumber::class,
            value = 30,
            bsonValue = BsonInt32(30)
        ),
        DecoderAsserter(
            deserializedType = Long::class,
            bsonType = BsonNumber::class,
            value = 40L,
            bsonValue = BsonInt64(40L)
        ),
        DecoderAsserter(
            deserializedType = Float::class,
            bsonType = BsonNumber::class,
            value = 50F,
            bsonValue = BsonDouble(50F.toDouble())
        ),
        DecoderAsserter(
            deserializedType = Double::class,
            bsonType = BsonNumber::class,
            value = 2.0,
            bsonValue = BsonDouble(2.0)
        ),
        DecoderAsserter(
            deserializedType = Boolean::class,
            value = true,
            bsonValue = BsonBoolean.TRUE_VALUE
        ),
        DecoderAsserter(
            deserializedType = String::class,
            value = "hello world",
            bsonValue = BsonString("hello world")
        ),
        DecoderAsserter(
            deserializedType = Char::class,
            value = 'c',
            bsonValue = BsonString('c'.toString())
        ),
        DecoderAsserter(
            deserializedType = ByteArray::class,
            value = byteArrayOf(0x00, 0x01, 0x03),
            bsonValue = BsonBinary(
                BsonBinarySubType.BINARY,
                byteArrayOf(0x00, 0x01, 0x03)
            )
        ),
        DecoderAsserter(
            deserializedType = ByteArray::class,
            value = null,
            bsonValue = BsonNull
        ),
    )

    private val realmValueAsserter = listOf(
        DecoderAsserter(
            deserializedType = MutableRealmInt::class,
            bsonType = BsonNumber::class,
            value = MutableRealmInt.create(15),
            bsonValue = BsonInt64(15),
        ),
        RealmUUID.from("ffffffff-ffff-ffff-ffff-ffffffffffff").let { uuid ->
            DecoderAsserter(
                deserializedType = RealmUUID::class,
                value = uuid,
                bsonValue = BsonBinary(BsonBinarySubType.UUID_STANDARD, uuid.bytes),
            )
        },
        ObjectId.create().let { objectId ->
            DecoderAsserter(
                deserializedType = ObjectId::class,
                value = objectId,
                bsonValue = objectId.asBsonObjectId(),
            )
        },
        RealmInstant.from(
            epochSeconds = 1668425451,
            nanosecondAdjustment = 862000000
        ).let { instant ->
            DecoderAsserter(
                deserializedType = RealmInstant::class,
                value = instant,
                bsonValue = BsonDateTime(instant.toDuration().inWholeMilliseconds),
            )
        },
    )

    private val listValueAsserter = DecoderAsserter(
        deserializedType = List::class,
        bsonValue = BsonArray(primitiveAsserters.map { it.bsonValue }),
        value = primitiveAsserters.map { it.value }
    )

    private val mapValueAsserter = DecoderAsserter(
        deserializedType = Map::class,
        bsonValue = BsonDocument(
            primitiveAsserters.mapIndexed { index, asserter ->
                "$index" to asserter.bsonValue
            }.toMap()
        ),
        value = primitiveAsserters.mapIndexed { index, asserter ->
            "$index" to asserter.value
        }.toMap()
    )

    @Test
    fun encodeToBsonValue() {
        (primitiveAsserters + realmValueAsserter + listValueAsserter + mapValueAsserter).forEach { asserter ->
            assertEquals(
                asserter.bsonValue,
                BsonEncoder.encodeToBsonValue(asserter.value)
            )
        }
    }

    @Test
    fun decodeFromBsonElement() {
        (primitiveAsserters + realmValueAsserter).forEach { asserter: DecoderAsserter ->
            when (asserter.value) {
                null -> assertNull(
                    BsonEncoder.decodeFromBsonValue(
                        kClassifier = asserter.deserializedType, // Arbitrary serializer to encode to null
                        bsonValue = asserter.bsonValue
                    )
                )
                is ByteArray -> assertContentEquals(
                    asserter.value,
                    BsonEncoder.decodeFromBsonValue(
                        kClassifier = asserter.deserializedType,
                        bsonValue = asserter.bsonValue
                    ) as ByteArray,
                    "Failed to validate types ${asserter.deserializedType.simpleName} and ${asserter.bsonValue::class.simpleName}"
                )
                else -> assertEquals(
                    asserter.value,
                    BsonEncoder.decodeFromBsonValue(
                        kClassifier = asserter.deserializedType,
                        bsonValue = asserter.bsonValue
                    ),
                    "Failed to validate types ${asserter.deserializedType.simpleName} and ${asserter.bsonValue::class.simpleName}"
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
                kClassifier = SerializableClass::class,
                bsonValue = BsonString("")
            )
        }
    }

    private class DecoderAsserter(
        val deserializedType: KClass<*>,
        bsonType: KClass<*>? = null,
        val bsonValue: BsonValue,
        val value: Any? = null
    ) {
        val bsonType: KClass<*> = bsonType ?: bsonValue::class
    }

    private val wrongPrimitiveAsserters = listOf(
        DecoderAsserter(
            deserializedType = Byte::class,
            bsonType = BsonNumber::class,
            bsonValue = BsonString("")
        ),
        DecoderAsserter(
            deserializedType = Short::class,
            bsonType = BsonNumber::class,
            bsonValue = BsonString("")
        ),
        DecoderAsserter(
            deserializedType = Int::class,
            bsonType = BsonNumber::class,
            bsonValue = BsonString("")
        ),
        DecoderAsserter(
            deserializedType = Long::class,
            bsonType = BsonNumber::class,
            bsonValue = BsonString("")
        ),
        DecoderAsserter(
            deserializedType = Float::class,
            bsonType = BsonNumber::class,
            bsonValue = BsonString("")
        ),
        DecoderAsserter(
            deserializedType = Double::class,
            bsonType = BsonNumber::class,
            bsonValue = BsonString("")
        ),
        DecoderAsserter(
            deserializedType = Boolean::class,
            bsonType = BsonBoolean::class,
            bsonValue = BsonString("")
        ),
        DecoderAsserter(
            deserializedType = String::class,
            bsonType = BsonString::class,
            bsonValue = BsonInt32(0)
        ),
        DecoderAsserter(
            deserializedType = Char::class,
            bsonType = BsonString::class,
            bsonValue = BsonInt32(0)
        ),
        DecoderAsserter(
            deserializedType = ByteArray::class,
            bsonType = BsonBinary::class,
            bsonValue = BsonInt32(0)
        ),
    )

    private val wrongRealmValueAsserters = listOf(
        DecoderAsserter(
            deserializedType = RealmUUID::class,
            bsonType = BsonBinary::class,
            bsonValue = BsonString("")
        ),
        DecoderAsserter(
            deserializedType = MutableRealmInt::class,
            bsonType = BsonInt64::class,
            bsonValue = BsonString("")
        ),
        DecoderAsserter(
            deserializedType = ObjectId::class,
            bsonType = BsonObjectId::class,
            bsonValue = BsonString("")
        ),
        DecoderAsserter(
            deserializedType = RealmInstant::class,
            bsonType = BsonDateTime::class,
            bsonValue = BsonString("")
        ),
    )

    @Test
    fun decodeFromBsonElement_throwsWrongType() {
        (wrongPrimitiveAsserters + wrongRealmValueAsserters).forEach {
            with(it) {
                assertFailsWithMessage<IllegalArgumentException>("A '${bsonType.simpleName}' is required to deserialize a '${deserializedType.simpleName}'. Type '${bsonValue.bsonType}' found.") {
                    BsonEncoder.decodeFromBsonValue(
                        kClassifier = deserializedType,
                        bsonValue = bsonValue
                    )
                }
            }
        }
    }

    @Test
    fun convertNumbersWithoutLoosingPrecision() {
        listOf(
            Short::class to BsonDouble(1.0),
            Int::class to BsonDouble(1.0),
            Long::class to BsonDouble(1.0),
            Float::class to BsonDouble(1.0),
//            Double::class to BsonDecimal128.POSITIVE_ZERO, // conversion from Decimal128 to Double not supported yet
        ).forEach { (clazz: KClass<out Number>, bsonValue: BsonValue) ->
            BsonEncoder.decodeFromBsonValue(
                kClassifier = clazz,
                bsonValue = bsonValue
            )
        }
    }

    @Test
    fun convertNumbersLoosingPrecision_throw() {
        listOf(
            Short::class to BsonDouble(1.3),
            Int::class to BsonDouble(1.3),
            Long::class to BsonDouble(1.3),
            Float::class to BsonDouble(1.3),
//        Double::class to BsonDecimal128.POSITIVE_INFINITY,// conversion from Decimal128 to Double not supported yet
        ).forEach { (clazz: KClass<*>, bsonValue: BsonValue) ->
            assertFailsWithMessage<BsonInvalidOperationException>("Could not convert DOUBLE to a ${clazz.simpleName} without losing precision") {
                BsonEncoder.decodeFromBsonValue(
                    kClassifier = clazz,
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
}
