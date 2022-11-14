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

import io.realm.kotlin.internal.ObjectIdImpl
import io.realm.kotlin.mongodb.internal.BsonEncoderHelper
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BsonEncoderHelperTests {
    @Serializable
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

    @OptIn(InternalSerializationApi::class)
    @Test
    fun decodeFromBsonElement() {
        (primitiveValues + realmValues).forEach { (value, bsonValue) ->
            when (value) {
                null -> assertNull(
                    BsonEncoderHelper.decodeFromBsonValue(
                        String.serializer(),
                        bsonValue
                    )
                )
                is ByteArray -> assertContentEquals(
                    value,
                    BsonEncoderHelper.decodeFromBsonValue(
                        value::class.serializer(),
                        bsonValue
                    ) as ByteArray
                )
                else -> assertEquals(
                    value,
                    BsonEncoderHelper.decodeFromBsonValue(value::class.serializer(), bsonValue),
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
            BsonEncoderHelper.decodeFromBsonValue(SerializableClass.serializer(), BsonString(""))
        }
    }

    @Test
    fun decodeFromBsonElement_throwsWrongType() {
        assertFailsWithMessage<IllegalArgumentException>("A 'BsonDouble' is required to deserialize a 'Float'.") {
            BsonEncoderHelper.decodeFromBsonValue(Float.serializer(), BsonString(""))
        }
    }
}
