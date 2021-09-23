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
import io.realm.mongodb.AuthenticationProvider
import io.realm.mongodb.Credentials
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.asTestApp
import io.realm.test.util.TestHelper
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

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

    fun anonymous(): Credentials {
        val creds = Credentials.anonymous()
        // assertTrue(creds.asJson().contains("anon-user")) // Treat the JSON as an opaque value.
        return creds
    }

//    @Test
//    fun apiKey() {
//        val creds = Credentials.apiKey("token")
//        assertEquals(Credentials.Provider.API_KEY, creds.identityProvider)
//        assertTrue(creds.asJson().contains("token")) // Treat the JSON as an opaque value.
//    }
//
//    @Test
//    fun apiKey_invalidInput() {
//        assertFailsWith<IllegalArgumentException> { Credentials.apiKey("") }
//        assertFailsWith<IllegalArgumentException> { Credentials.apiKey(TestHelper.getNull()) }
//    }
//
//    @Test
//    fun apple() {
//        val creds = Credentials.apple("apple-token")
//        assertEquals(Credentials.Provider.APPLE, creds.identityProvider)
//        assertTrue(creds.asJson().contains("apple-token")) // Treat the JSON as a largely opaque value.
//    }
//
//    @Test
//    fun apple_invalidInput() {
//        assertFailsWith<IllegalArgumentException> { Credentials.apple("") }
//        assertFailsWith<IllegalArgumentException> { Credentials.apple(TestHelper.getNull()) }
//    }
//
//    @Test
//    fun customFunction() {
//        val mail = TestHelper.getRandomEmail()
//        val id = 666 + TestHelper.getRandomId()
//        val creds = mapOf(
//            "mail" to mail,
//            "id" to id
//        ).let { Credentials.customFunction(Document(it)) }
//        assertEquals(Credentials.Provider.CUSTOM_FUNCTION, creds.identityProvider)
//        assertTrue(creds.asJson().contains(mail))
//        assertTrue(creds.asJson().contains(id.toString()))
//    }
//
//    @Test
//    fun customFunction_invalidInput() {
//        assertFailsWith<IllegalArgumentException> { Credentials.customFunction(null) }
//    }

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
    fun emailPassword_invalidInput() {
        assertFailsWith<IllegalArgumentException> { Credentials.emailPassword("", "password") }
        assertFailsWith<IllegalArgumentException> { Credentials.emailPassword("email", "") }
        // TODO I guess we cannot workaround these being null in Kotlin, but do we need some Java tests then?
        // assertFailsWith<IllegalArgumentException> { Credentials.emailPassword(TestHelper.getNull(), "password") }
        // assertFailsWith<IllegalArgumentException> { Credentials.emailPassword("email", TestHelper.getNull()) }
    }

