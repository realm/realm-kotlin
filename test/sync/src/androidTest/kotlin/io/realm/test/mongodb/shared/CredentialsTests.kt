/*
 * Copyright 2021 Realm Inc.
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

package io.realm.test.mongodb.shared

import io.realm.internal.platform.runBlocking
import io.realm.mongodb.App
import io.realm.mongodb.AppException
import io.realm.mongodb.AuthenticationProvider
import io.realm.mongodb.Credentials
import io.realm.mongodb.GoogleAuthType
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.asTestApp
import io.realm.test.mongodb.createUserAndLogIn
import io.realm.test.util.TestHelper
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@Suppress("ForbiddenComment")
class CredentialsTests {

    private lateinit var app: App

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.asTestApp.close()
        }
    }

    @Test
    fun allCredentials() {
        for (value in AuthenticationProvider.values()) {
            val credentials = when (value) {
                AuthenticationProvider.ANONYMOUS ->
                    anonymous()
                AuthenticationProvider.EMAIL_PASSWORD ->
                    emailPassword()
                else -> error("Untested credentials type: $value")
            }
            assertEquals(value, credentials.authenticationProvider)
        }
    }

    @Test
    fun allCredentials_emptyInputThrows() {
        for (value in AuthenticationProvider.values()) {
            assertFailsWith<IllegalArgumentException> { // No arguments should be allow
                when (value) {
                    AuthenticationProvider.ANONYMOUS -> { /* Do nothing, no arguments */ }
                    AuthenticationProvider.API_KEY -> Credentials.apiKey("")
                    AuthenticationProvider.APPLE -> Credentials.apple("")
                    AuthenticationProvider.EMAIL_PASSWORD -> { /* Test below as a special case */ }
                    AuthenticationProvider.FACEBOOK -> Credentials.facebook("")
                    AuthenticationProvider.GOOGLE -> { /* Tested below as a special case */ }
                    AuthenticationProvider.JWT -> Credentials.jwt("")
                }
            }
        }

        // Test Email/Password as a special case, due to it having two arguments
        assertFailsWith<IllegalArgumentException> { Credentials.emailPassword("", "password") }
        assertFailsWith<IllegalArgumentException> { Credentials.emailPassword("foo@bar.com", "") }

        // Test Google as a special case as two types of Google login exists
        assertFailsWith<IllegalArgumentException> { Credentials.google("", GoogleAuthType.AUTH_CODE) }
        assertFailsWith<IllegalArgumentException> { Credentials.google("", GoogleAuthType.ID_TOKEN) }
    }

    fun anonymous(): Credentials {
        val creds: Credentials = Credentials.anonymous()
        // assertTrue(creds.asJson().contains("anon-user")) // Treat the JSON as an opaque value.
        return creds
    }

    @Test
    fun apiKey() {
        val creds: Credentials = Credentials.apiKey("token")
        assertEquals(AuthenticationProvider.API_KEY, creds.authenticationProvider)
        // assertTrue(creds.asJson().contains("token")) // Treat the JSON as an opaque value.
    }

    @Test
    fun apple() {
        val creds = Credentials.apple("apple-token")
        assertEquals(AuthenticationProvider.APPLE, creds.authenticationProvider)
        // assertTrue(creds.asJson().contains("apple-token")) // Treat the JSON as a largely opaque value.
    }

    // TODO See https://github.com/realm/realm-kotlin/issues/742
    // @Test
    // fun customFunction() {
    //     val mail = TestHelper.getRandomEmail()
    //     val id = 666 + TestHelper.getRandomId()
    //     val creds = mapOf(
    //         "mail" to mail,
    //         "id" to id
    //     ).let { Credentials.customFunction(Document(it)) }
    //     assertEquals(Credentials.Provider.CUSTOM_FUNCTION, creds.identityProvider)
    //     assertTrue(creds.asJson().contains(mail))
    //     assertTrue(creds.asJson().contains(id.toString()))
    // }

    fun emailPassword(): Credentials {
        val creds = Credentials.emailPassword("foo@bar.com", "secret")
        // TODO Do we need exposure of the json representation? If so, then we need exposure of it
        //  in the C-API as well
        // Treat the JSON as a largely opaque value.
        // assertTrue(creds.asJson().contains("foo@bar.com"))
        // assertTrue(creds.asJson().contains("secret"))
        return creds
    }

    @Test
    fun facebook() {
        val creds = Credentials.facebook("fb-token")
        assertEquals(AuthenticationProvider.FACEBOOK, creds.authenticationProvider)
        // assertTrue(creds.asJson().contains("fb-token"))
    }

    @Test
    fun google_authCode() {
        // TODO https://github.com/realm/realm-core/issues/5347
        assertFailsWith<NotImplementedError> {
            Credentials.google("google-token", GoogleAuthType.AUTH_CODE)
        }
        // val creds = Credentials.google("google-token", GoogleAuthType.AUTH_CODE)
        // assertEquals(AuthenticationProvider.GOOGLE, creds.authenticationProvider)
        // assertTrue(creds.asJson().contains("google-token"))
        // assertTrue(creds.asJson().contains("authCode"))
    }

    @Test
    fun google_idToken() {
        val creds = Credentials.google("google-token", GoogleAuthType.ID_TOKEN)
        assertEquals(AuthenticationProvider.GOOGLE, creds.authenticationProvider)
        // assertTrue(creds.asJson().contains("google-token"))
        // assertTrue(creds.asJson().contains("id_token"))
    }

    @Test
    fun jwt() {
        val creds = Credentials.jwt("jwt-token")
        assertEquals(AuthenticationProvider.JWT, creds.authenticationProvider)
        // assertTrue(creds.asJson().contains("jwt-token"))
    }

    @Test
    fun loginUsingCredentials() {
        app = TestApp()
        runBlocking {
            AuthenticationProvider.values().forEach { provider ->
                when (provider) {
                    AuthenticationProvider.ANONYMOUS -> {
                        val user = app.login(Credentials.anonymous())
                        assertNotNull(user)
                    }
// TODO Enable this when https://github.com/realm/realm-kotlin/issues/432 is complete.

//                    AuthenticationProvider.API_KEY -> {
//                        // Log in, create an API key, log out, log in with the key, compare users
//                        val user: User =
//                            app.registerUserAndLogin(TestHelper.getRandomEmail(), "123456")
//                        val key: ApiKey = user.apiKeys.create("my-key");
//                        user.logOut()
//                        val apiKeyUser = app.login(Credentials.apiKey(key.value!!))
//                        assertEquals(user.id, apiKeyUser.id)
//                    }

// TODO Enable this when https://github.com/realm/realm-kotlin/issues/741 is complete.

//                    Credentials.Provider.CUSTOM_FUNCTION -> {
//                        val customFunction = mapOf(
//                            "mail" to TestHelper.getRandomEmail(),
//                            "id" to 666 + TestHelper.getRandomId()
//                        ).let {
//                            Credentials.customFunction(Document(it))
//                        }
//
//                        // We are not testing the authentication function itself, but rather that the
//                        // credentials work
//                        val functionUser = app.login(customFunction)
//                        assertNotNull(functionUser)
//                    }
                    AuthenticationProvider.EMAIL_PASSWORD -> {
                        val (email, password) = TestHelper.randomEmail() to "password1234"
                        val user = app.createUserAndLogIn(email, password)
                        assertNotNull(user)
                    }
                    // These providers are hard to test for real since they depend on a 3rd party
                    // login service. Instead we attempt to login and verify that a proper exception
                    // is thrown. At least that should verify that correctly formatted JSON is being
                    // sent across the wire.
                    AuthenticationProvider.FACEBOOK -> {
                        expectInvalidSession(app, Credentials.facebook("facebook-token"))
                    }
                    AuthenticationProvider.APPLE -> {
                        expectInvalidSession(app, Credentials.apple("apple-token"))
                    }
                    AuthenticationProvider.GOOGLE -> {
                        expectInvalidSession(app, Credentials.google("google-token", GoogleAuthType.AUTH_CODE))
                        expectInvalidSession(app, Credentials.google("google-token", GoogleAuthType.ID_TOKEN))
                    }
                    AuthenticationProvider.JWT -> {
                        expectInvalidSession(app, Credentials.jwt("jwt-token"))
                    }
                    else -> {
                        error("Untested provider: $provider")
                    }
                }
            }
        }
    }

    private fun expectInvalidSession(app: App, credentials: Credentials) {
        try {
            runBlocking {
                app.login(credentials)
            }
            fail()
        } catch (error: AppException) {
            assertTrue(error.message!!.contains("Invalid session"), error.message)
        }
    }
}
