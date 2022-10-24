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

package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.AuthenticationProvider
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.GoogleAuthType
import io.realm.kotlin.mongodb.exceptions.AppException
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
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
            val credentials: Credentials = when (value) {
                AuthenticationProvider.ANONYMOUS -> anonymous()
                AuthenticationProvider.EMAIL_PASSWORD -> emailPassword()
                AuthenticationProvider.API_KEY -> apiKey()
                AuthenticationProvider.APPLE -> apple()
                AuthenticationProvider.FACEBOOK -> facebook()
                AuthenticationProvider.GOOGLE -> continue // Ignore, see below
                AuthenticationProvider.JWT -> jwt()
            }
            assertEquals(value, credentials.authenticationProvider)
        }

        // Special case for Anonymous having 'reuseExisting'
        val nonReusableAnonymous = anonymous(false)
        assertEquals(AuthenticationProvider.ANONYMOUS, nonReusableAnonymous.authenticationProvider)

        // Special case for Google Auth having two types
        val googleIdToken = google_idToken()
        assertEquals(AuthenticationProvider.GOOGLE, googleIdToken.authenticationProvider)
        google_authCode()
    }

    @Test
    fun allCredentials_emptyInputThrows() {
        for (value in AuthenticationProvider.values()) {
            assertFailsWith<IllegalArgumentException>("$value failed") { // No arguments should be allow
                when (value) {
                    AuthenticationProvider.ANONYMOUS -> throw IllegalArgumentException("Do nothing, no arguments")
                    AuthenticationProvider.API_KEY -> Credentials.apiKey("")
                    AuthenticationProvider.APPLE -> Credentials.apple("")
                    AuthenticationProvider.EMAIL_PASSWORD -> throw IllegalArgumentException("Test below as a special case")
                    AuthenticationProvider.FACEBOOK -> Credentials.facebook("")
                    AuthenticationProvider.GOOGLE -> throw IllegalArgumentException("Test below as a special case")
                    AuthenticationProvider.JWT -> Credentials.jwt("")
                }
            }
        }

        // Test Email/Password as a special case, due to it having two arguments
        assertFailsWith<IllegalArgumentException> { Credentials.emailPassword("", "password") }
        assertFailsWith<IllegalArgumentException> { Credentials.emailPassword("foo@bar.com", "") }

        // Test Google as a special case as two types of Google login exists
        assertFailsWith<IllegalArgumentException> {
            Credentials.google("", GoogleAuthType.AUTH_CODE)
        }
        assertFailsWith<IllegalArgumentException> {
            Credentials.google("", GoogleAuthType.ID_TOKEN)
        }
    }

    @Suppress("invisible_reference", "invisible_member")
    private fun anonymous(reuseExisting: Boolean = true): Credentials {
        val creds: Credentials = Credentials.anonymous(reuseExisting)
        val credsImpl = creds as io.realm.kotlin.mongodb.internal.CredentialsImpl
        assertTrue(credsImpl.asJson().contains("anon-user")) // Treat the JSON as an opaque value.
        return creds
    }

    @Suppress("invisible_reference", "invisible_member")
    private fun apiKey(): Credentials {
        val creds: Credentials = Credentials.apiKey("token")
        val credsImpl = creds as io.realm.kotlin.mongodb.internal.CredentialsImpl
        assertTrue(credsImpl.asJson().contains("token")) // Treat the JSON as an opaque value.
        return creds
    }

    private fun apple(): Credentials {
        val creds = Credentials.apple("apple-token")
        assertJsonContains(creds, "apple-token")
        return creds
    }

    // TODO See https://github.com/realm/realm-kotlin/issues/742
    // fun customFunction(): Credentials {
    //     val mail = TestHelper.getRandomEmail()
    //     val id = 666 + TestHelper.getRandomId()
    //     val creds = mapOf(
    //         "mail" to mail,
    //         "id" to id
    //     ).let { Credentials.customFunction(Document(it)) }
    //     assertEquals(Credentials.Provider.CUSTOM_FUNCTION, creds.identityProvider)
    //     assertTrue(creds.asJson().contains(mail))
    //     assertTrue(creds.asJson().contains(id.toString()))
    //     return creds
    // }

    private fun emailPassword(): Credentials {
        val creds = Credentials.emailPassword("foo@bar.com", "secret")
        assertJsonContains(creds, "foo@bar.com")
        assertJsonContains(creds, "secret")
        return creds
    }

    private fun facebook(): Credentials {
        val creds = Credentials.facebook("fb-token")
        assertEquals(AuthenticationProvider.FACEBOOK, creds.authenticationProvider)
        assertJsonContains(creds, "fb-token")
        return creds
    }

    private fun google_authCode() {
        val creds = Credentials.google("google-token", GoogleAuthType.AUTH_CODE)
        assertEquals(AuthenticationProvider.GOOGLE, creds.authenticationProvider)
        assertJsonContains(creds, "google-token")
        assertJsonContains(creds, "authCode")
    }

    @Suppress("invisible_reference", "invisible_member")
    private fun google_idToken(): Credentials {
        val creds = Credentials.google("google-token", GoogleAuthType.ID_TOKEN)
        assertEquals(AuthenticationProvider.GOOGLE, creds.authenticationProvider)
        assertJsonContains(creds, "google-token")
        assertJsonContains(creds, "id_token")
        return creds
    }

    @Suppress("invisible_reference", "invisible_member")
    private fun jwt(): Credentials {
        val creds = Credentials.jwt("jwt-token")
        assertEquals(AuthenticationProvider.JWT, creds.authenticationProvider)
        assertJsonContains(creds, "jwt-token")
        return creds
    }

    // Since integration tests of Credentials are very hard to setup, we instead just fake it
    // by checking that the JSON payload we send to the server seems to be correct. If that is
    // the case, we assume the server does the right thing (and has tests for it).
    @Suppress("invisible_reference", "invisible_member")
    private fun assertJsonContains(creds: Credentials, subString: String) {
        val credsImpl = creds as io.realm.kotlin.mongodb.internal.CredentialsImpl

        // Treat the JSON as a largely opaque value.
        assertTrue(credsImpl.asJson().contains(subString))
    }

    @Test
    fun anonymousLogin() {
        app = TestApp()
        runBlocking {
            val firstUser = app.login(Credentials.anonymous())
            assertNotNull(firstUser)
            val reusedUser = app.login(Credentials.anonymous())
            assertNotNull(reusedUser)
            assertEquals(firstUser.identity, reusedUser.identity)

            val newAnonymousUser1 = app.login(Credentials.anonymous(false))
            assertNotNull(newAnonymousUser1)
            assertNotEquals(firstUser.identity, newAnonymousUser1.identity)

            val newAnonymousUser2 = app.login(Credentials.anonymous(false))
            assertNotNull(newAnonymousUser2)
            assertNotEquals(newAnonymousUser1.identity, newAnonymousUser2.identity)
        }
    }

    @Test
    fun loginUsingCredentials() {
        app = TestApp()
        runBlocking {
            AuthenticationProvider.values().forEach { provider ->
                when (provider) {
                    AuthenticationProvider.ANONYMOUS -> {
                        val reusableUser = app.login(Credentials.anonymous())
                        assertNotNull(reusableUser)
                        val nonReusableUser = app.login(Credentials.anonymous(false))
                        assertNotNull(nonReusableUser)
                        assertNotEquals(reusableUser.identity, nonReusableUser.identity)
                    }
                    AuthenticationProvider.API_KEY -> { /* Ignore, see https://github.com/realm/realm-kotlin/issues/432 */ }
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
                    AuthenticationProvider.FACEBOOK ->
                        expectInvalidSession(app, Credentials.facebook("facebook-token"))
                    AuthenticationProvider.APPLE ->
                        expectInvalidSession(app, Credentials.apple("apple-token"))
                    AuthenticationProvider.GOOGLE -> {
                        expectInvalidSession(
                            app,
                            Credentials.google("google-token", GoogleAuthType.AUTH_CODE)
                        )
                        expectInvalidSession(
                            app,
                            Credentials.google("google-token", GoogleAuthType.ID_TOKEN)
                        )
                    }
                    AuthenticationProvider.JWT ->
                        expectInvalidSession(app, Credentials.jwt("jwt-token"))
                    else -> error("Untested provider: $provider")
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
            assertTrue(error.message!!.contains("authentication via"), error.message)
        }
    }
}
