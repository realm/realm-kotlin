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
@file:Suppress("invisible_member", "invisible_reference")
@file:OptIn(ExperimentalRealmSerializerApi::class)

package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.annotations.ExperimentalRealmSerializerApi
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.AuthenticationProvider
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.GoogleAuthType
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.auth.ApiKey
import io.realm.kotlin.mongodb.exceptions.AppException
import io.realm.kotlin.mongodb.exceptions.AuthException
import io.realm.kotlin.mongodb.internal.AppImpl
import io.realm.kotlin.mongodb.internal.CredentialsImpl
import io.realm.kotlin.mongodb.internal.CustomEJsonCredentialsImpl
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import kotlinx.serialization.Serializable
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@Serializable
data class CustomCredentialsPayload(
    val id: Int,
    val mail: String
)

@Suppress("ForbiddenComment")
class CredentialsTests {

    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        app = TestApp(this::class.simpleName)
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.asTestApp.close()
        }
    }

    @Test
    fun allCredentials() {
        AuthenticationProvider.entries.flatMap {
            when (it) {
                AuthenticationProvider.ANONYMOUS -> listOf(it to anonymous())
                AuthenticationProvider.EMAIL_PASSWORD -> listOf(it to emailPassword())
                AuthenticationProvider.API_KEY -> listOf(it to apiKey())
                AuthenticationProvider.APPLE -> listOf(it to apple())
                AuthenticationProvider.FACEBOOK -> listOf(it to facebook())
                AuthenticationProvider.GOOGLE -> listOf() // Ignore, see below
                AuthenticationProvider.JWT -> listOf(it to jwt())
                AuthenticationProvider.CUSTOM_FUNCTION -> listOf(
                    it to customFunction(),
                    it to customFunctionExperimental(),
                    it to customFunctionExperimentalWithSerializer(),
                )
            }
        }.forEach { (authenticationProvider, credentials) ->
            assertEquals(authenticationProvider, credentials.authenticationProvider)
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
        for (value in AuthenticationProvider.entries) {
            assertFailsWith<IllegalArgumentException>("$value failed") { // No arguments should be allow
                when (value) {
                    AuthenticationProvider.ANONYMOUS -> throw IllegalArgumentException("Do nothing, no arguments")
                    AuthenticationProvider.API_KEY -> Credentials.apiKey("")
                    AuthenticationProvider.APPLE -> Credentials.apple("")
                    AuthenticationProvider.EMAIL_PASSWORD -> throw IllegalArgumentException("Test below as a special case")
                    AuthenticationProvider.FACEBOOK -> Credentials.facebook("")
                    AuthenticationProvider.GOOGLE -> throw IllegalArgumentException("Test below as a special case")
                    AuthenticationProvider.JWT -> Credentials.jwt("")
                    AuthenticationProvider.CUSTOM_FUNCTION -> throw IllegalArgumentException("No check required")
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
        val credsImpl = creds as CredentialsImpl
        assertTrue(credsImpl.asJson().contains("anon-user")) // Treat the JSON as an opaque value.
        return creds
    }

    @Suppress("invisible_reference", "invisible_member")
    private fun apiKey(): Credentials {
        val creds: Credentials = Credentials.apiKey("token")
        val credsImpl = creds as CredentialsImpl
        assertTrue(credsImpl.asJson().contains("token")) // Treat the JSON as an opaque value.
        return creds
    }

    private fun apple(): Credentials {
        val creds = Credentials.apple("apple-token")
        assertJsonContains(creds, "apple-token")
        return creds
    }

    private fun customFunction(): Credentials {
        val mail = TestHelper.randomEmail()
        val id = 700
        val credentials = Credentials.customFunction(
            payload = mapOf("mail" to mail, "id" to id)
        )

        assertEquals(AuthenticationProvider.CUSTOM_FUNCTION, credentials.authenticationProvider)
        assertJsonContains(credentials, mail)
        assertJsonContains(credentials, id.toString())
        return credentials
    }

    private fun customFunctionExperimental(): Credentials {
        val mail = TestHelper.randomEmail()
        val id = 700

        val credentials = Credentials.customFunction(
            payload = CustomCredentialsPayload(
                id = id,
                mail = mail,
            )
        )

        assertEquals(AuthenticationProvider.CUSTOM_FUNCTION, credentials.authenticationProvider)
        assertJsonContains(credentials, mail)
        assertJsonContains(credentials, id.toString())
        return credentials
    }

    private fun customFunctionExperimentalWithSerializer(): Credentials {
        val mail = TestHelper.randomEmail()
        val id = 700

        val credentials = Credentials.customFunction(
            payload = CustomCredentialsPayload(
                id = id,
                mail = mail,
            ),
            serializer = CustomCredentialsPayload.serializer()
        )

        assertEquals(AuthenticationProvider.CUSTOM_FUNCTION, credentials.authenticationProvider)
        assertJsonContains(credentials, mail)
        assertJsonContains(credentials, id.toString())
        return credentials
    }

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
    private fun assertJsonContains(creds: Credentials, subString: String) {
        val jsonEncodedCredentials = when (creds) {
            is CredentialsImpl -> {
                creds.asJson()
            }
            is CustomEJsonCredentialsImpl -> {
                creds.asJson(app.app as AppImpl)
            }
            else -> error("Invalid crendentials type ${creds::class.simpleName}")
        }

        // Treat the JSON as a largely opaque value.
        assertTrue(
            jsonEncodedCredentials.contains(subString),
            "[$jsonEncodedCredentials] does not contain [$subString]"
        )
    }

    @Test
    fun anonymousLogin() {
        runBlocking {
            val firstUser = app.login(Credentials.anonymous())
            assertNotNull(firstUser)
            val reusedUser = app.login(Credentials.anonymous())
            assertNotNull(reusedUser)
            assertEquals(firstUser, reusedUser)

            val newAnonymousUser1 = app.login(Credentials.anonymous(false))
            assertNotNull(newAnonymousUser1)
            assertNotEquals(firstUser, newAnonymousUser1)

            val newAnonymousUser2 = app.login(Credentials.anonymous(false))
            assertNotNull(newAnonymousUser2)
            assertNotEquals(newAnonymousUser1, newAnonymousUser2)
        }
    }

    @Test
    fun loginUsingCredentials() {
        runBlocking {
            AuthenticationProvider.entries.forEach { provider ->
                when (provider) {
                    AuthenticationProvider.ANONYMOUS -> {
                        val reusableUser = app.login(Credentials.anonymous())
                        assertNotNull(reusableUser)
                        val nonReusableUser = app.login(Credentials.anonymous(false))
                        assertNotNull(nonReusableUser)
                        assertNotEquals(reusableUser, nonReusableUser)
                    }
                    AuthenticationProvider.API_KEY -> {
                        // Log in, create an API key, log out, log in with the key, compare users
                        val user: User =
                            app.createUserAndLogIn(TestHelper.randomEmail(), "password1234")
                        val key: ApiKey = user.apiKeyAuth.create("my-key")
                        user.logOut()
                        val apiKeyUser = app.login(Credentials.apiKey(key.value!!))
                        assertEquals(user.id, apiKeyUser.id)
                    }
                    AuthenticationProvider.CUSTOM_FUNCTION -> {
                        val credentials = Credentials.customFunction(
                            payload = mapOf("mail" to TestHelper.randomEmail(), "id" to 700)
                        )

                        // We are not testing the authentication function itself, but rather that the
                        // credentials work
                        val functionUser = app.login(credentials)
                        assertNotNull(functionUser)

                        // Test customFunction with kserializer
                        setOf(
                            Credentials.customFunction(
                                payload = CustomCredentialsPayload(
                                    mail = TestHelper.randomEmail(),
                                    id = 700
                                )
                            ),
                            Credentials.customFunction(
                                payload = CustomCredentialsPayload(
                                    mail = TestHelper.randomEmail(),
                                    id = 700
                                ),
                                serializer = CustomCredentialsPayload.serializer()
                            )
                        ).forEach { credentials: Credentials ->
                            // We are not testing the authentication function itself, but rather that the
                            // credentials work
                            val functionUserExperimental = app.login(credentials)
                            assertNotNull(functionUserExperimental)
                        }
                    }
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

    @Test
    fun customFunction_authExceptionThrownOnError() {
        val credentials = Credentials.customFunction(
            payload = mapOf("mail" to TestHelper.randomEmail(), "id" to 0)
        )

        assertFailsWithMessage<AuthException>("Authentication failed") {
            runBlocking {
                app.login(credentials)
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
            assertTrue(error.message!!.contains("unauthorized"), error.message)
        }
    }
}
