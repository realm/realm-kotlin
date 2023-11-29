/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.GoogleAuthType
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.ext.call
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.common.utils.CustomLogCollector
import io.realm.kotlin.test.mongodb.syncServerAppName
import io.realm.kotlin.test.mongodb.util.TestAppInitializer
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.initializeDefault
import io.realm.kotlin.test.util.receiveOrFail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import org.mongodb.kbson.BsonNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.fail

private const val password: String = "password1234"

class HttpLogObfuscatorTests {

    private lateinit var app: TestApp
    private lateinit var channel: Channel<Operation>
    private lateinit var user: User

    private class ObfuscatorLoggerInspector(
        private val channel: Channel<Operation>
    ) : RealmLogger {

        override val level: LogLevel = LogLevel.DEBUG
        override val tag: String = "ObfuscatorLoggerInspector"

        override fun log(
            level: LogLevel,
            throwable: Throwable?,
            message: String?,
            vararg args: Any?
        ) {
            message?.also {
                if (it.contains(""""password":"***"""")) {
                    channel.send(Operation.OBFUSCATED_PASSWORD)
                } else if (it.contains(""""access_token":"***","refresh_token":"***"""")) {
                    channel.send(Operation.OBFUSCATED_ACCESS_AND_REFRESH_TOKENS)
                } else if (it.contains(""""key":"***"""")) {
                    channel.send(Operation.OBFUSCATED_API_KEY)
                } else if (it.contains(""""id_token":"***"""")) {
                    channel.send(Operation.OBFUSCATED_APPLE_OR_GOOGLE_ID_TOKEN)
                } else if (it.contains(""""accessToken":"***"""")) {
                    channel.send(Operation.OBFUSCATED_FACEBOOK)
                } else if (it.contains(""""authCode":"***"""")) {
                    channel.send(Operation.OBFUSCATED_GOOGLE_AUTH_CODE)
                } else if (it.contains(""""token":"***"""")) {
                    channel.send(Operation.OBFUSCATED_JWT)
                } else if (
                    it.contains(""""arguments":[***]""") ||
                    it.contains("BODY START\n***\nBODY END")
                ) {
                    channel.send(Operation.OBFUSCATED_CUSTOM_FUNCTION)
                } else if (it.contains(""""password":"$password"""")) {
                    channel.cancel(CancellationException("Password was not obfuscated: $message"))
                } else if (it.contains(""""(("access_token"):(".+?")),(("refresh_token"):(".+?"))""".toRegex())) {
                    channel.cancel(
                        CancellationException(
                            """Access and refresh tokens were not obfuscated: $message""".trimMargin()
                        )
                    )
                } else if (it.contains(""""(("key"):(".+?"))""".toRegex())) {
                    channel.cancel(
                        CancellationException(
                            """API key was not obfuscated: $message""".trimMargin()
                        )
                    )
                }
            }
        }
    }

    private enum class Operation {
        OBFUSCATED_PASSWORD,
        OBFUSCATED_ACCESS_AND_REFRESH_TOKENS,
        OBFUSCATED_API_KEY,
        OBFUSCATED_APPLE_OR_GOOGLE_ID_TOKEN,
        OBFUSCATED_FACEBOOK,
        OBFUSCATED_GOOGLE_AUTH_CODE,
        OBFUSCATED_JWT,
        OBFUSCATED_CUSTOM_FUNCTION
    }

    @BeforeTest
    fun setUp() {
        channel = Channel(1)
    }

    private fun initApp(): TestApp {
        return TestApp(
            this::class.simpleName,
            appName = syncServerAppName("obfsctr"),
            logLevel = LogLevel.DEBUG,
            customLogger = ObfuscatorLoggerInspector(channel),
            initialSetup = { app, service ->
                initializeDefault(app, service)
                app.addFunction(TestAppInitializer.FIRST_ARG_FUNCTION)
                app.addFunction(TestAppInitializer.SUM_FUNCTION)
                app.addFunction(TestAppInitializer.NULL_FUNCTION)
            }
        )
    }

    @AfterTest
    fun tearDown() {
        channel.close()

        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun nullObfuscator() = runBlocking {
        val logger = CustomLogCollector("NULL-OBFUSCATOR", LogLevel.DEBUG)
        app = TestApp(
            "nullObfuscator",
            appName = syncServerAppName("null-obf"),
            logLevel = LogLevel.DEBUG,
            builder = { it.httpLogObfuscator(null) },
            customLogger = logger,
            initialSetup = { app, service ->
                initializeDefault(app, service)
                app.addFunction(TestAppInitializer.FIRST_ARG_FUNCTION)
                app.addFunction(TestAppInitializer.SUM_FUNCTION)
                app.addFunction(TestAppInitializer.NULL_FUNCTION)
            }
        )

        // Create user and log in
        user = app.createUserAndLogin()

        // Create API key
        val key = user.apiKeyAuth.create("foo")
        app.login(Credentials.apiKey(key.value!!))

        // Login with Apple credentials fails as it normally does
        assertFails {
            app.login(Credentials.apple("apple"))
        }

        // Login with Facebook credentials fails as it normally does
        assertFails {
            app.login(Credentials.facebook("facebook"))
        }

        // Login with Google credentials fails as it normally does
        assertFails {
            app.login(Credentials.google("google-auth-code", GoogleAuthType.AUTH_CODE))
        }
        assertFails {
            app.login(Credentials.google("google-id-token", GoogleAuthType.ID_TOKEN))
        }

        // Login with JWT fails as it normally does
        assertFails {
            app.login(Credentials.jwt("jwt"))
        }

        // Calling functions with arguments results in these not being obfuscated
        with(user.functions) {
            call<Double>(TestAppInitializer.FIRST_ARG_FUNCTION.name, 42.0)
            call<Double>(TestAppInitializer.SUM_FUNCTION.name, 42.0, 1.0)
            call<BsonNull>(TestAppInitializer.NULL_FUNCTION.name)
        }

        // Verify that none of the logs are obfuscated
        logger.logs.forEach { message ->
            message.also {
                if (it.contains(""""password":"***"""")) {
                    fail("Password was obfuscated: $it")
                } else if (it.contains(""""access_token":"***","refresh_token":"***"""")) {
                    fail("Access/refresh tokens were obfuscated: $it")
                } else if (it.contains(""""key":"***"""")) {
                    fail("API key was obfuscated: $it")
                } else if (it.contains(""""id_token":"***"""")) {
                    fail("Apple/Google ID tokens were obfuscated: $it")
                } else if (it.contains(""""accessToken":"***"""")) {
                    fail("Facebook token was obfuscated: $it")
                } else if (it.contains(""""authCode":"***"""")) {
                    fail("Google Auth Code was obfuscated: $it")
                } else if (it.contains(""""token":"***"""")) {
                    fail("JWT key was obfuscated: $it")
                } else if (
                    it.contains(""""arguments":[***]""") ||
                    it.contains("BODY START\n***\nBODY END")
                ) {
                    fail("Custom function arguments were obfuscated: $it")
                }
            }
        }
    }

    @Test
    fun emailPassword_registerAndLogin() = runBlocking {
        app = initApp()
        createUserAndLoginAssertions()
    }

    @Test
    fun apiKey_createAndLogin() = runBlocking {
        app = initApp()
        createUserAndLoginAssertions()

        async {
            val key = user.apiKeyAuth.create("foo")
            app.login(Credentials.apiKey(key.value!!))
        }

        // Create API KEY - response (obfuscate API key)
        assertEquals(Operation.OBFUSCATED_API_KEY, channel.receiveOrFail())
        // Login API KEY - request (obfuscate API key)
        assertEquals(Operation.OBFUSCATED_API_KEY, channel.receiveOrFail())
        // Login API KEY - response (obfuscate access and refresh tokens)
        assertEquals(Operation.OBFUSCATED_ACCESS_AND_REFRESH_TOKENS, channel.receiveOrFail())
    }

    @Test
    fun apple_login() = runBlocking {
        app = initApp()

        async {
            // Testing this requires a valid token so let's just test we obfuscate the request
            assertFails {
                app.login(Credentials.apple("apple"))
            }
        }
        // Login Apple - request (obfuscate token)
        assertEquals(Operation.OBFUSCATED_APPLE_OR_GOOGLE_ID_TOKEN, channel.receiveOrFail())
    }

    @Test
    fun facebook_login() = runBlocking {
        app = initApp()

        async {
            // Testing this requires a valid token so let's just test we obfuscate the request
            assertFails {
                app.login(Credentials.facebook("facebook"))
            }
        }
        // Login Facebook - request (obfuscate token)
        assertEquals(Operation.OBFUSCATED_FACEBOOK, channel.receiveOrFail())
    }

    @Test
    fun googleAuthToken_login() = runBlocking {
        app = initApp()

        async {
            // Testing these two requires a valid token so let's just test we obfuscate the request
            assertFails {
                app.login(Credentials.google("google-auth-code", GoogleAuthType.AUTH_CODE))
            }
            assertFails {
                app.login(Credentials.google("google-id-token", GoogleAuthType.ID_TOKEN))
            }
        }
        // Login Google auth token - request (obfuscate token)
        assertEquals(Operation.OBFUSCATED_GOOGLE_AUTH_CODE, channel.receiveOrFail())
        // Login Google ID token - request (obfuscate token)
        assertEquals(Operation.OBFUSCATED_APPLE_OR_GOOGLE_ID_TOKEN, channel.receiveOrFail())
    }

    @Test
    fun jwtToken_login() = runBlocking {
        app = initApp()
        createUserAndLoginAssertions()

        async {
            // Testing this requires a valid token so let's just test we obfuscate the request
            assertFails {
                app.login(Credentials.jwt("jwt"))
            }
        }
        // Login JWT - request (obfuscate token)
        assertEquals(Operation.OBFUSCATED_JWT, channel.receiveOrFail())
    }

    @Test
    fun customFunction() = runBlocking {
        app = initApp()
        createUserAndLoginAssertions()

        async {
            with(user.functions) {
                call<Double>(TestAppInitializer.FIRST_ARG_FUNCTION.name, 42.0)
                call<Double>(TestAppInitializer.SUM_FUNCTION.name, 42.0, 1.0)
                call<BsonNull>(TestAppInitializer.NULL_FUNCTION.name)
            }
        }
        // 1st custom function call - request (obfuscate arguments)
        assertEquals(Operation.OBFUSCATED_CUSTOM_FUNCTION, channel.receiveOrFail())
        // 1st custom function call - response (obfuscate result)
        assertEquals(Operation.OBFUSCATED_CUSTOM_FUNCTION, channel.receiveOrFail())

        // 2nd custom function call - request (obfuscate arguments)
        assertEquals(Operation.OBFUSCATED_CUSTOM_FUNCTION, channel.receiveOrFail())
        // 2nd custom function call - response (obfuscate result)
        assertEquals(Operation.OBFUSCATED_CUSTOM_FUNCTION, channel.receiveOrFail())

        // 3rd custom function call - request (obfuscate arguments)
        assertEquals(Operation.OBFUSCATED_CUSTOM_FUNCTION, channel.receiveOrFail())
        // 3rd custom function call - response (obfuscate result)
        assertEquals(Operation.OBFUSCATED_CUSTOM_FUNCTION, channel.receiveOrFail())
    }

    private suspend fun createUserAndLoginAssertions() {
        coroutineScope {
            val deferred = async { user = app.createUserAndLogin() }
            assertEquals(Operation.OBFUSCATED_PASSWORD, channel.receiveOrFail())
            assertEquals(Operation.OBFUSCATED_PASSWORD, channel.receiveOrFail())
            assertEquals(Operation.OBFUSCATED_ACCESS_AND_REFRESH_TOKENS, channel.receiveOrFail())
            deferred.cancel()
        }
    }
}
