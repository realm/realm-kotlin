/*
 * Copyright 2020 Realm Inc.
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

@file:OptIn(InternalSerializationApi::class)
@file:Suppress("invisible_member", "invisible_reference")

package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.internal.toMillis
import io.realm.kotlin.internal.toRealmInstant
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.Functions
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.call
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.mongodb.invoke
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.mongodb.util.BaasApp
import io.realm.kotlin.test.mongodb.util.Service
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.authorizedOnlyFunction
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.errorFunction
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.firstArg
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.initializeDefault
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.nullFunction
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.sumFunction
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.voidFunction
import io.realm.kotlin.types.RealmInstant
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonBinary
import org.mongodb.kbson.BsonBoolean
import org.mongodb.kbson.BsonDecimal128
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonDouble
import org.mongodb.kbson.BsonInt32
import org.mongodb.kbson.BsonInt64
import org.mongodb.kbson.BsonNull
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.BsonType
import org.mongodb.kbson.BsonUndefined
import org.mongodb.kbson.Decimal128
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class FunctionsTests {
    companion object {
        const val FIRST_ARG_FUNCTION = "firstArg"
    }

    // Pojo class for testing custom encoder/decoder
//    @Serializable
//    private data class Dog(var name: String? = null)

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
        app = TestApp("functions") { app: BaasApp, service: Service ->
            initializeDefault(app, service)
            app.addFunction(firstArg)
            app.addFunction(nullFunction)
            app.addFunction(sumFunction)
            app.addFunction(errorFunction)
            app.addFunction(voidFunction)
            app.addFunction(authorizedOnlyFunction)
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
    fun roundtripWithSupportedTypes() {
        runBlocking {
            val i32 = 42
            val i64 = 42L

            for (type in BsonType.values()) {
                when (type) {
                    BsonType.DOUBLE -> {
                        assertEquals(
                            1.4f,
                            functions.invoke(
                                FIRST_ARG_FUNCTION,
                                listOf(1.4f),
                                Float::class.serializer()
                            ).toFloat()
                        )
                        assertEquals(
                            1.4,
                            functions.invoke(
                                FIRST_ARG_FUNCTION,
                                listOf(1.4),
                                Double::class.serializer()
                            ).toDouble()
                        )
                        assertTypeOfFirstArgFunction(BsonDouble(1.4))
                    }
                    BsonType.STRING -> {
                        assertTypeOfFirstArgFunction("Realm")
                        assertTypeOfFirstArgFunction(BsonString("Realm"))
                    }
                    BsonType.ARRAY -> {
                        val values1 = listOf<Any>(true, i32, i64)
                        assertEquals(
                            values1[0],
                            functions.invoke(
                                FIRST_ARG_FUNCTION,
                                values1,
                                Boolean::class.serializer()
                            )
                        )

                        val values2 = listOf<Any>(1, true, 3)
                        assertContentEquals(
                            expected = BsonArray(
                                listOf(
                                    BsonInt32(1),
                                    BsonBoolean.TRUE,
                                    BsonInt32(3)
                                )
                            ),
                            actual = functions.invoke<BsonArray>(
                                FIRST_ARG_FUNCTION,
                                listOf(values2)
                            )
                        )
                        val values3 = listOf(2, "Realm", 3)
                        assertContentEquals(
                            expected = BsonArray(
                                listOf(
                                    BsonInt32(2),
                                    BsonString("Realm"),
                                    BsonInt32(3)
                                )
                            ),
                            actual = functions.invoke<BsonArray>(
                                FIRST_ARG_FUNCTION,
                                listOf(values3)
                            )
                        )
                    }
                    BsonType.BINARY -> {
                        val value = byteArrayOf(1, 2, 3)
                        val actual = functions.invoke<ByteArray>(FIRST_ARG_FUNCTION, listOf(value))
                        assertContentEquals(value, actual)
                        assertTypeOfFirstArgFunction(BsonBinary(byteArrayOf(1, 2, 3)))
                    }
                    BsonType.OBJECT_ID -> {
                        assertTypeOfFirstArgFunction(io.realm.kotlin.types.ObjectId.create())
                        assertTypeOfFirstArgFunction(org.mongodb.kbson.BsonObjectId())
                    }
                    BsonType.BOOLEAN -> {
                        assertTrue(functions.invoke(FIRST_ARG_FUNCTION, listOf(true)))
                        assertTypeOfFirstArgFunction(BsonBoolean(true))
                    }
                    BsonType.INT32 -> {
                        assertEquals(
                            32,
                            functions.invoke<Int>(FIRST_ARG_FUNCTION, listOf(32)).toInt()
                        )
                        assertEquals(
                            32,
                            functions.invoke<Int>(FIRST_ARG_FUNCTION, listOf(32L)).toInt()
                        )
                        assertTypeOfFirstArgFunction(BsonInt32(32))
                    }
                    BsonType.INT64 -> {
                        assertEquals(
                            32L,
                            functions.invoke<Long>(FIRST_ARG_FUNCTION, listOf(32L)).toLong()
                        )
                        assertEquals(
                            32L,
                            functions.invoke<Long>(FIRST_ARG_FUNCTION, listOf(32)).toLong()
                        )
                        assertTypeOfFirstArgFunction(BsonInt64(32))
                    }
                    BsonType.DECIMAL128 -> {
                        assertTypeOfFirstArgFunction(Decimal128("32"))
                        assertTypeOfFirstArgFunction(BsonDecimal128("32"))
                    }
                    BsonType.DOCUMENT -> {
                        val map = mapOf("foo" to 5)
                        val document = BsonDocument(mapOf("foo" to BsonInt32(5)))

                        assertEquals(
                            document,
                            functions.invoke<BsonDocument>(FIRST_ARG_FUNCTION, listOf(map))
                        )
                        assertEquals(
                            document,
                            functions.invoke<BsonDocument>(FIRST_ARG_FUNCTION, listOf(document))
                        )

                        var documents = listOf(BsonDocument(), BsonDocument())
                        assertEquals(
                            documents[0],
                            functions.invoke<BsonDocument>(FIRST_ARG_FUNCTION, documents)
                        )

                        documents = listOf(
                            BsonDocument("KEY", BsonString("VALUE")),
                            BsonDocument("KEY", BsonString("VALUE")),
                            BsonDocument("KEY", BsonString("VALUE"))
                        )
                        assertEquals(
                            documents[0],
                            functions.invoke<BsonDocument>(FIRST_ARG_FUNCTION, documents)
                        )
                    }
                    BsonType.DATE_TIME -> {
                        // JVM and Darwing platform's RealmInstant have better precission than BsonDateTime
                        // we create a RealmInstant with loose of precision
                        val nowWithPrecisionLoose = RealmInstant.now().toMillis()
                        val now = nowWithPrecisionLoose.toRealmInstant()

                        assertEquals(
                            now,
                            functions.invoke<RealmInstant>(FIRST_ARG_FUNCTION, listOf(now))
                        )
                    }
                    BsonType.UNDEFINED,
                    BsonType.NULL -> {
                        assertNull(functions.invoke(FIRST_ARG_FUNCTION, listOf(null)))
                    }
                    BsonType.REGULAR_EXPRESSION,
                    BsonType.SYMBOL,
                    BsonType.DB_POINTER,
                    BsonType.JAVASCRIPT,
                    BsonType.JAVASCRIPT_WITH_SCOPE,
                    BsonType.TIMESTAMP,
                    BsonType.END_OF_DOCUMENT,
                    BsonType.MIN_KEY,
                    BsonType.MAX_KEY -> {
                        // Relying on org.bson codec providers for conversion, so skipping explicit
                        // tests for these more exotic types
                    }
                    else -> {
                        fail("Unsupported BsonType $type")
                    }
                }
            }
        }
    }

    private suspend inline fun <reified T : Any> assertTypeOfFirstArgFunction(
        value: T
    ): T = functions.invoke<T>(FIRST_ARG_FUNCTION, listOf(value)).also {
        assertEquals(value, it)
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
        assertFailsWithMessage<ServiceException>("[Service][FunctionNotFound(26)] function not found: 'unknown'") {
            runBlocking {
                functions.call<String>("unknown", listOf(32))
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
            assertEquals(10, functions.call<Int>("sum", 1, 2, 3, 4))
        }
    }

    @Test
    fun callFunction_remoteError() {
        assertFailsWithMessage<ServiceException>("ReferenceError: 'unknown' is not defined") {
            runBlocking {
                functions.call<String>("error")
            }
        }
    }

    @Test
    fun callFunction_null() {
        runBlocking {
            assertTrue(functions.call<BsonNull>("null", emptyList<Any>()).isNull())
        }
    }

    @Test
    fun callFunction_void() {
        runBlocking {
            assertEquals(BsonType.UNDEFINED, functions.call<BsonUndefined>("void").bsonType)
        }
    }

    @Test
    fun callFunction_afterLogout() {
        runBlocking {
            anonUser.logOut()
        }
        assertFailsWithMessage<ServiceException>("[Service][Unknown(-1)] expected Authorization header with JWT") {
            runBlocking {
                functions.call(FIRST_ARG_FUNCTION, 1, 2, 3)
            }
        }
    }

    // Tests that functions that should not execute based on "canevalute"-expression fails.
    @Test
    fun callFunction_authorizedOnly() {
        // Not allow for anonymous user
        assertFailsWithMessage<ServiceException>("[Service][FunctionExecutionError(14)] rule not matched for function \"authorizedOnly\"") {
            runBlocking {
                functions.call<BsonDocument>("authorizedOnly", 1, 2, 3)
            }
        }

        runBlocking {
            // User email must match "canevaluate" section of servers "functions/authorizedOnly/config.json"
            val authorizedUser = app.createUserAndLogIn(
                email = "authorizeduser@example.org",
                password = "asdfasdf"
            )
            assertNotNull(authorizedUser.functions.call<BsonDocument>("authorizedOnly", 1, 2, 3))
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
