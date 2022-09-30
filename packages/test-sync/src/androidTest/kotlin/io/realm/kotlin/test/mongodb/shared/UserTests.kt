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

import io.realm.kotlin.Realm
import io.realm.kotlin.entities.sync.SyncObjectWithAllTypes
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.AuthenticationProvider
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.CredentialsCannotBeLinkedException
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.TestHelper.randomEmail
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
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
    fun getId() = runBlocking {
        val anonUser = app.login(Credentials.anonymous())
        assertEquals(24, anonUser.id.length)
        anonUser.logOut()
        assertEquals(24, anonUser.id.length)
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
        emailUser.remove()
        assertEquals(User.State.REMOVED, emailUser.state)
    }

    @Test
    fun getIdentities() = runBlocking {
        val email = randomEmail()
        val emailUser = createUserAndLogin(email, "123456")
        assertEquals(1, emailUser.identities.size)
        emailUser.identities.first().let {
            assertEquals(24, it.id.length)
            assertEquals(AuthenticationProvider.EMAIL_PASSWORD, it.provider)
        }

        val anonUser = app.login(Credentials.anonymous())
        assertEquals(1, anonUser.identities.size)
        anonUser.identities.first().let {
            assertEquals(49, it.id.length)
            assertEquals(AuthenticationProvider.ANONYMOUS, it.provider)
        }
    }

    @Test
    fun getProviderType() = runBlocking {
        val email = randomEmail()
        val emailUser = createUserAndLogin(email, "123456")
        assertEquals(AuthenticationProvider.EMAIL_PASSWORD, emailUser.provider)
    }

    @Test
    fun logOut() = runBlocking {
        val anonUser = app.login(Credentials.anonymous())

        // Anonymous users are removed upon log out
        assertEquals(anonUser, app.currentUser)
        assertEquals(User.State.LOGGED_IN, anonUser.state)
        anonUser.logOut()
        assertEquals(User.State.REMOVED, anonUser.state)
        assertNull(app.currentUser)

        // Users registered with Email/Password will register as Logged Out
        val user2 = createUserAndLogin()
        val current: User = app.currentUser!!
        assertEquals(user2, current)
        user2.logOut()
        assertEquals(User.State.LOGGED_OUT, user2.state)
        // Same effect on all instances
        assertEquals(User.State.LOGGED_OUT, current.state)
        // And no current user anymore
        assertNull(app.currentUser)
    }

    @Test
    fun logOutUserInstanceImpactsCurrentUser() = runBlocking {
        val anonUser = app.login(Credentials.anonymous())

        val currentUser = app.currentUser!!
        assertEquals(User.State.LOGGED_IN, currentUser.state)
        assertEquals(User.State.LOGGED_IN, anonUser.state)
        assertEquals(currentUser, anonUser)

        anonUser.logOut()

        assertNotEquals(User.State.LOGGED_OUT, currentUser.state)
        assertNotEquals(User.State.LOGGED_OUT, anonUser.state)
        assertNull(app.currentUser)
    }

    @Test
    fun logOutCurrentUserImpactsOtherInstances() = runBlocking {
        val anonUser = app.login(Credentials.anonymous())

        val currentUser = app.currentUser!!
        assertEquals(User.State.LOGGED_IN, currentUser.state)
        assertEquals(User.State.LOGGED_IN, anonUser.state)
        assertEquals(currentUser, anonUser)

        currentUser.logOut()

        assertNotEquals(User.State.LOGGED_OUT, currentUser.state)
        assertNotEquals(User.State.LOGGED_OUT, anonUser.state)
        assertNull(app.currentUser)
    }

    @Test
    fun repeatedLogInAndOut() = runBlocking {
        val (email, password) = randomEmail() to "password1234"
        val initialUser = createUserAndLogin(email, password)
        assertEquals(User.State.LOGGED_IN, initialUser.state)
        initialUser.logOut()
        assertEquals(User.State.LOGGED_OUT, initialUser.state)

        repeat(3) {
            // FIXME assert with user.profile.email instead when user.profile API is ready
            val user = app.login(Credentials.emailPassword(email, password))
            assertEquals(User.State.LOGGED_IN, user.state)
            user.logOut()
            assertEquals(User.State.LOGGED_OUT, user.state)
        }
    }

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

    @Test
    fun removeUser() {
        runBlocking {
            // Removing logged in user
            val user1 = createUserAndLogin()
            assertEquals(user1, app.currentUser)
            assertEquals(1, app.allUsers().size)
            assertEquals(user1, user1.remove())
            assertEquals(User.State.REMOVED, user1.state)
            assertNull(app.currentUser)
            assertEquals(0, app.allUsers().size)

            // Remove logged out user
            val user2 = createUserAndLogin()
            user2.logOut()
            assertNull(app.currentUser)
            assertEquals(1, app.allUsers().size)
            assertEquals(user2, user2.remove())
            assertEquals(User.State.REMOVED, user2.state)
            assertEquals(0, app.allUsers().size)
        }
    }

    @Test
    fun removeUser_throwsIfUserAlreadyRemoved() {
        runBlocking {
            val user1 = createUserAndLogin()
            assertEquals(user1, user1.remove())
            assertFailsWith<IllegalStateException> {
                user1.remove()
            }
        }
    }

    @Test
    fun deleteUser() {
        runBlocking {
            val user1 = createUserAndLogin()
            val config = SyncConfiguration.create(
                user1,
                TestHelper.randomPartitionValue(),
                setOf(SyncObjectWithAllTypes::class)
            )
            Realm.open(config).close()
            assertTrue(fileExists(config.path))
            assertEquals(user1, app.currentUser)
            assertEquals(1, app.allUsers().size)
            user1.delete()
            assertEquals(User.State.REMOVED, user1.state)
            assertNull(app.currentUser)
            assertEquals(0, app.allUsers().size)
            assertFalse(fileExists(config.path))
        }
    }

    @Test
    fun deleteUser_loggedOutThrows() {
        runBlocking {
            val user1 = createUserAndLogin()
            val config = SyncConfiguration.create(
                user1,
                TestHelper.randomPartitionValue(),
                setOf(SyncObjectWithAllTypes::class)
            )
            Realm.open(config).close()
            user1.logOut()
            assertTrue(fileExists(config.path))
            assertFailsWith<IllegalStateException> {
                user1.delete()
            }
            assertTrue(fileExists(config.path))
        }
    }

    @Test
    fun deleteUser_throwsIfUserAlreadyDeleted() {
        runBlocking {
            val user1 = createUserAndLogin()
            user1.delete()
            assertFailsWith<IllegalStateException> {
                user1.delete()
            }
        }
    }

    @Test
    fun linkCredentials_emailPassword() = runBlocking {
        val anonUser = app.login(Credentials.anonymous())
        assertEquals(1, anonUser.identities.size)
        val (email, password) = randomEmail() to "123456"
        app.emailPasswordAuth.registerUser(email, password)
        val linkedUser = anonUser.linkCredentials(Credentials.emailPassword(email, password))

        assertSame(anonUser, linkedUser)
        assertEquals(2, linkedUser.identities.size)
        assertEquals(AuthenticationProvider.EMAIL_PASSWORD, linkedUser.identities[1].provider)

        // Validate that we cannot link a second set of credentials
        val otherEmail = randomEmail()
        val otherPassword = "123456"
        app.emailPasswordAuth.registerUser(otherEmail, otherPassword)
        val credentials = Credentials.emailPassword(otherEmail, otherPassword)

        assertFailsWith<CredentialsCannotBeLinkedException> {
            anonUser.linkCredentials(credentials)
        }.let {
            assertTrue(it.message!!.contains("linking a local-userpass identity is not allowed when one is already linked"), it.message)
        }
    }

    @Test
    fun linkCredentials_twoEmailAccountsThrows() = runBlocking {
        val (email1, password1) = randomEmail() to "123456"
        app.emailPasswordAuth.registerUser(email1, password1)
        val credentials1 = Credentials.emailPassword(email1, password1)
        val emailUser1 = app.login(credentials1)
        val (email2, password2) = randomEmail() to "123456"
        app.emailPasswordAuth.registerUser(email2, password2)
        val credentials2 = Credentials.emailPassword(email2, password2)
        assertFailsWith<CredentialsCannotBeLinkedException> {
            emailUser1.linkCredentials(credentials2)
        }.let {
            assertTrue(it.message!!.contains("linking a local-userpass identity is not allowed when one is already linked"), it.message)
        }
    }

    @Test
    fun linkCredentials_addingAnonymousToEmailThrows() = runBlocking {
        val (email1, password1) = randomEmail() to "123456"
        app.emailPasswordAuth.registerUser(email1, password1)
        val credentials1 = Credentials.emailPassword(email1, password1)
        val emailUser1 = app.login(credentials1)
        assertFailsWith<CredentialsCannotBeLinkedException> {
            emailUser1.linkCredentials(Credentials.anonymous())
        }.let {
            assertTrue(it.message!!.contains("linking an anonymous identity is not allowed"), it.message)
        }
    }

    @Test
    fun linkCredentials_linkingWithItselfThrows() = runBlocking {
        var anonUser = app.login(Credentials.anonymous())
        val email = randomEmail()
        val password = "123456"
        app.emailPasswordAuth.registerUser(email, password)
        val creds = Credentials.emailPassword(email, password)
        app.login(creds)
        assertFailsWith<CredentialsCannotBeLinkedException> {
            anonUser.linkCredentials(creds)
        }.let {
            assertTrue(it.message!!.contains("a user already exists with the specified provider"), it.message)
        }
    }

    // TODO Add support for ApiKeyAuth: https://github.com/realm/realm-kotlin/issues/432
    // @Test
    // fun linkUser_userApiKey() {
    //     // Generate API key
    //     val user: User = app.registerUserAndLogin(TestHelper.getRandomEmail(), "123456")
    //     val apiKey: ApiKey = user.apiKeys.create("my-key");
    //     user.logOut()
    //
    //     anonUser = app.login(Credentials.anonymous())
    //
    //     Assert.assertEquals(1, anonUser.identities.size)
    //
    //     // Linking with another user's API key is not allowed and must raise an AppException
    //     val exception = assertFailsWith<AppException> {
    //         anonUser.linkCredentials(Credentials.apiKey(apiKey.value))
    //     }
    //
    //     Assert.assertEquals("invalid user link request", exception.errorMessage);
    //     assertEquals(ErrorCode.Category.FATAL, exception.errorCode.category);
    //     Assert.assertEquals("realm::app::ServiceError", exception.errorCode.type);
    //     Assert.assertEquals(6, exception.errorCode.intValue());
    // }

    // TODO Add support for logging in using a custom function: https://github.com/realm/realm-kotlin/issues/741
    // @Test
    // fun linkUser_customFunction() = runBlocking {
    //     var anonUser = app.login(Credentials.anonymous())
    //     Assert.assertEquals(1, anonUser.identities.size)
    //
    //     val document = Document(mapOf(
    //         "mail" to TestHelper.getRandomEmail(),
    //         "id" to TestHelper.getRandomId() + 666
    //     ))
    //
    //     val credentials = Credentials.customFunction(document)
    //
    //     val linkedUser = anonUser.linkCredentials(credentials)
    //
    //     Assert.assertTrue(anonUser === linkedUser)
    //     Assert.assertEquals(2, linkedUser.identities.size)
    //     assertEquals(AuthenticationProvider.CUSTOM_FUNCTION, linkedUser.identities[1].provider)
    // }

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
        val (email, password) = randomEmail() to "123456"
        val user = createUserAndLogin(email, password)
        assertEquals(user, user)
        user.logOut()

        // Verify that it is not same object but uses underlying User equality on identity
        val sameUserNewLogin = app.login(Credentials.emailPassword(email, password))
        assertNotSame(user, sameUserNewLogin)
        assertEquals(user, sameUserNewLogin)

        val differentUser = createUserAndLogin(randomEmail(), password)
        assertNotEquals(user, differentUser)
    }

    @Test
    fun hashCode_user() = runBlocking {
        val (email, password) = randomEmail() to "123456"
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
        email: String = randomEmail(),
        password: String = "123456"
    ): User {
        app.emailPasswordAuth.registerUser(email, password)
        return app.login(Credentials.emailPassword(email, password))
    }
}
