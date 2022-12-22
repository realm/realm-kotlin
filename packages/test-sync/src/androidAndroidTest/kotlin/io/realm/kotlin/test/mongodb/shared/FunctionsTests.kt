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

// We use internal serialization APIs for testing purposes. No leaks to the public API.
@file:OptIn(InternalSerializationApi::class)
@file:Suppress("invisible_member", "invisible_reference")

package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.internal.toDuration
import io.realm.kotlin.internal.toRealmInstant
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.Functions
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.call
import io.realm.kotlin.mongodb.exceptions.FunctionExecutionException
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.mongodb.util.BaasApp
import io.realm.kotlin.test.mongodb.util.Service
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.AUTHORIZED_ONLY_FUNCTION
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.ERROR_FUNCTION
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.FIRST_ARG_FUNCTION
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.NULL_FUNCTION
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.SUM_FUNCTION
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.VOID_FUNCTION
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.initializeDefault
import io.realm.kotlin.types.RealmInstant
import kotlinx.serialization.InternalSerializationApi
import org.mongodb.kbson.BsonArray
import org.mongodb.kbson.BsonBinary
import org.mongodb.kbson.BsonBoolean
import org.mongodb.kbson.BsonDBPointer
import org.mongodb.kbson.BsonDateTime
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class FunctionsTests {
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

    // Facilitates debugging by executing the functions on its own block.
    private inline fun <reified T : Any?> Functions.callBlocking(
        name: String,
        vararg args: Any?,
    ): T = runBlocking {
        functions.call(name, *args)
    }

    @Test
    fun roundtripWithSupportedTypes() {
        val i32 = 42
        val i64 = 42L

        for (type in BsonType.values()) {
            when (type) {
                BsonType.DOUBLE -> {
                    assertEquals(
                        1.4f,
                        functions.callBlocking<Float>(FIRST_ARG_FUNCTION.name, 1.4f).toFloat()
                    )
                    assertEquals(
                        1.4,
                        functions.callBlocking<Double>(FIRST_ARG_FUNCTION.name, 1.4).toDouble()
                    )
                    assertTypeOfFirstArgFunction(BsonDouble(1.4))
                }
                BsonType.STRING -> {
                    assertTypeOfFirstArgFunction("Realm")
                    assertTypeOfFirstArgFunction(BsonString("Realm"))
                }
                BsonType.ARRAY -> {
                    listOf(true, i32, i64).let { values: List<Any> ->
                        val result = functions.callBlocking<BsonArray>(FIRST_ARG_FUNCTION.name, values)
                        assertEquals(
                            values.first(),
                            result.first().asBoolean().value
                        )
                    }

                    listOf<Any>(1, true, 3).let { values: List<Any> ->
                        val result = functions.callBlocking<BsonArray>(FIRST_ARG_FUNCTION.name, values)

                        assertContentEquals(
                            expected = BsonArray(
                                listOf(
                                    BsonInt32(1),
                                    BsonBoolean.TRUE_VALUE,
                                    BsonInt32(3)
                                )
                            ),
                            actual = result
                        )
                    }

                    setOf(2, "Realm", 3).let { values: Set<Any> ->
                        val result = functions.callBlocking<BsonArray>(FIRST_ARG_FUNCTION.name, values)

                        assertContentEquals(
                            expected = BsonArray(
                                listOf(
                                    BsonInt32(2),
                                    BsonString("Realm"),
                                    BsonInt32(3)
                                )
                            ),
                            actual = result
                        )
                    }
                }
                BsonType.BINARY -> {
                    val value = byteArrayOf(1, 2, 3)
                    val actual = functions.callBlocking<ByteArray>(FIRST_ARG_FUNCTION.name, value)
                    assertContentEquals(value, actual)
                    assertTypeOfFirstArgFunction(BsonBinary(byteArrayOf(1, 2, 3)))
                }
                BsonType.OBJECT_ID -> {
                    assertTypeOfFirstArgFunction(io.realm.kotlin.types.ObjectId.create())
                    assertTypeOfFirstArgFunction(org.mongodb.kbson.BsonObjectId())
                }
                BsonType.BOOLEAN -> {
                    assertTrue(functions.callBlocking(FIRST_ARG_FUNCTION.name, true))
                    assertTypeOfFirstArgFunction(BsonBoolean(true))
                }
                BsonType.INT32 -> {
                    assertEquals(
                        32,
                        functions.callBlocking<Int>(FIRST_ARG_FUNCTION.name, 32).toInt()
                    )
                    assertEquals(
                        32,
                        functions.callBlocking<Int>(FIRST_ARG_FUNCTION.name, 32L).toInt()
                    )
                    assertTypeOfFirstArgFunction(BsonInt32(32))
                }
                BsonType.INT64 -> {
                    assertEquals(
                        32L,
                        functions.callBlocking<Long>(FIRST_ARG_FUNCTION.name, 32L).toLong()
                    )
                    assertEquals(
                        32L,
                        functions.callBlocking<Long>(FIRST_ARG_FUNCTION.name, 32).toLong()
                    )
                    assertTypeOfFirstArgFunction(BsonInt64(32))
                }
                BsonType.DECIMAL128 -> {
                    assertTypeOfFirstArgFunction(BsonDecimal128("32"))
                }
                BsonType.DOCUMENT -> {
                    val map = mapOf("foo" to 5)
                    val document = BsonDocument(mapOf("foo" to BsonInt32(5)))

                    assertEquals(
                        document,
                        functions.callBlocking<BsonDocument>(FIRST_ARG_FUNCTION.name, map)
                    )
                    assertEquals(
                        document,
                        functions.callBlocking<BsonDocument>(FIRST_ARG_FUNCTION.name, document)
                    )

                    var documents = arrayOf(BsonDocument(), BsonDocument())
                    assertEquals(
                        documents[0],
                        functions.callBlocking<BsonDocument>(FIRST_ARG_FUNCTION.name, *documents)
                    )

                    documents = arrayOf(
                        BsonDocument("KEY", BsonString("VALUE")),
                        BsonDocument("KEY", BsonString("VALUE")),
                        BsonDocument("KEY", BsonString("VALUE"))
                    )
                    assertEquals(
                        documents[0],
                        functions.callBlocking<BsonDocument>(FIRST_ARG_FUNCTION.name, *documents)
                    )
                }
                BsonType.DATE_TIME -> {
                    // RealmInstant has better precision (nanoseconds) than BsonDateTime (millis)
                    // Here we create a RealmInstant with loose of precision to match BsonDateTime
                    val nowAsDuration: Duration = RealmInstant.now().toDuration()
                    val nowInMilliseconds = nowAsDuration.inWholeMilliseconds.milliseconds
                    val now = nowInMilliseconds.toRealmInstant()
                    assertEquals(
                        now,
                        functions.callBlocking<RealmInstant>(FIRST_ARG_FUNCTION.name, now)
                    )

                    BsonDateTime().let {
                        assertEquals(it, functions.callBlocking(FIRST_ARG_FUNCTION.name, it))
                    }
                }
                BsonType.UNDEFINED -> assertEquals(
                    BsonUndefined,
                    functions.callBlocking(FIRST_ARG_FUNCTION.name, BsonUndefined)
                )
                BsonType.NULL -> {
                    assertEquals(
                        BsonNull,
                        functions.callBlocking(FIRST_ARG_FUNCTION.name, BsonNull)
                    )
                    assertNull(functions.callBlocking(FIRST_ARG_FUNCTION.name, null))
                }
                BsonType.REGULAR_EXPRESSION -> assertTypeOfFirstArgFunction(BsonRegularExpression(""))
                BsonType.SYMBOL -> assertTypeOfFirstArgFunction(BsonSymbol(""))
                BsonType.JAVASCRIPT -> assertTypeOfFirstArgFunction(BsonJavaScript(""))
                BsonType.JAVASCRIPT_WITH_SCOPE -> assertTypeOfFirstArgFunction(
                    BsonJavaScriptWithScope("", BsonDocument())
                )
                BsonType.TIMESTAMP -> assertTypeOfFirstArgFunction(BsonTimestamp())
                BsonType.MIN_KEY -> assertTypeOfFirstArgFunction(BsonMinKey)
                BsonType.MAX_KEY -> assertTypeOfFirstArgFunction(BsonMaxKey)
                BsonType.DB_POINTER -> assertTypeOfFirstArgFunction(
                    BsonDBPointer(
                        namespace = "namespace",
                        id = BsonObjectId()
                    )
                )
                BsonType.END_OF_DOCUMENT -> {
                    // Not a real Bson type
                }
                else -> {
                    fail("Unsupported BsonType $type")
                }
            }
        }
    }

    private inline fun <reified T : Any> assertTypeOfFirstArgFunction(
        value: T
    ): T = functions.callBlocking<T>(FIRST_ARG_FUNCTION.name, value).also {
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
            assertEquals(BsonType.UNDEFINED, functions.call<BsonUndefined>(VOID_FUNCTION.name).bsonType)
        }
    }

    @Test
    fun callFunction_afterLogout() {
        runBlocking {
            anonUser.logOut()
        }
        assertFailsWithMessage<ServiceException>("[Service][Unknown(-1)] expected Authorization header with JWT") {
            runBlocking {
                functions.call(FIRST_ARG_FUNCTION.name, 1, 2, 3)
            }
        }
    }

    // Tests that functions that should not execute based on "canevalute"-expression fails.
    @Test
    fun callFunction_authorizedOnly() {
        // Not allow for anonymous user
        assertFailsWithMessage<FunctionExecutionException>("[Service][FunctionExecutionError(14)] rule not matched for function \"authorizedOnly\"") {
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
            assertNotNull(authorizedUser.functions.call<BsonDocument>(AUTHORIZED_ONLY_FUNCTION.name, 1, 2, 3))
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
