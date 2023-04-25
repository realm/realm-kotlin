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
@file:Suppress("invisible_member", "invisible_reference")
@file:OptIn(ExperimentalKSerializerApi::class)

package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.ext.toRealmDictionary
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.ext.toRealmSet
import io.realm.kotlin.internal.asBsonDateTime
import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.internal.restrictToMillisPrecision
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.Functions
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.FunctionExecutionException
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.mongodb.ext.CallBuilder
import io.realm.kotlin.mongodb.ext.call
import io.realm.kotlin.serializers.kotlinxserializers.RealmDictionaryKSerializer
import io.realm.kotlin.serializers.kotlinxserializers.RealmListKSerializer
import io.realm.kotlin.serializers.kotlinxserializers.RealmSetKSerializer
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.mongodb.syncServerAppName
import io.realm.kotlin.test.mongodb.util.BaasApp
import io.realm.kotlin.test.mongodb.util.Service
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.AUTHORIZED_ONLY_FUNCTION
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.ERROR_FUNCTION
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.FIRST_ARG_FUNCTION
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.NULL_FUNCTION
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.SUM_FUNCTION
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.VOID_FUNCTION
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.initializeDefault
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonBinary
import org.mongodb.kbson.BsonBinarySubType
import org.mongodb.kbson.BsonBoolean
import org.mongodb.kbson.BsonDBPointer
import org.mongodb.kbson.BsonDecimal128
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonDouble
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
import org.mongodb.kbson.Decimal128
import org.mongodb.kbson.ExperimentalKSerializerApi
import org.mongodb.kbson.serialization.EJson
import org.mongodb.kbson.serialization.encodeToBsonValue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

const val STRING_VALUE = "Hello world"
const val BYTE_VALUE = Byte.MAX_VALUE
const val SHORT_VALUE = Short.MAX_VALUE
const val INT_VALUE = Int.MAX_VALUE
const val LONG_VALUE = Long.MAX_VALUE
const val CHAR_VALUE = 'a'
const val FLOAT_VALUE = 1.4f
const val DOUBLE_VALUE = 1.4
val REALM_INSTANT_VALUE = RealmInstant.now().restrictToMillisPrecision()
val REALM_UUID_VALUE = RealmUUID.random()
val BYTE_ARRAY_VALUE = byteArrayOf(0, 1, 0)
val MUTABLE_REALM_INT_VALUE = MutableRealmInt.create(50)
val REALM_OBJECT_VALUE = SerializablePerson()
val LIST_VALUE = listOf("hello", "world", null)
val SET_VALUE = LIST_VALUE.toSet()
val REALM_LIST_VALUE = LIST_VALUE.toRealmList()
val REALM_SET_VALUE = SET_VALUE.toRealmSet()
val BSON_ARRAY_VALUE = BsonArray(LIST_VALUE.map { it ->
    it?.let { BsonString(it) } ?: BsonNull
})

val MAP_VALUE: Map<String, String?> = LIST_VALUE.mapIndexed { index, s ->
    "$index" to s
}.toMap()
val REALM_MAP_VALUE = MAP_VALUE.toRealmDictionary()

val BSON_DOCUMENT_VALUE = BsonDocument(
    MAP_VALUE.map { entry ->
        entry.key to (entry.value?.let { BsonString(it) } ?: BsonNull)
    }.toMap()
)

@Serializable
class SerializablePerson : RealmObject {
    var firstName: String = "FIRST NAME"
    var lastName: String = "LAST NAME"
}

class FunctionsTests {
    private data class Dog(var name: String? = null)

//    private val looperThread = BlockingLooperThread()

    private lateinit var app: TestApp
    private lateinit var functions: Functions
    private lateinit var anonUser: User

//    private lateinit var admin: ServerAdmin

//    // Custom registry with support for encoding/decoding Dogs
//    private val pojoRegistry by lazy {
//        CodecRegistries.fromRegistries(
//            app.configuration.defaultCodecRegistry,
//            CodecRegistries.fromProviders(
//                PojoCodecProvider.builder()
//                    .register(Dog::class.java)
//                    .build()
//            )
//        )
//    }

//    // Custom string decoder returning hardcoded value
//    private class CustomStringDecoder(val value: String) : Decoder<String> {
//        override fun decode(reader: BsonReader, decoderContext: DecoderContext): String {
//            reader.readString()
//            return value
//        }
//    }
//
//    // Custom codec that throws an exception when encoding/decoding integers
//    private val faultyIntegerCodec = object : Codec<Integer> {
//        override fun decode(reader: BsonReader, decoderContext: DecoderContext): Integer {
//            throw RuntimeException("Simulated error")
//        }
//
//        override fun getEncoderClass(): Class<Integer> {
//            return Integer::class.java
//        }
//
//        override fun encode(writer: BsonWriter?, value: Integer?, encoderContext: EncoderContext?) {
//            throw RuntimeException("Simulated error")
//        }
//    }
//
//    // Custom registry that throws an exception when encoding/decoding integers
//    private val faultyIntegerRegistry = CodecRegistries.fromRegistries(
//        CodecRegistries.fromProviders(IterableCodecProvider()),
//        CodecRegistries.fromCodecs(StringCodec(), faultyIntegerCodec)
//    )

