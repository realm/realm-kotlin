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
import io.realm.mongodb.Credentials
import io.realm.mongodb.User
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.asTestApp
import io.realm.test.util.TestHelper
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// val CUSTOM_USER_DATA_FIELD = "custom_field"
// val CUSTOM_USER_DATA_VALUE = "custom_data"

class UserTests {

    private lateinit var app: App

    @BeforeTest
    fun setUp() {
        app = TestApp()
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.asTestApp.close()
        }
    }

    @Test
    fun getApp() = runBlocking {
        val anonUser = app.login(Credentials.anonymous())
        assertEquals(anonUser.app, app.asTestApp.app)
    }

    @Test
    fun getState_anonymousUser() = runBlocking {
        val anonUser = app.login(Credentials.anonymous())
        assertEquals(User.State.LOGGED_IN, anonUser.state)
        anonUser.logOut()
        assertEquals(User.State.REMOVED, anonUser.state)
    }

    @Test
    fun getState_emailUser() = runBlocking {
        val emailUser = createUserAndLogin()
        assertEquals(User.State.LOGGED_IN, emailUser.state)
        emailUser.logOut()
        assertEquals(User.State.LOGGED_OUT, emailUser.state)
        // TODO wait for EmailPasswordAuth
//        app.removeUser(emailUser)
//        assertEquals(User.State.REMOVED, emailUser.state)
    }

    @Test
    fun logOut() = runBlocking {
        val anonUser = app.login(Credentials.anonymous())

        // Anonymous users are removed upon log out
        assertEquals(anonUser, app.currentUser())
        assertEquals(User.State.LOGGED_IN, anonUser.state)
        anonUser.logOut()
        assertEquals(User.State.REMOVED, anonUser.state)
        assertNull(app.currentUser())

        // Users registered with Email/Password will register as Logged Out
        val user2 = createUserAndLogin()
        val current: User = app.currentUser()!!
        assertEquals(user2, current)
        user2.logOut()
        assertEquals(User.State.LOGGED_OUT, user2.state)
        // Same effect on all instances
        assertEquals(User.State.LOGGED_OUT, current.state)
        // And no current user anymore
        assertNull(app.currentUser())
    }

    @Test
    fun logOutUserInstanceImpactsCurrentUser() = runBlocking {
        val anonUser = app.login(Credentials.anonymous())

        val currentUser = app.currentUser()!!
        assertEquals(User.State.LOGGED_IN, currentUser.state)
        assertEquals(User.State.LOGGED_IN, anonUser.state)
        assertEquals(currentUser, anonUser)

        anonUser.logOut()

        assertNotEquals(User.State.LOGGED_OUT, currentUser.state)
        assertNotEquals(User.State.LOGGED_OUT, anonUser.state)
        assertNull(app.currentUser())
    }

    @Test
    fun logOutCurrentUserImpactsOtherInstances() = runBlocking {
        val anonUser = app.login(Credentials.anonymous())

        val currentUser = app.currentUser()!!
        assertEquals(User.State.LOGGED_IN, currentUser.state)
        assertEquals(User.State.LOGGED_IN, anonUser.state)
        assertEquals(currentUser, anonUser)

        currentUser.logOut()

        assertNotEquals(User.State.LOGGED_OUT, currentUser.state)
        assertNotEquals(User.State.LOGGED_OUT, anonUser.state)
        assertNull(app.currentUser())
    }

