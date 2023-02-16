package io.realm.kotlin.test.mongodb.darwin

import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.GoogleAuthType
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.ext.call
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.util.TestAppInitializer
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.initializeDefault
import org.mongodb.kbson.BsonNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.fail

// FIXME remove this if we ever figure out what's going with this
//  https://github.com/realm/realm-kotlin/issues/1284
class HttpLogObfuscatorTests {

    private lateinit var app: TestApp
    private lateinit var user: User

    private object NoOpObfuscatorLoggerInspector : RealmLogger {

        override val level: LogLevel = LogLevel.DEBUG
        override val tag: String = "NullObfuscatorLoggerInspector"

        override fun log(
            level: LogLevel,
            throwable: Throwable?,
            message: String?,
            vararg args: Any?
        ) {
            message?.also {
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

    @BeforeTest
    fun setUp() {
        app = TestApp(
            appName = "obfuscator",
            logLevel = LogLevel.DEBUG,
            customLogger = NoOpObfuscatorLoggerInspector,
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
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun nullObfuscator() = runBlocking {
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

        Unit
    }

    @Test
    fun emailPassword_registerAndLogin() = runBlocking {
        createUserAndLoginAssertions()
    }

    @Test
    fun apiKey_createAndLogin() = runBlocking {
        createUserAndLoginAssertions()
        val key = user.apiKeyAuth.create("foo")
        app.login(Credentials.apiKey(key.value!!))
        Unit
    }

    @Test
    fun apple_login() = runBlocking {
        createUserAndLoginAssertions()

        // Testing this requires a valid token so let's just test we obfuscate the request
        assertFails {
            app.login(Credentials.apple("apple"))
        }
        Unit
    }

    @Test
    fun facebook_login() = runBlocking {
        createUserAndLoginAssertions()

        // Testing this requires a valid token so let's just test we obfuscate the request
        assertFails {
            app.login(Credentials.facebook("facebook"))
        }
        Unit
    }

    @Test
    fun googleAuthToken_login() = runBlocking {
        createUserAndLoginAssertions()

        // Testing these two requires a valid token so let's just test we obfuscate the request
        assertFails {
            app.login(Credentials.google("google-auth-code", GoogleAuthType.AUTH_CODE))
        }
        assertFails {
            app.login(Credentials.google("google-id-token", GoogleAuthType.ID_TOKEN))
        }
        Unit
    }

    @Test
    fun jwtToken_login() = runBlocking {
        createUserAndLoginAssertions()

        // Testing this requires a valid token so let's just test we obfuscate the request
        assertFails {
            app.login(Credentials.jwt("jwt"))
        }
        Unit
    }

    @Test
    fun customFunction() = runBlocking {
        createUserAndLoginAssertions()

        with(user.functions) {
            call<Double>(TestAppInitializer.FIRST_ARG_FUNCTION.name, 42.0)
            call<Double>(TestAppInitializer.SUM_FUNCTION.name, 42.0, 1.0)
            call<BsonNull>(TestAppInitializer.NULL_FUNCTION.name)
        }
        Unit
    }

    private fun createUserAndLoginAssertions() {
        // No assertions since the obfuscator is a no-op for native
        user = app.createUserAndLogin()
    }
}