    @BeforeTest
    fun setup() {
        app = TestApp(
            syncServerAppName("funcs"),
            ejson = EJson(
                serializersModule = SerializersModule {
                    polymorphic(RealmObject::class) {
                        subclass(SerializablePerson::class)
                    }
                }
            )
        ) { app: BaasApp, service: Service ->
            initializeDefault(app, service)
            app.addFunction(FIRST_ARG_FUNCTION)
            app.addFunction(NULL_FUNCTION)
            app.addFunction(SUM_FUNCTION)
            app.addFunction(ERROR_FUNCTION)
            app.addFunction(VOID_FUNCTION)
            app.addFunction(AUTHORIZED_ONLY_FUNCTION)
        }
        anonUser = runBlocking {
            app.login(Credentials.anonymous())
        }
        functions = anonUser.functions
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun exhaustiveElementClassifiersTest() {
        runBlocking {
            TypeDescriptor.elementClassifiers
                .filterNot { classifier ->
                    classifier in listOf(
                        Decimal128::class,
                        ObjectId::class, // BsonType
                        BsonObjectId::class //
                    )
                }
                .forEach { classifier ->
                    when (classifier) {
                        String::class -> testFunctionCall_String()
                        Char::class -> testFunctionCall_Char()
                        Byte::class -> testFunctionCall_Byte()
                        Short::class -> testFunctionCall_Short()
                        Int::class -> testFunctionCall_Int()
                        Long::class -> testFunctionCall_Long()
                        Float::class -> testFunctionCall_Float()
                        Double::class -> testFunctionCall_Double()
                        Boolean::class -> testFunctionCall_Boolean()
                        RealmInstant::class -> testFunctionCall_RealmInstant()
                        RealmUUID::class -> testFunctionCall_RealmUUID()
                        ByteArray::class -> testFunctionCall_ByteArray()
                        MutableRealmInt::class -> testFunctionCall_MutableRealmInt()
                        RealmObject::class -> testFunctionCall_RealmObject()
                        RealmAny::class -> testFunctionCall_RealmAny()
                        else -> error("Untested classifier $classifier")
                    }
                }
        }
    }

    @Test
    fun exhaustiveCollectionTest() {
        runBlocking {
            CollectionType.values()
                .forEach { collectionType ->
                    when (collectionType) {
                        CollectionType.RLM_COLLECTION_TYPE_NONE -> {}
                        CollectionType.RLM_COLLECTION_TYPE_LIST -> testFunctionCall_List()
                        CollectionType.RLM_COLLECTION_TYPE_SET -> testFunctionCall_Set()
                        CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> testFunctionCall_Dictionary()
                        else -> error("Untested collection type $collectionType")
                    }
                }
        }
    }

    @Test
    fun exhaustiveBsonTypesTest() {
        runBlocking {
            BsonType.values()
                .forEach {
                    when (it) {
                        BsonType.END_OF_DOCUMENT -> {} // it is not an actual BsonType
                        BsonType.DOUBLE -> testFunctionCall_BsonDouble()
                        BsonType.STRING -> testFunctionCall_BsonString()
                        BsonType.DOCUMENT -> testFunctionCall_BsonDocument()
                        BsonType.ARRAY -> testFunctionCall_BsonArray()
                        BsonType.BINARY -> testFunctionCall_BsonBinary()
                        BsonType.UNDEFINED -> testFunctionCall_BsonUndefined()
                        BsonType.OBJECT_ID -> testFunctionCall_BsonObjectId()
                        BsonType.BOOLEAN -> testFunctionCall_BsonBoolean()
                        BsonType.DATE_TIME -> testFunctionCall_BsonDateTime()
                        BsonType.NULL -> testFunctionCall_BsonNull()
                        BsonType.REGULAR_EXPRESSION -> testFunctionCall_BsonRegularExpresion()
                        BsonType.DB_POINTER -> testFunctionCall_BsonDBPointer()
                        BsonType.JAVASCRIPT -> testFunctionCall_BsonJavaScript()
                        BsonType.SYMBOL -> testFunctionCall_BsonSymbol()
                        BsonType.JAVASCRIPT_WITH_SCOPE -> testFunctionCall_BsonJavaScriptWithScope()
                        BsonType.INT32 -> testFunctionCall_BsonInt32()
                        BsonType.TIMESTAMP -> testFunctionCall_BsonTimestamp()
                        BsonType.INT64 -> testFunctionCall_BsonInt64()
                        BsonType.DECIMAL128 -> testFunctionCall_BsonDecimal128()
                        BsonType.MIN_KEY -> testFunctionCall_BsonMinKey()
                        BsonType.MAX_KEY -> testFunctionCall_BsonMaxKey()
                    }
                }
        }
    }

    private fun testFunctionCall_BsonMaxKey() {
        functionCallRoundTrip(BsonMaxKey, BsonMaxKey)
    }

    private fun testFunctionCall_BsonMinKey() {
        functionCallRoundTrip(BsonMinKey, BsonMinKey)
    }

    private fun testFunctionCall_BsonDecimal128() {
        BsonDecimal128(LONG_VALUE.toString()).let { decimal128 ->
            functionCallRoundTrip(decimal128, decimal128)
        }
    }

    private fun testFunctionCall_BsonInt64() {
        functionCallRoundTrip(BsonInt64(LONG_VALUE), LONG_VALUE)
        functionCallRoundTrip(BsonInt64(LONG_VALUE), LONG_VALUE.toLong())
        functionCallRoundTrip(BsonInt64(LONG_VALUE), LONG_VALUE.toFloat())
        functionCallRoundTrip(BsonInt64(LONG_VALUE), LONG_VALUE.toDouble())
    }

    private fun testFunctionCall_BsonTimestamp() {
        BsonTimestamp().let { timestamp ->
            functionCallRoundTrip(timestamp, timestamp)
        }
    }

    private fun testFunctionCall_BsonInt32() {
        functionCallRoundTrip(BsonInt32(BYTE_VALUE.toInt()), BYTE_VALUE)
        functionCallRoundTrip(BsonInt32(SHORT_VALUE.toInt()), SHORT_VALUE)
        functionCallRoundTrip(BsonInt32(INT_VALUE), INT_VALUE)
        functionCallRoundTrip(BsonInt32(INT_VALUE), BsonInt64(INT_VALUE.toLong()))
        functionCallRoundTrip(BsonInt32(INT_VALUE), INT_VALUE.toLong())
        functionCallRoundTrip(BsonInt32(INT_VALUE), INT_VALUE.toFloat())
        functionCallRoundTrip(BsonInt32(INT_VALUE), INT_VALUE.toDouble())
    }

    private fun testFunctionCall_BsonJavaScriptWithScope() {
        BsonJavaScriptWithScope("", BsonDocument()).let { javaScriptWithScope ->
            functionCallRoundTrip(javaScriptWithScope, javaScriptWithScope)
        }
    }

    private fun testFunctionCall_BsonSymbol() {
        BsonSymbol("").let { bsonSymbol ->
            functionCallRoundTrip(bsonSymbol, bsonSymbol)
        }
    }

    private fun testFunctionCall_BsonJavaScript() {
        BsonJavaScript("").let { bsonJavaScript ->
            functionCallRoundTrip(bsonJavaScript, bsonJavaScript)
        }
    }

    private fun testFunctionCall_BsonDBPointer() {
        BsonDBPointer(
            namespace = "namespace",
            id = BsonObjectId()
        ).let { bsonDBPointer ->
            functionCallRoundTrip(bsonDBPointer, bsonDBPointer)
        }
    }

    private fun testFunctionCall_BsonRegularExpresion() {
        BsonRegularExpression("").let { bsonRegularExpression ->
            functionCallRoundTrip(bsonRegularExpression, bsonRegularExpression)
        }
    }

    private fun testFunctionCall_BsonNull() {
        functionCallRoundTrip(BsonNull, BsonNull)
        functionCallRoundTrip(BsonNull, null as String?)
    }

    private fun testFunctionCall_BsonDateTime() {
        REALM_INSTANT_VALUE.asBsonDateTime().let { bsonDateTimeValue ->
            functionCallRoundTrip(
                bsonDateTimeValue,
                bsonDateTimeValue
            )

            functionCallRoundTrip(bsonDateTimeValue, REALM_INSTANT_VALUE)
        }
    }

    private fun testFunctionCall_BsonBoolean() {
        functionCallRoundTrip(BsonBoolean(true), BsonBoolean(true))
        functionCallRoundTrip(BsonBoolean(true), true)
    }

    private fun testFunctionCall_BsonObjectId() {
        BsonObjectId().let { objectId ->
            functionCallRoundTrip(objectId, objectId)
        }
    }

    private fun testFunctionCall_BsonUndefined() {
        functionCallRoundTrip(BsonUndefined, BsonUndefined)
    }

    private fun testFunctionCall_BsonBinary() {
        functionCallRoundTrip(
            argument = BsonBinary(BYTE_ARRAY_VALUE),
            expectedResult = BsonBinary(BYTE_ARRAY_VALUE)
        )
        functionCallRoundTrip(
            argument = BsonBinary(BYTE_ARRAY_VALUE),
            expectedResult = BYTE_ARRAY_VALUE
        )
    }

    private fun testFunctionCall_BsonString() {
        functionCallRoundTrip(
            BsonString(STRING_VALUE),
            BsonString(STRING_VALUE)
        )
        functionCallRoundTrip(BsonString(STRING_VALUE), STRING_VALUE)
    }

    private fun testFunctionCall_BsonDocument() {
        functionCallRoundTrip(BSON_DOCUMENT_VALUE, BSON_DOCUMENT_VALUE)

        assertKSerializerFunctionCall(
            BSON_DOCUMENT_VALUE,
            MAP_VALUE
        )
        assertKSerializerFunctionCall(
            argument = BSON_DOCUMENT_VALUE,
            expectedResult = REALM_MAP_VALUE
        ) { arg: BsonDocument ->
            add(arg)
            returnValueSerializer = RealmDictionaryKSerializer<String?>(String.serializer().nullable)
        }
    }

    private fun testFunctionCall_BsonArray() {
        functionCallRoundTrip(BSON_ARRAY_VALUE, BSON_ARRAY_VALUE)

        // only kserializer can deserialize RealmList and lists
        assertKSerializerFunctionCall(BSON_ARRAY_VALUE, LIST_VALUE)
        assertKSerializerFunctionCall(
            argument = BSON_ARRAY_VALUE,
            expectedResult = REALM_LIST_VALUE
        ) { arg: BsonArray ->
            add(arg)
            returnValueSerializer = RealmListKSerializer<String?>(String.serializer().nullable)
        }

        // only kserializer can deserialize RealmSet and sets
        assertKSerializerFunctionCall(BSON_ARRAY_VALUE, SET_VALUE)
        assertKSerializerFunctionCall(
            argument = BSON_ARRAY_VALUE,
            expectedResult = REALM_SET_VALUE
        ) { arg: BsonArray ->
            add(arg)
            returnValueSerializer = RealmSetKSerializer<String?>(String.serializer().nullable)
        }
    }

    private fun testFunctionCall_BsonDouble() {
        functionCallRoundTrip(
            BsonDouble(DOUBLE_VALUE),
            BsonDouble(DOUBLE_VALUE)
        )
        functionCallRoundTrip(BsonDouble(DOUBLE_VALUE), DOUBLE_VALUE)
    }

    @OptIn(ExperimentalKSerializerApi::class)
    private fun testFunctionCall_RealmObject(): BsonDocument {
        // The "stable" serializer does not support RealmObject serialization

        assertKSerializerFunctionCall(
            REALM_OBJECT_VALUE,
            REALM_OBJECT_VALUE
        )
        return assertKSerializerFunctionCall(
            argument = REALM_OBJECT_VALUE,
            expectedResult = EJson.encodeToBsonValue(
                REALM_OBJECT_VALUE
            ).asDocument()
        )
    }

    private fun testFunctionCall_RealmAny() {
        functionCallRoundTrip(
            RealmAny.create(STRING_VALUE),
            RealmAny.create(STRING_VALUE)
        )
        functionCallRoundTrip(
            RealmAny.create(INT_VALUE),
            RealmAny.create(INT_VALUE)
        )
        assertFailsWithMessage<SerializationException>("Polymorphic values are not supported.") {
            assertKSerializerFunctionCall(
                RealmAny.create(REALM_OBJECT_VALUE),
                RealmAny.create(REALM_OBJECT_VALUE)
            )
        }
    }

    private fun testFunctionCall_MutableRealmInt() {
        assertStableSerializerFunctionCall(
            argument = MUTABLE_REALM_INT_VALUE,
            expectedResult = MUTABLE_REALM_INT_VALUE,
        )

        assertKSerializerFunctionCall(
            argument = MUTABLE_REALM_INT_VALUE,
            expectedResult = MUTABLE_REALM_INT_VALUE
        )
    }

    private fun testFunctionCall_ByteArray() {
        functionCallRoundTrip(
            argument = BYTE_ARRAY_VALUE,
            expectedResult = BYTE_ARRAY_VALUE
        )

        functionCallRoundTrip(
            argument = BYTE_ARRAY_VALUE,
            expectedResult = BsonBinary(
                type = BsonBinarySubType.BINARY,
                data = BYTE_ARRAY_VALUE
            )
        )
    }

    private fun testFunctionCall_RealmUUID() {
        functionCallRoundTrip(REALM_UUID_VALUE, REALM_UUID_VALUE)
        functionCallRoundTrip(
            argument = REALM_UUID_VALUE,
            expectedResult = BsonBinary(
                type = BsonBinarySubType.UUID_STANDARD,
                data = REALM_UUID_VALUE.bytes
            )
        )
    }

    private fun testFunctionCall_RealmInstant() {
        functionCallRoundTrip(REALM_INSTANT_VALUE, REALM_INSTANT_VALUE)
        functionCallRoundTrip(REALM_INSTANT_VALUE, REALM_INSTANT_VALUE.asBsonDateTime())
    }

    private fun testFunctionCall_Boolean() {
        functionCallRoundTrip(
            argument = true,
            expectedResult = true
        )
        functionCallRoundTrip(
            argument = true,
            expectedResult = BsonBoolean(true)
        )
    }

    private fun testFunctionCall_Double() {
        functionCallRoundTrip(DOUBLE_VALUE, DOUBLE_VALUE)
        functionCallRoundTrip(DOUBLE_VALUE, BsonDouble(DOUBLE_VALUE))
        // TODO coercion with Decimal128
    }

    private fun testFunctionCall_Float() {
        functionCallRoundTrip(FLOAT_VALUE, FLOAT_VALUE)
        functionCallRoundTrip(FLOAT_VALUE, BsonDouble(FLOAT_VALUE.toDouble()))
        // TODO coercion with Decimal128
    }

    private fun testFunctionCall_Long() {
        functionCallRoundTrip(LONG_VALUE, LONG_VALUE)
        functionCallRoundTrip(LONG_VALUE, BsonInt64(LONG_VALUE))
        functionCallRoundTrip(LONG_VALUE, LONG_VALUE.toFloat())
        functionCallRoundTrip(LONG_VALUE, LONG_VALUE.toDouble())
        // TODO coercion with Decimal128
    }

    private fun testFunctionCall_Int() {
        functionCallRoundTrip(INT_VALUE, INT_VALUE)
        functionCallRoundTrip(INT_VALUE, BsonInt32(INT_VALUE))
        functionCallRoundTrip(INT_VALUE, BsonInt64(INT_VALUE.toLong()))
        functionCallRoundTrip(INT_VALUE, INT_VALUE.toLong())
        functionCallRoundTrip(INT_VALUE, INT_VALUE.toFloat())
        functionCallRoundTrip(INT_VALUE, INT_VALUE.toDouble())
        // TODO coercion with Decimal128
    }

    private fun testFunctionCall_Short() {
        functionCallRoundTrip(SHORT_VALUE, SHORT_VALUE)
        functionCallRoundTrip(SHORT_VALUE, BsonInt32(SHORT_VALUE.toInt()))
        functionCallRoundTrip(SHORT_VALUE, BsonInt64(SHORT_VALUE.toLong()))
        functionCallRoundTrip(SHORT_VALUE, SHORT_VALUE.toInt())
        functionCallRoundTrip(SHORT_VALUE, SHORT_VALUE.toLong())
        functionCallRoundTrip(SHORT_VALUE, SHORT_VALUE.toFloat())
        functionCallRoundTrip(SHORT_VALUE, SHORT_VALUE.toDouble())
        // TODO coercion with Decimal128
    }

    private fun testFunctionCall_Byte() {
        functionCallRoundTrip(BYTE_VALUE, BYTE_VALUE)
        functionCallRoundTrip(BYTE_VALUE, BsonInt32(BYTE_VALUE.toInt()))
        functionCallRoundTrip(BYTE_VALUE, BsonInt64(BYTE_VALUE.toLong()))
        functionCallRoundTrip(BYTE_VALUE, BYTE_VALUE.toShort())
        functionCallRoundTrip(BYTE_VALUE, BYTE_VALUE.toInt())
        functionCallRoundTrip(BYTE_VALUE, BYTE_VALUE.toLong())
        functionCallRoundTrip(BYTE_VALUE, BYTE_VALUE.toFloat())
        functionCallRoundTrip(BYTE_VALUE, BYTE_VALUE.toDouble())
        // TODO coercion with Decimal128
    }

    private fun testFunctionCall_Char() {
        functionCallRoundTrip(CHAR_VALUE, CHAR_VALUE)
    }

    private fun testFunctionCall_String() {
        functionCallRoundTrip(STRING_VALUE, STRING_VALUE)
        functionCallRoundTrip(STRING_VALUE, BsonString(STRING_VALUE))
    }

    private fun testFunctionCall_List() {
        // common roundtrips
        functionCallRoundTrip(LIST_VALUE, BSON_ARRAY_VALUE)

        val serializer = RealmListKSerializer<String?>(String.serializer().nullable)

        // only kserializer can deserialize RealmList and lists
        assertKSerializerFunctionCall(LIST_VALUE, LIST_VALUE)
        assertKSerializerFunctionCall(
            argument = LIST_VALUE,
            expectedResult = REALM_LIST_VALUE
        ) { arg: List<String?> ->
            add(arg)
            returnValueSerializer = serializer
        }

        assertKSerializerFunctionCall(
            argument = REALM_LIST_VALUE,
            expectedResult = LIST_VALUE
        ) { arg: RealmList<String?> ->
            add(arg, serializer)
        }

        assertKSerializerFunctionCall(
            argument = REALM_LIST_VALUE,
            expectedResult = REALM_LIST_VALUE
        ) { arg: RealmList<String?> ->
            add(arg, serializer)
            returnValueSerializer = serializer
        }
    }

    private fun testFunctionCall_Set() {
        // common roundtrips
        functionCallRoundTrip(SET_VALUE, BSON_ARRAY_VALUE)

        // these roundtrips are only possible with the kserializer function call
        val serializer = RealmSetKSerializer<String?>(String.serializer().nullable)

        assertKSerializerFunctionCall(
            SET_VALUE,
            SET_VALUE
        )
        assertKSerializerFunctionCall(
            argument = SET_VALUE,
            expectedResult = REALM_SET_VALUE
        ) { arg: Set<String?> ->
            add(arg)
            returnValueSerializer = serializer
        }

        assertKSerializerFunctionCall(
            argument = REALM_SET_VALUE,
            expectedResult = SET_VALUE
        ) { arg: RealmSet<String?> ->
            add(arg, serializer)
        }

        assertKSerializerFunctionCall(
            argument = REALM_SET_VALUE,
            expectedResult = REALM_SET_VALUE
        ) { arg: RealmSet<String?> ->
            add(arg, serializer)
            returnValueSerializer = serializer
        }
    }

    private fun testFunctionCall_Dictionary() {
        // common roundtrips
        functionCallRoundTrip(MAP_VALUE, BSON_DOCUMENT_VALUE)

        // these roundtrips are only possible with the kserializer function call
        val serializer = RealmDictionaryKSerializer<String?>(String.serializer().nullable)

        assertKSerializerFunctionCall(MAP_VALUE, MAP_VALUE)
        assertKSerializerFunctionCall(
            argument = MAP_VALUE,
            expectedResult = REALM_MAP_VALUE
        ) { arg: Map<String, String?> ->
            add(arg)
            returnValueSerializer = serializer
        }

        assertKSerializerFunctionCall(
            argument = REALM_MAP_VALUE,
            expectedResult = MAP_VALUE
        ) { arg: RealmDictionary<String?> ->
            add(arg, serializer)
        }

        assertKSerializerFunctionCall(
            argument = REALM_MAP_VALUE,
            expectedResult = REALM_MAP_VALUE
        ) { arg: RealmDictionary<String?> ->
            add(arg, serializer)
            returnValueSerializer = serializer
        }
    }

    private inline fun <reified A : Any, reified R> functionCallRoundTrip(
        argument: A,
        expectedResult: R
    ) {
        assertStableSerializerFunctionCall(
            argument = argument,
            expectedResult = expectedResult
        )
        assertKSerializerFunctionCall(
            argument = argument,
            expectedResult = expectedResult
        )
    }

    // Invokes [functions.call] with a given argument and validates that the result matches a given
    // expected result.
    private inline fun <reified A : Any, reified R> assertKSerializerFunctionCall(
        argument: A,
        expectedResult: R,
        crossinline callBuilderBlock: CallBuilder<R>.(arg: A) -> Unit = { arg ->
            add(arg)
        }
    ) = runBlocking {
        functions.call<R>(FIRST_ARG_FUNCTION.name) {
            this.callBuilderBlock(argument)
        }
    }.also { returnValue: R ->
        assertValueEquals(expectedResult, returnValue)
    }

    private fun <T> assertValueEquals(expected: T, actual: T) {
        when (expected) {
            is SerializablePerson -> {
                actual as SerializablePerson

                assertEquals(expected.firstName, actual.firstName)
                assertEquals(expected.lastName, actual.lastName)
            }
            is BsonBinary -> {
                actual as BsonBinary
                assertContentEquals(expected.data, actual.data)
            }
            is ByteArray -> {
                actual as ByteArray
                assertContentEquals(expected, actual)
            }
            is RealmUUID -> {
                actual as RealmUUID
                assertEquals(expected, actual)
            }
            is Iterable<*> -> {
                actual as Iterable<*>
                assertContentEquals(expected, actual)
            }
            else -> assertEquals(expected, actual)
        }
    }

    // Invokes [functions.call] (kserializer version) with a given argument and validates that the
    // result matches a given expected result.
    private inline fun <reified A : Any, reified R> assertStableSerializerFunctionCall(
        argument: A,
        expectedResult: R
    ) = runBlocking { functions.call<R>(FIRST_ARG_FUNCTION.name, argument) }
        .also { returnValue: R ->
            assertValueEquals(expectedResult, returnValue)
        }

    // Facilitates debugging by executing the functions on its own block.
    private inline fun <reified T> Functions.callBlocking(
        name: String,
        vararg args: Any?,
    ): T = runBlocking {
        call(name, *args)
    }

    @Test
    fun unsupportedArgumentTypeThrows() {
        assertFailsWithMessage<IllegalArgumentException>("Failed to convert arguments, type 'Dog' not supported. Only Bson, MutableRealmInt, RealmUUID, ObjectId, RealmInstant, RealmAny, Array, Collection, Map and primitives are valid arguments types.") {
            functions.callBlocking<Int>(FIRST_ARG_FUNCTION.name, Dog())
        }
    }

    @Test
    fun unsupportedReturnTypeThrows() {
        assertFailsWithMessage<IllegalArgumentException>("Unsupported type 'RealmList'. Only Bson, MutableRealmInt, RealmUUID, ObjectId, RealmInstant, RealmAny, and primitives are valid decoding types.") {
            functions.callBlocking<RealmList<String>>(FIRST_ARG_FUNCTION.name, "hello world")
        }
    }

//    @Test
//    fun asyncCallFunction() = looperThread.runBlocking {
//        functions.callFunctionAsync(FIRST_ARG_FUNCTION, listOf(32), Integer::class.java) { result ->
//            try {
//                assertEquals(32, result.orThrow.toInt())
//            } finally {
//                looperThread.testComplete()
//            }
//        }
//    }
//
//
//    @Test
//    fun codecArgumentFailure() {
//        assertFailsWithErrorCode(ErrorCode.BSON_CODEC_NOT_FOUND) {
//            functions.callFunction(FIRST_ARG_FUNCTION, listOf(Dog("PojoFido")), Dog::class.java)
//        }
//    }
//
//    @Test
//    fun asyncCodecArgumentFailure() = looperThread.runBlocking {
//        functions.callFunctionAsync(FIRST_ARG_FUNCTION, listOf(Dog("PojoFido")), Integer::class.java) { result ->
//            try {
//                assertEquals(ErrorCode.BSON_CODEC_NOT_FOUND, result.error.errorCode)
//                assertTrue(result.error.exception is CodecConfigurationException)
//            } finally {
//                looperThread.testComplete()
//            }
//        }
//    }
//
//    @Test
//    fun codecResponseFailure() {
//        assertFailsWithErrorCode(ErrorCode.BSON_CODEC_NOT_FOUND) {
//            functions.callFunction(FIRST_ARG_FUNCTION, listOf(32), Dog::class.java)
//        }
//    }
//
//    @Test
//    fun asyncCodecResponseFailure() = looperThread.runBlocking {
//        functions.callFunctionAsync(FIRST_ARG_FUNCTION, listOf(Dog("PojoFido")), Integer::class.java) { result ->
//            try {
//                assertEquals(ErrorCode.BSON_CODEC_NOT_FOUND, result.error.errorCode)
//                assertTrue(result.error.exception is CodecConfigurationException)
//            } finally {
//                looperThread.testComplete()
//            }
//        }
//    }
//
//    @Test
//    fun codecBsonEncodingFailure() {
//        assertFailsWithErrorCode(ErrorCode.BSON_ENCODING) {
//            functions.callFunction(FIRST_ARG_FUNCTION, listOf(32), String::class.java, faultyIntegerRegistry)
//        }
//    }
//
//    @Test
//    fun asyncCodecBsonEncodingFailure() = looperThread.runBlocking {
//        functions.callFunctionAsync(FIRST_ARG_FUNCTION, listOf(32), String::class.java, faultyIntegerRegistry) { result ->
//            try {
//                assertEquals(ErrorCode.BSON_ENCODING, result.error.errorCode)
//            } finally {
//                looperThread.testComplete()
//            }
//        }
//    }
//
//    @Test
//    fun codecBsonDecodingFailure() {
//        assertFailsWithErrorCode(ErrorCode.BSON_DECODING) {
//            functions.callFunction(FIRST_ARG_FUNCTION, listOf(32), String::class.java)
//        }
//    }
//
//    @Test
//    fun asyncCodecBsonDecodingFailure() = looperThread.runBlocking {
//        functions.callFunctionAsync(FIRST_ARG_FUNCTION, listOf(32), String::class.java) { result ->
//            try {
//                assertEquals(ErrorCode.BSON_DECODING, result.error.errorCode)
//                assertTrue(result.error.exception is BSONException)
//            } finally {
//                looperThread.testComplete()
//            }
//        }
//    }
//
//    @Test
//    fun localCodecRegistry() {
//        val input = Dog("PojoFido")
//        assertEquals(input, functions.callFunction(FIRST_ARG_FUNCTION, listOf(input), Dog::class.java, pojoRegistry))
//    }
//
//    @Test
//    fun asyncLocalCodecRegistry() = looperThread.runBlocking {
//        val input = Dog("PojoFido")
//        functions.callFunctionAsync(FIRST_ARG_FUNCTION, listOf(input), Dog::class.java, pojoRegistry) { result ->
//            try {
//                assertEquals(input, result.orThrow)
//            } finally {
//                looperThread.testComplete()
//            }
//        }
//    }
//
//    @Test
//    fun instanceCodecRegistry() {
//        val input = Dog("PojoFido")
//        val functionsWithCodecRegistry = anonUser.getFunctions(pojoRegistry)
//        assertEquals(input, functionsWithCodecRegistry.callFunction(FIRST_ARG_FUNCTION, listOf(input), Dog::class.java))
//    }
//
//    @Test
//    fun resultDecoder() {
//        val input = "Realm"
//        val output = "Custom Realm"
//        assertEquals(output, functions.callFunction(FIRST_ARG_FUNCTION, listOf(input), CustomStringDecoder(output)))
//    }
//
//    @Test
//    fun asyncResultDecoder() = looperThread.runBlocking {
//        val input = "Realm"
//        val output = "Custom Realm"
//        functions.callFunctionAsync(FIRST_ARG_FUNCTION, listOf(input), CustomStringDecoder(output), App.Callback<String> { result ->
//            try {
//                assertEquals(output, result.orThrow)
//            } finally {
//                looperThread.testComplete()
//            }
//        })
//    }

    @Test
    fun unknownFunction() {
        assertFailsWithMessage<FunctionExecutionException>("function not found: 'unknown'") {
            runBlocking {
                functions.call<String>("unknown", 32)
            }
        }
    }

//    @Test
//    fun asyncUnknownFunction() = looperThread.runBlocking {
//        val input = Dog("PojoFido")
//        functions.callFunctionAsync("unknown", listOf(input), Dog::class.java, pojoRegistry) { result ->
//            try {
//                assertEquals(ErrorCode.FUNCTION_NOT_FOUND, result.error.errorCode)
//            } finally {
//                looperThread.testComplete()
//            }
//        }
//    }
//
//    @Test
//    fun asyncNonLoopers() {
//        assertFailsWith<IllegalStateException> {
//            functions.callFunctionAsync(FIRST_ARG_FUNCTION, listOf(32), Integer::class.java, pojoRegistry) { result ->
//                fail()
//            }
//        }
//    }

    @Test
    fun callFunction_sum() {
        runBlocking {
            assertEquals(10, functions.call<Int>(SUM_FUNCTION.name, 1, 2, 3, 4))
        }
    }

    @Test
    fun callFunction_remoteError() {
        assertFailsWithMessage<FunctionExecutionException>("ReferenceError: 'unknown' is not defined") {
            runBlocking {
                functions.call<String>(ERROR_FUNCTION.name)
            }
        }
    }

    @Test
    fun callFunction_null() {
        runBlocking {
            assertTrue(functions.call<BsonNull>(NULL_FUNCTION.name, emptyList<Any>()).isNull())
        }
    }

    @Test
    fun callFunction_void() {
        runBlocking {
            assertEquals(
                BsonType.UNDEFINED,
                functions.call<BsonUndefined>(VOID_FUNCTION.name).bsonType
            )
        }
    }

    @Test
    fun callFunction_afterLogout() {
        runBlocking {
            anonUser.logOut()
        }
        assertFailsWithMessage<ServiceException>("[Service][Unknown(4351)] expected Authorization header with JWT") {
            runBlocking {
                functions.call(FIRST_ARG_FUNCTION.name, 1, 2, 3)
            }
        }
    }

    // Tests that functions that should not execute based on "canevalute"-expression fails.
    @Test
    fun callFunction_authorizedOnly() {
        // Not allow for anonymous user
        assertFailsWithMessage<FunctionExecutionException>("[Service][FunctionExecutionError(4313)] rule not matched for function \"authorizedOnly\"") {
            runBlocking {
                functions.call<BsonDocument>(AUTHORIZED_ONLY_FUNCTION.name, 1, 2, 3)
            }
        }

        runBlocking {
            // User email must match "canevaluate" section of servers "functions/authorizedOnly/config.json"
            val authorizedUser = app.createUserAndLogIn(
                email = "authorizeduser@example.org",
                password = "asdfasdf"
            )
            assertNotNull(
                authorizedUser.functions.call<BsonDocument>(
                    AUTHORIZED_ONLY_FUNCTION.name,
                    1,
                    2,
                    3
                )
            )
        }
    }

    @Test
    fun getApp() {
        assertEquals(app.app, functions.app)
    }

    @Test
    fun getUser() {
        assertEquals(anonUser, functions.user)
    }
//
//    @Test
//    fun defaultCodecRegistry() {
//        // TODO Maybe we should test that setting configuration specific would propagate all the way
//        //  to here, but we do not have infrastructure to easily override TestApp configuration,
//        //  and actual configuration is verified in AppConfigurationTests
//        assertEquals(app.configuration.defaultCodecRegistry, functions.defaultCodecRegistry)
//    }
//
//    @Test
//    fun customCodecRegistry() {
//        val configCodecRegistry = CodecRegistries.fromCodecs(StringCodec())
//        val customCodecRegistryFunctions = anonUser.getFunctions(configCodecRegistry)
//        assertEquals(configCodecRegistry, customCodecRegistryFunctions.defaultCodecRegistry)
//    }
//
//    @Test
//    fun illegalBsonArgument() {
//        // Coded that will generate non-BsonArray from list
//        val faultyListCodec = object : Codec<Iterable<*>> {
//            override fun getEncoderClass(): Class<Iterable<*>> {
//                return Iterable::class.java
//            }
//
//            override fun encode(writer: BsonWriter, value: Iterable<*>, encoderContext: EncoderContext) {
//                writer.writeString("Not an array")
//            }
//
//            override fun decode(reader: BsonReader?, decoderContext: DecoderContext?): ArrayList<*> {
//                TODO("Not yet implemented")
//            }
//        }
//        // Codec registry that will use the above faulty codec for lists
//        val faultyCodecRegistry = CodecRegistries.fromProviders(
//            object : CodecProvider {
//                override fun <T : Any> get(clazz: Class<T>?, registry: CodecRegistry?): Codec<T> {
//                    @Suppress("UNCHECKED_CAST")
//                    return faultyListCodec as Codec<T>
//                }
//            }
//        )
//        assertFailsWith<IllegalArgumentException> {
//            functions.callFunction(FIRST_ARG_FUNCTION, listOf("Realm"), String::class.java, faultyCodecRegistry)
//        }
//    }
//
//    // Test cases previously failing due to C++ parsing
//    @Test
//    fun roundtrip_arrayOfBinary() {
//        val value = byteArrayOf(1, 2, 3)
//        val listOf = listOf(value)
//        val actual = functions.callFunction(FIRST_ARG_FUNCTION, listOf, ByteArray::class.java)
//        assertEquals(value.toList(), actual.toList())
//    }
//
//    @Test
//    fun roundtrip_arrayOfDocuments() {
//        val map = mapOf("foo" to 5, "bar" to 7)
//        assertEquals(map, functions.callFunction(FIRST_ARG_FUNCTION, listOf(map), Map::class.java))
//    }
//
//    @Test
//    @Ignore("C++ parser does not support binary subtypes yet")
//    fun roundtrip_binaryUuid() {
//        // arg      = "{"value": {"$binary": {"base64": "JmS8oQitTny4IPS2tyjmdA==", "subType": "04"}}}"
//        // response = "{"value":{"$binary":{"base64":"JmS8oQitTny4IPS2tyjmdA==","subType":"00"}}}"
//        assertTypeOfFirstArgFunction(BsonBinary(UUID.randomUUID()), BsonBinary::class.java)
//    }
}