//    @Test
//    fun repeatedLogInAndOut() = runBlocking {
//        val initialUser = createUserAndLogin()
//        assertEquals(User.State.LOGGED_IN, initialUser.state)
//        initialUser.logOut()
//        assertEquals(User.State.LOGGED_OUT, initialUser.state)
//
//        repeat(3) {
//            val user = app.login(Credentials.emailPassword(initialUser.profile.email, password))
//            assertEquals(User.State.LOGGED_IN, user.state)
//            user.logOut()
//            assertEquals(User.State.LOGGED_OUT, user.state)
//        }
//    }
//
//    @Test
//    fun linkUser_emailPassword() {
//        assertEquals(1, anonUser.identities.size)
//
//        val email = TestHelper.getRandomEmail()
//        val password = "123456"
//        app.emailPassword.registerUser(email, password) // TODO Test what happens if auto-confirm is enabled
//        var linkedUser: User = anonUser.linkCredentials(Credentials.emailPassword(email, password))
//
//        assertTrue(anonUser === linkedUser)
//        assertEquals(2, linkedUser.identities.size)
//        assertEquals(Credentials.Provider.EMAIL_PASSWORD, linkedUser.identities[1].provider)
//
//        // Validate that we cannot link a second set of credentials
//        val otherEmail = TestHelper.getRandomEmail()
//        val otherPassword = "123456"
//        app.emailPassword.registerUser(otherEmail, otherPassword)
//
//        val credentials = Credentials.emailPassword(otherEmail, otherPassword)
//
//        assertFails {
//            linkedUser = anonUser.linkCredentials(credentials)
//        }
//    }
//
//    @Test
//    fun linkUser_userApiKey() {
//        // Generate API key
//        val user: User = app.registerUserAndLogin(TestHelper.getRandomEmail(), "123456")
//        val apiKey: ApiKey = user.apiKeys.create("my-key");
//        user.logOut()
//
//        anonUser = app.login(Credentials.anonymous())
//
//        assertEquals(1, anonUser.identities.size)
//
//        // Linking with another user's API key is not allowed and must raise an AppException
//        val exception = assertFailsWith<AppException> {
//            anonUser.linkCredentials(Credentials.apiKey(apiKey.value))
//        }
//
//        assertEquals("invalid user link request", exception.errorMessage);
//        assertEquals(ErrorCode.Category.FATAL, exception.errorCode.category);
//        assertEquals("realm::app::ServiceError", exception.errorCode.type);
//        assertEquals(6, exception.errorCode.intValue());
//    }
//
//    @Test
//    fun linkUser_customFunction() {
//        assertEquals(1, anonUser.identities.size)
//
//        val document = Document(mapOf(
//            "mail" to TestHelper.getRandomEmail(),
//            "id" to TestHelper.getRandomId() + 666
//        ))
//
//        val credentials = Credentials.customFunction(document)
//
//        val linkedUser = anonUser.linkCredentials(credentials)
//
//        assertTrue(anonUser === linkedUser)
//        assertEquals(2, linkedUser.identities.size)
//        assertEquals(Credentials.Provider.CUSTOM_FUNCTION, linkedUser.identities[1].provider)
//    }
//
//    @Test
//    fun linkUser_existingCredentialsThrows() {
//        val email = TestHelper.getRandomEmail()
//        val password = "123456"
//        val emailUser: User = app.registerUserAndLogin(email, password)
//        try {
//            anonUser.linkCredentials(Credentials.emailPassword(email, password))
//            fail()
//        } catch (ex: AppException) {
//            assertEquals(ErrorCode.INVALID_SESSION, ex.errorCode)
//        }
//    }
//
//    @Test
//    fun linkUser_invalidArgsThrows() {
//        try {
//            anonUser.linkCredentials(TestHelper.getNull())
//            fail()
//        } catch (ignore: IllegalArgumentException) {
//        }
//    }
//
//    @Test
//    fun linkUserAsync() = looperThread.runBlocking {
//        assertEquals(1, anonUser.identities.size)
//        val email = TestHelper.getRandomEmail()
//        val password = "123456"
//        app.emailPassword.registerUser(email, password) // TODO Test what happens if auto-confirm is enabled
//
//        anonUser.linkCredentialsAsync(Credentials.emailPassword(email, password)) { result ->
//            val linkedUser: User = result.orThrow
//            assertTrue(anonUser === linkedUser)
//            assertEquals(2, linkedUser.identities.size)
//            assertEquals(Credentials.Provider.EMAIL_PASSWORD, linkedUser.identities[1].provider)
//            looperThread.testComplete()
//        }
//    }
//
//    @Test
//    fun linkUserAsync_throwsOnNonLooperThread() {
//        try {
//            anonUser.linkCredentialsAsync(Credentials.emailPassword(TestHelper.getRandomEmail(), "123456")) { fail() }
//            fail()
//        } catch (ignore: java.lang.IllegalStateException) {
//        }
//    }
//
//    @Test
//    fun removeUser() {
//        anonUser.logOut() // Remove user used by other tests
//
//        // Removing logged in user
//        val user1 = app.registerUserAndLogin(TestHelper.getRandomEmail(), "123456")
//        assertEquals(user1, app.currentUser())
//        assertEquals(1, app.allUsers().size)
//        app.removeUser(user1)
//        assertEquals(User.State.REMOVED, user1.state)
//        assertNull(app.currentUser())
//        assertEquals(0, app.allUsers().size)
//
//        // Remove logged out user
//        val user2 = app.registerUserAndLogin(TestHelper.getRandomEmail(), "123456")
//        user2.logOut()
//        assertNull(app.currentUser())
//        assertEquals(1, app.allUsers().size)
//        app.removeUser(user2)
//        assertEquals(User.State.REMOVED, user2.state)
//        assertEquals(0, app.allUsers().size)
//    }
//
//    @Test
//    fun getApiKeyAuthProvider() {
//        val user: User = app.registerUserAndLogin(TestHelper.getRandomEmail(), "123456")
//        val provider1: ApiKeyAuth = user.apiKeys
//        assertEquals(user, provider1.user)
//
//        user.logOut()
//
//        try {
//            user.apiKeys
//            fail()
//        } catch (ex: IllegalStateException) {
//        }
//    }
//
//    @Test
//    fun revokedRefreshTokenIsNotSameAfterLogin() = looperThread.runBlocking {
//        val password = "password"
//        val user = app.registerUserAndLogin(TestHelper.getRandomEmail(), password)
//        val refreshToken = user.refreshToken
//
//        app.addAuthenticationListener(object : AuthenticationListener {
//            override fun loggedIn(user: User) {}
//
//            override fun loggedOut(loggerOutUser: User) {
//                app.loginAsync(Credentials.emailPassword(loggerOutUser.profile.email, password)) {
//                    val loggedInUser = it.orThrow
//                    assertTrue(loggerOutUser !== loggedInUser)
//                    assertNotEquals(refreshToken, loggedInUser.refreshToken)
//                    looperThread.testComplete()
//                }
//            }
//        })
//        user.logOut()
//    }
//

    @Test
    fun isLoggedIn() = runBlocking {
        val anonUser = app.login(Credentials.anonymous())

        val user = createUserAndLogin()

        assertTrue(anonUser.loggedIn)
        assertTrue(user.loggedIn)

        anonUser.logOut()
        assertFalse(anonUser.loggedIn)
        assertTrue(user.loggedIn)

        user.logOut()
        assertFalse(user.loggedIn)
    }

    @Test
    fun equals() = runBlocking {
        val (email, password) = TestHelper.randomEmail() to "123456"
        val user = createUserAndLogin(email, password)
        assertEquals(user, user)
        user.logOut()

        // Verify that it is not same object but uses underlying User equality on identity
        val sameUserNewLogin = app.login(Credentials.emailPassword(email, password))
        assertFalse(user === sameUserNewLogin)
        assertEquals(user, sameUserNewLogin)

        val differentUser = createUserAndLogin(TestHelper.randomEmail(), password)
        assertNotEquals(user, differentUser)
    }

    @Test
    fun hashCode_user() = runBlocking {
        val (email, password) = TestHelper.randomEmail() to "123456"
        val user = createUserAndLogin(email, password)
        user.logOut()

        val sameUserNewLogin = app.login(Credentials.emailPassword(email, password))
        // Verify that two equal users also returns same hashCode
        assertFalse(user === sameUserNewLogin)
        assertEquals(user.hashCode(), sameUserNewLogin.hashCode())
    }

