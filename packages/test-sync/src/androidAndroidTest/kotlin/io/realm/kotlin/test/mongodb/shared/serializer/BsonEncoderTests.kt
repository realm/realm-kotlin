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
@file:OptIn(InternalSerializationApi::class)

package io.realm.kotlin.test.mongodb.shared.serializer

import io.realm.kotlin.internal.ObjectIdImpl
import io.realm.kotlin.internal.RealmInstantImpl
import io.realm.kotlin.internal.RealmUUIDImpl
import io.realm.kotlin.internal.UnmanagedMutableRealmInt
import io.realm.kotlin.mongodb.internal.BsonEncoder
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
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
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BsonEncoderTests {
    @kotlinx.serialization.Serializable
    class SerializableClass

    private val primitiveValues: List<Pair<Any?, BsonValue>> = listOf(
        10.toByte() to BsonInt32(10.toByte().toInt()),
        20.toShort() to BsonInt32(20.toShort().toInt()),
        30 to BsonInt32(30),
        40L to BsonInt64(40L),
        50F to BsonDouble(50F.toDouble()),
        2.0 to BsonDouble(2.0),
        true to BsonBoolean.TRUE,
        "hello world" to BsonString("hello world"),
        'c' to BsonString('c'.toString()),
        byteArrayOf(0x00, 0x01, 0x03) to BsonBinary(
            BsonBinarySubType.BINARY,
            byteArrayOf(0x00, 0x01, 0x03)
        ),
        null to BsonNull
    )

    private val realmValues = listOf(
        MutableRealmInt.create(15) to BsonInt64(15),
        RealmUUID.from("ffffffff-ffff-ffff-ffff-ffffffffffff").let {
            it to BsonBinary(BsonBinarySubType.UUID_STANDARD, it.bytes)
        },
        ObjectId.create().let {
            it to BsonObjectId((it as ObjectIdImpl).bytes)
        },
        RealmInstant.from(
            epochSeconds = 1668425451,
            nanosecondAdjustment = 862000000
        ) to BsonDateTime(1668425451862)
    )

    private val listValue: Pair<List<Any?>, BsonArray> =
        primitiveValues.map { it.first } to BsonArray(primitiveValues.map { it.second })

    private val mapValue =
        primitiveValues.mapIndexed { index, pair ->
            index.toString() to pair.first
        }.toMap() to BsonDocument(
            primitiveValues.mapIndexed { index, pair ->
                index.toString() to pair.second
            }.toMap()
        )

    @Test
    fun encodeToString() {
        (primitiveValues + realmValues + listValue + mapValue).forEach { (value, bsonValue) ->
            assertEquals(
                bsonValue,
                BsonEncoder.encodeToBsonValue(value)
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun serializer(value: KClass<*>): KSerializer<*> = when (value) {
        ObjectIdImpl::class -> BsonEncoder.serializersModule.serializer<ObjectId>()
        ObjectId::class -> BsonEncoder.serializersModule.serializer<ObjectId>()
        RealmUUID::class -> BsonEncoder.serializersModule.serializer<RealmUUID>()
        RealmUUIDImpl::class -> BsonEncoder.serializersModule.serializer<RealmUUID>()
        RealmInstant::class -> BsonEncoder.serializersModule.serializer<RealmInstant>()
        RealmInstantImpl::class -> BsonEncoder.serializersModule.serializer<RealmInstant>()
        MutableRealmInt::class -> BsonEncoder.serializersModule.serializer<MutableRealmInt>()
        UnmanagedMutableRealmInt::class -> BsonEncoder.serializersModule.serializer<MutableRealmInt>()
        else -> value.serializer()
    }

    @Test
    fun decodeFromBsonElement() {
        (primitiveValues + realmValues).forEach { (value: Any?, bsonValue) ->
            when (value) {
                null -> assertNull(
                    BsonEncoder.decodeFromBsonValue(
                        deserializationStrategy = String.serializer(),
                        bsonValue = bsonValue
                    )
                )
                is ByteArray -> assertContentEquals(
                    value,
                    BsonEncoder.decodeFromBsonValue(
                        deserializationStrategy = serializer(value::class),
                        bsonValue = bsonValue
                    ) as ByteArray,
                    "Failed to validate types ${value::class.simpleName} and ${bsonValue::class.simpleName}"
                )
                else -> assertEquals(
                    value,
                    BsonEncoder.decodeFromBsonValue(
                        deserializationStrategy = serializer(value::class),
                        bsonValue = bsonValue
                    ),
                    "Failed to validate types ${value::class.simpleName} and ${bsonValue::class.simpleName}"
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
        assertFailsWithMessage<IllegalArgumentException>("Unsupported deserializer. Only Bson and primitives types deserializers are supported.") {
            BsonEncoder.decodeFromBsonValue(
                deserializationStrategy = SerializableClass.serializer(),
                bsonValue = BsonString("")
            )
        }
    }

    private data class WrongTypeAsserter(
        val deserializedType: KClass<*>,
        val requiredBsonType: KClass<*>,
        val invalidBsonValue: BsonValue,
        val deserializationStrategy: KSerializer<*> = deserializedType.serializer()
    ) {
        fun assert() {
            assertFailsWithMessage<IllegalArgumentException>("A '${requiredBsonType.simpleName}' is required to deserialize a '${deserializedType.simpleName}'. Type '${invalidBsonValue.bsonType}' found.") {
                BsonEncoder.decodeFromBsonValue(

                    deserializationStrategy = deserializationStrategy,
                    bsonValue = invalidBsonValue
                )
            }
        }
    }

    private val primitiveAsserters = listOf(
        WrongTypeAsserter(
            deserializedType = Byte::class,
            requiredBsonType = BsonNumber::class,
            invalidBsonValue = BsonString("")
        ),
        WrongTypeAsserter(
            deserializedType = Short::class,
            requiredBsonType = BsonNumber::class,
            invalidBsonValue = BsonString("")
        ),
        WrongTypeAsserter(
            deserializedType = Int::class,
            requiredBsonType = BsonNumber::class,
            invalidBsonValue = BsonString("")
        ),
        WrongTypeAsserter(
            deserializedType = Long::class,
            requiredBsonType = BsonNumber::class,
            invalidBsonValue = BsonString("")
        ),
        WrongTypeAsserter(
            deserializedType = Float::class,
            requiredBsonType = BsonNumber::class,
            invalidBsonValue = BsonString("")
        ),
        WrongTypeAsserter(
            deserializedType = Double::class,
            requiredBsonType = BsonNumber::class,
            invalidBsonValue = BsonString("")
        ),
        WrongTypeAsserter(
            deserializedType = Boolean::class,
            requiredBsonType = BsonBoolean::class,
            invalidBsonValue = BsonString("")
        ),
        WrongTypeAsserter(
            deserializedType = String::class,
            requiredBsonType = BsonString::class,
            invalidBsonValue = BsonInt32(0)
        ),
        WrongTypeAsserter(
            deserializedType = Char::class,
            requiredBsonType = BsonString::class,
            invalidBsonValue = BsonInt32(0)
        ),
        WrongTypeAsserter(
            deserializedType = ByteArray::class,
            requiredBsonType = BsonBinary::class,
            invalidBsonValue = BsonInt32(0)
        ),
    )

    private val realmAsserters = listOf(
        WrongTypeAsserter(
            deserializedType = RealmUUID::class,
            deserializationStrategy = serializer(RealmUUID::class),
            requiredBsonType = BsonBinary::class,
            invalidBsonValue = BsonString("")
        ),
        WrongTypeAsserter(
            deserializedType = MutableRealmInt::class,
            deserializationStrategy = serializer(MutableRealmInt::class),
            requiredBsonType = BsonInt64::class,
            invalidBsonValue = BsonString("")
        ),
        // TODO enabling the following test fails with `org.jetbrains.kotlin.backend.common.BackendException: Backend Internal error: Exception during IR lowering`
//        WrongTypeAsserter(
//            deserializedType = ObjectId::class,
//            deserializationStrategy = ObjectIdImpl.serializer(),
//            requiredBsonType = BsonBinary::class,
//            invalidBsonValue = BsonString("")
//        ),
        WrongTypeAsserter(
            deserializedType = RealmInstant::class,
            deserializationStrategy = serializer(RealmInstant::class),
            requiredBsonType = BsonDateTime::class,
            invalidBsonValue = BsonString("")
        ),
    )

    @Test
    fun decodeFromBsonElement_throwsWrongType() {
        (primitiveAsserters + realmAsserters).forEach {
            it.assert()
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
                deserializationStrategy = clazz.serializer(),
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
        ).forEach { (clazz: KClass<out Number>, bsonValue: BsonValue) ->
            assertFailsWithMessage<BsonInvalidOperationException>("Could not convert DOUBLE to a ${clazz.simpleName} without losing precision") {
                BsonEncoder.decodeFromBsonValue(
                    deserializationStrategy = clazz.serializer(),
                    bsonValue = bsonValue
                )
            }
        }
    }
}
