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

package io.realm.kotlin.test.shared.serializer

import io.realm.kotlin.internal.BsonEncoderHelper
import io.realm.kotlin.internal.ObjectIdImpl
import io.realm.kotlin.internal.RealmInstantImpl
import io.realm.kotlin.internal.RealmUUIDImpl
import io.realm.kotlin.internal.UnmanagedMutableRealmInt
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Test
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonBinary
import org.mongodb.kbson.BsonBinarySubType
import org.mongodb.kbson.BsonBoolean
import org.mongodb.kbson.BsonDateTime
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonDouble
import org.mongodb.kbson.BsonInt32
import org.mongodb.kbson.BsonInt64
import org.mongodb.kbson.BsonNull
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.serialization.Bson
import kotlin.reflect.KClass
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BsonEncoderHelperTests {
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
                Bson.toJson(bsonValue),
                BsonEncoderHelper.encodeToString(value)
            )
        }
    }

    @Test
    fun decodeFromBsonElement() {
        (primitiveValues + realmValues).forEach { (value, bsonValue) ->
            when (value) {
                null -> assertNull(
                    BsonEncoderHelper.decodeFromBsonValue(
                        serializersModule = Json.serializersModule,
                        deserializationStrategy = String.serializer(),
                        bsonValue = bsonValue
                    )
                )
                is ByteArray -> assertContentEquals(
                    value,
                    BsonEncoderHelper.decodeFromBsonValue(
                        serializersModule = Json.serializersModule,
                        deserializationStrategy = value::class.serializer(),
                        bsonValue = bsonValue
                    ) as ByteArray,
                    "Failed to validate types ${value::class.simpleName} and ${bsonValue::class.simpleName}"
                )
                else -> assertEquals(
                    value,
                    BsonEncoderHelper.decodeFromBsonValue(
                        serializersModule = Json.serializersModule,
                        deserializationStrategy = value::class.serializer(),
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
            BsonEncoderHelper.encodeToString(SerializableClass())
        }
    }

    @Test
    fun decodeFromBsonElement_throwsUnsupportedType() {
        assertFailsWithMessage<IllegalArgumentException>("Unsupported deserializer. Only Bson and primitives deserializers are supported.") {
            BsonEncoderHelper.decodeFromBsonValue(
                serializersModule = Json.serializersModule,
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
                BsonEncoderHelper.decodeFromBsonValue(
                    serializersModule = Json.serializersModule,
                    deserializationStrategy = deserializationStrategy,
                    bsonValue = invalidBsonValue
                )
            }
        }
    }

    private val primitiveAsserters = listOf(
        WrongTypeAsserter(
            deserializedType = Byte::class,
            requiredBsonType = BsonInt32::class,
            invalidBsonValue = BsonString("")
        ),
        WrongTypeAsserter(
            deserializedType = Short::class,
            requiredBsonType = BsonInt32::class,
            invalidBsonValue = BsonString("")
        ),
        WrongTypeAsserter(
            deserializedType = Int::class,
            requiredBsonType = BsonInt32::class,
            invalidBsonValue = BsonString("")
        ),
        WrongTypeAsserter(
            deserializedType = Long::class,
            requiredBsonType = BsonInt64::class,
            invalidBsonValue = BsonString("")
        ),
        WrongTypeAsserter(
            deserializedType = Float::class,
            requiredBsonType = BsonDouble::class,
            invalidBsonValue = BsonString("")
        ),
        WrongTypeAsserter(
            deserializedType = Double::class,
            requiredBsonType = BsonDouble::class,
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
            deserializationStrategy = RealmUUIDImpl.serializer(),
            requiredBsonType = BsonBinary::class,
            invalidBsonValue = BsonString("")
        ),
        WrongTypeAsserter(
            deserializedType = MutableRealmInt::class,
            deserializationStrategy = UnmanagedMutableRealmInt.serializer(),
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
            deserializationStrategy = RealmInstantImpl.serializer(),
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
}