//    @Test
//    fun facebook() {
//        val creds = Credentials.facebook("fb-token")
//        assertEquals(Credentials.Provider.FACEBOOK, creds.identityProvider)
//        assertTrue(creds.asJson().contains("fb-token"))
//    }
//
//    @Test
//    fun facebook_invalidInput() {
//        assertFailsWith<IllegalArgumentException> { Credentials.facebook("") }
//        assertFailsWith<IllegalArgumentException> { Credentials.facebook(TestHelper.getNull()) }
//    }
//
//    @Test
//    fun google_authCode() {
//        val creds = Credentials.google("google-token", GoogleAuthType.AUTH_CODE)
//        assertEquals(Credentials.Provider.GOOGLE, creds.identityProvider)
//        assertTrue(creds.asJson().contains("google-token"))
//        assertTrue(creds.asJson().contains("authCode"))
//    }
//
//    @Test
//    fun google_idToken() {
//        val creds = Credentials.google("google-token", GoogleAuthType.ID_TOKEN)
//        assertEquals(Credentials.Provider.GOOGLE, creds.identityProvider)
//        assertTrue(creds.asJson().contains("google-token"))
//        assertTrue(creds.asJson().contains("id_token"))
//    }
//
//    @Test
//    fun google_invalidInput_authCode() {
//        assertFailsWith<IllegalArgumentException> { Credentials.google("", GoogleAuthType.AUTH_CODE) }
//        assertFailsWith<IllegalArgumentException> { Credentials.google(TestHelper.getNull(), GoogleAuthType.AUTH_CODE) }
//    }
//
//    @Test
//    fun google_invalidInput_idToken() {
//        assertFailsWith<IllegalArgumentException> { Credentials.google("", GoogleAuthType.ID_TOKEN) }
//        assertFailsWith<IllegalArgumentException> { Credentials.google(TestHelper.getNull(), GoogleAuthType.ID_TOKEN) }
//    }
//
//    @Ignore("FIXME: Awaiting ObjectStore support")
//    @Test
//    fun jwt() {
//        val creds = Credentials.jwt("jwt-token")
//        assertEquals(Credentials.Provider.JWT, creds.identityProvider)
//        assertTrue(creds.asJson().contains("jwt-token"))
//    }
//
//    @Test
//    fun jwt_invalidInput() {
//        assertFailsWith<IllegalArgumentException> { Credentials.jwt("") }
//        assertFailsWith<IllegalArgumentException> { Credentials.jwt(TestHelper.getNull()) }
//    }

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
//                    AuthenticationProvider.API_KEY -> {
//                        // Log in, create an API key, log out, log in with the key, compare users
//                        val user: User =
//                            app.registerUserAndLogin(TestHelper.getRandomEmail(), "123456")
//                        val key: ApiKey = user.apiKeys.create("my-key");
//                        user.logOut()
//                        val apiKeyUser = app.login(Credentials.apiKey(key.value!!))
//                        assertEquals(user.id, apiKeyUser.id)
//                    }
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
                        val email = TestHelper.randomEmail()
                        val password = "123456"
                        app.asTestApp.createUser(email, password)
                        val user = app.login(Credentials.emailPassword(email, password))
                        assertNotNull(user)
                    }

//                    // These providers are hard to test for real since they depend on a 3rd party
//                    // login service. Instead we attempt to login and verify that a proper exception
//                    // is thrown. At least that should verify that correctly formatted JSON is being
//                    // sent across the wire.
//                    AuthenticationProvider.FACEBOOK -> {
//                        expectErrorCode(
//                            app,
//                            ErrorCode.INVALID_SESSION,
//                            Credentials.facebook("facebook-token")
//                        )
//                    }
//                    AuthenticationProvider.APPLE -> {
//                        expectErrorCode(
//                            app,
//                            ErrorCode.INVALID_SESSION,
//                            Credentials.apple("apple-token")
//                        )
//                    }
//                    AuthenticationProvider.GOOGLE -> {
//                        expectErrorCode(
//                            app,
//                            ErrorCode.INVALID_SESSION,
//                            Credentials.google("google-token", GoogleAuthType.AUTH_CODE)
//                        )
//                        expectErrorCode(
//                            app,
//                            ErrorCode.INVALID_SESSION,
//                            Credentials.google("google-token", GoogleAuthType.ID_TOKEN)
//                        )
//                    }
//                    AuthenticationProvider.JWT -> {
//                        expectErrorCode(
//                            app,
//                            ErrorCode.INVALID_SESSION,
//                            Credentials.jwt("jwt-token")
//                        )
//                    }
//                    AuthenticationProvider.UNKNOWN -> {
//                        // Ignore
//                    }
                    else -> {
                        error("Untested provider: $provider")
                    }
                }
            }
        }
    }

//    private fun expectErrorCode(app: App, expectedCode: ErrorCode, credentials: Credentials) {
//        try {
//            app.login(credentials)
//            fail()
//        } catch (error: AppException) {
//            assertEquals(expectedCode, error.errorCode)
//        }
//    }
}