//    @Test
//    fun customData_initiallyEmpty() {
//        val user = app.registerUserAndLogin(TestHelper.getRandomEmail(), "123456")
//        // Newly registered users do not have any custom data with current test server setup
//        assertEquals(Document(), user.customData)
//    }
//
//    @Test
//    fun customData_refresh() {
//        val user = app.registerUserAndLogin(TestHelper.getRandomEmail(), "123456")
//        // Newly registered users do not have any custom data with current test server setup
//        assertEquals(Document(), user.customData)
//
//        updateCustomData(user, Document(CUSTOM_USER_DATA_FIELD, CUSTOM_USER_DATA_VALUE))
//
//        val updatedCustomData = user.refreshCustomData()
//        assertEquals(CUSTOM_USER_DATA_VALUE, updatedCustomData[CUSTOM_USER_DATA_FIELD])
//        assertEquals(CUSTOM_USER_DATA_VALUE, user.customData[CUSTOM_USER_DATA_FIELD])
//    }
//
//    @Test
//    fun customData_refreshAsync() = looperThread.runBlocking {
//        val user = app.registerUserAndLogin(TestHelper.getRandomEmail(), "123456")
//        // Newly registered users do not have any custom data with current test server setup
//        assertEquals(Document(), user.customData)
//
//        updateCustomData(user, Document(CUSTOM_USER_DATA_FIELD, CUSTOM_USER_DATA_VALUE))
//
//        val updatedCustomData = user.refreshCustomData { result ->
//            val updatedCustomData = result.orThrow
//            assertEquals(CUSTOM_USER_DATA_VALUE, updatedCustomData[CUSTOM_USER_DATA_FIELD])
//            assertEquals(CUSTOM_USER_DATA_VALUE, user.customData[CUSTOM_USER_DATA_FIELD])
//            looperThread.testComplete()
//        }
//    }
//
//    @Test
//    fun customData_refreshByLogout() {
//        val password = "123456"
//        val user = app.registerUserAndLogin(TestHelper.getRandomEmail(), password)
//        // Newly registered users do not have any custom data with current test server setup
//        assertEquals(Document(), user.customData)
//
//        updateCustomData(user, Document(CUSTOM_USER_DATA_FIELD, CUSTOM_USER_DATA_VALUE))
//
//        // But will be updated when authorization token is refreshed
//        user.logOut()
//        app.login(Credentials.emailPassword(user.profile.email, password))
//        assertEquals(CUSTOM_USER_DATA_VALUE, user.customData.get(CUSTOM_USER_DATA_FIELD))
//    }
//
//    @Test
//    fun customData_refreshAsyncThrowsOnNonLooper() {
//        val password = "123456"
//        val user = app.registerUserAndLogin(TestHelper.getRandomEmail(), password)
//
//        assertFailsWith<java.lang.IllegalStateException> {
//            user.refreshCustomData { }
//        }
//    }
//
//    private fun updateCustomData(user: User, data: Document) {
//        // Name of collection and property used for storing custom user data. Must match server config.json
//        val COLLECTION_NAME = "custom_user_data"
//        val USER_ID_FIELD = "userid"
//
//        val client = user.getMongoClient(SERVICE_NAME)
//        client.getDatabase(DATABASE_NAME).let {
//            it.getCollection(COLLECTION_NAME).also { collection ->
//                collection.insertOne(data.append(USER_ID_FIELD, user.id)).get()
//            }
//        }
//    }

    private suspend fun createUserAndLogin(
        email: String = TestHelper.randomEmail(),
        password: String = "123456"
    ): User {
        app.asTestApp.createUser(email, password)
        return app.login(Credentials.emailPassword(email, password))
    }
}
