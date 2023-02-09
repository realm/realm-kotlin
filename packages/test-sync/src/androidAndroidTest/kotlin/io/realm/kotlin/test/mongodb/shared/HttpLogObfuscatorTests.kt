package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.GoogleAuthType
import io.realm.kotlin.mongodb.HttpLogObfuscator
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.ext.call
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.util.TestAppInitializer
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.initializeDefault
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
                    channel.trySend(Operation.OBFUSCATED_PASSWORD)
                } else if (it.contains(""""access_token":"***","refresh_token":"***"""")) {
                    channel.trySend(Operation.OBFUSCATED_ACCESS_AND_REFRESH_TOKENS)
                } else if (it.contains(""""key":"***"""")) {
                    channel.trySend(Operation.OBFUSCATED_API_KEY)
                } else if (it.contains(""""id_token":"***"""")) {
                    channel.trySend(Operation.OBFUSCATED_APPLE_OR_GOOGLE_ID_TOKEN)
                } else if (it.contains(""""accessToken":"***"""")) {
                    channel.trySend(Operation.OBFUSCATED_FACEBOOK)
                } else if (it.contains(""""authCode":"***"""")) {
                    channel.trySend(Operation.OBFUSCATED_GOOGLE_AUTH_CODE)
                } else if (it.contains(""""token":"***"""")) {
                    channel.trySend(Operation.OBFUSCATED_JWT)
                } else if (
                    it.contains(""""arguments":[***]""") ||
                    it.contains("BODY START\n***\nBODY END")
                ) {
                    channel.trySend(Operation.OBFUSCATED_CUSTOM_FUNCTION)
                } else if (it.contains(""""password":"$password"""")) {
                    fail(
                        """Password was not obfuscated: $message""".trimMargin()
                    )
                } else if (it.contains(""""(("access_token"):(".+?")),(("refresh_token"):(".+?"))""".toRegex())) {
                    fail(
                        """Access and refresh tokens were not obfuscated: $message""".trimMargin()
                    )
                } else if (it.contains(""""(("key"):(".+?"))""".toRegex())) {
                    fail(
                        """API key was not obfuscated: $message""".trimMargin()
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
        app = TestApp(
            appName = "obfuscator",
            logLevel = LogLevel.DEBUG,
            builder = {
                it.logObfuscator(HttpLogObfuscator.create())
            },
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
    fun emailPassword_registerAndLogin() = runBlocking {
        createUserAndLoginAssertions()
    }

    @Test
    fun apiKey_createAndLogin() = runBlocking {
        createUserAndLoginAssertions()

        async {
            val key = user.apiKeyAuth.create("foo")
            app.login(Credentials.apiKey(key.value!!))
        }

        // Create API KEY - response (obfuscate API key)
        assertEquals(Operation.OBFUSCATED_API_KEY, channel.receive())
        // Login API KEY - request (obfuscate API key)
        assertEquals(Operation.OBFUSCATED_API_KEY, channel.receive())
        // Login API KEY - response (obfuscate access and refresh tokens)
        assertEquals(Operation.OBFUSCATED_ACCESS_AND_REFRESH_TOKENS, channel.receive())
    }

    @Test
    fun apple_login() = runBlocking {
        createUserAndLoginAssertions()

        async {
            // Testing this requires a valid token so let's just test we obfuscate the request
            assertFails {
                app.login(Credentials.apple("apple"))
            }
        }
        // Login Apple - request (obfuscate token)
        assertEquals(Operation.OBFUSCATED_APPLE_OR_GOOGLE_ID_TOKEN, channel.receive())
    }

    @Test
    fun facebook_login() = runBlocking {
        createUserAndLoginAssertions()

        async {
            // Testing this requires a valid token so let's just test we obfuscate the request
            assertFails {
                app.login(Credentials.facebook("facebook"))
            }
        }
        // Login Facebook - request (obfuscate token)
        assertEquals(Operation.OBFUSCATED_FACEBOOK, channel.receive())
    }

    @Test
    fun googleAuthToken_login() = runBlocking {
        createUserAndLoginAssertions()

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
        assertEquals(Operation.OBFUSCATED_GOOGLE_AUTH_CODE, channel.receive())
        // Login Google ID token - request (obfuscate token)
        assertEquals(Operation.OBFUSCATED_APPLE_OR_GOOGLE_ID_TOKEN, channel.receive())
    }

    @Test
    fun jwtToken_login() = runBlocking {
        createUserAndLoginAssertions()

        async {
            // Testing this requires a valid token so let's just test we obfuscate the request
            assertFails {
                app.login(Credentials.jwt("jwt"))
            }
        }
        // Login JWT - request (obfuscate token)
        assertEquals(Operation.OBFUSCATED_JWT, channel.receive())
    }

    @Test
    fun customFunction() = runBlocking {
        createUserAndLoginAssertions()

        async {
            with(user.functions) {
                call<Double>(TestAppInitializer.FIRST_ARG_FUNCTION.name, 42.0)
                call<Double>(TestAppInitializer.SUM_FUNCTION.name, 42.0, 1.0)
                call<BsonNull>(TestAppInitializer.NULL_FUNCTION.name)
            }
        }
        // 1st custom function call - request (obfuscate arguments)
        assertEquals(Operation.OBFUSCATED_CUSTOM_FUNCTION, channel.receive())
        // 1st custom function call - response (obfuscate result)
        assertEquals(Operation.OBFUSCATED_CUSTOM_FUNCTION, channel.receive())

        // 2nd custom function call - request (obfuscate arguments)
        assertEquals(Operation.OBFUSCATED_CUSTOM_FUNCTION, channel.receive())
        // 2nd custom function call - response (obfuscate result)
        assertEquals(Operation.OBFUSCATED_CUSTOM_FUNCTION, channel.receive())

        // 3rd custom function call - request (obfuscate arguments)
        assertEquals(Operation.OBFUSCATED_CUSTOM_FUNCTION, channel.receive())
        // 3rd custom function call - response (obfuscate result)
        assertEquals(Operation.OBFUSCATED_CUSTOM_FUNCTION, channel.receive())
    }

    private suspend fun createUserAndLoginAssertions() {
        coroutineScope {
            val deferred = async { user = app.createUserAndLogin() }
            assertEquals(Operation.OBFUSCATED_PASSWORD, channel.receive())
            assertEquals(Operation.OBFUSCATED_PASSWORD, channel.receive())
            assertEquals(Operation.OBFUSCATED_ACCESS_AND_REFRESH_TOKENS, channel.receive())
            deferred.cancel()
        }
    }
}
