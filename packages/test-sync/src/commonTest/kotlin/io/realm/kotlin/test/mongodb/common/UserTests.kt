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

@file:OptIn(ExperimentalRealmSerializerApi::class)

package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.Realm
import io.realm.kotlin.annotations.ExperimentalRealmSerializerApi
import io.realm.kotlin.entities.sync.CollectionDataType
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.AuthenticationProvider
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.CredentialsCannotBeLinkedException
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.mongodb.ext.customData
import io.realm.kotlin.mongodb.ext.customDataAsBsonDocument
import io.realm.kotlin.mongodb.ext.insertOne
import io.realm.kotlin.mongodb.mongo.MongoClient
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.common.mongo.CustomDataType
import io.realm.kotlin.test.mongodb.common.mongo.TEST_SERVICE_NAME
import io.realm.kotlin.test.mongodb.common.mongo.customEjsonSerializer
import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.TestHelper.randomEmail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonString
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.Bson
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

const val CUSTOM_USER_DATA_FIELD = "custom_field"
const val CUSTOM_USER_DATA_VALUE = "custom_data"

class UserTests {

    private lateinit var app: TestApp

    @BeforeTest
    fun setUp() {
        app = TestApp(this::class.simpleName)
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
        assertEquals(AuthenticationProvider.EMAIL_PASSWORD, emailUser.identities.first().provider)
        emailUser.logOut()
        // AuthenticationProvider is not removed once user is logged out
        assertEquals(AuthenticationProvider.EMAIL_PASSWORD, emailUser.identities.first().provider)
    }

    @Test
    fun getAccessToken() = runBlocking {
        val email = randomEmail()
        val emailUser = createUserAndLogin(email, "123456")
        assertFalse(emailUser.accessToken.isEmpty())
        emailUser.logOut()
        // AccessToken is removed once user is logged out
        assertTrue(emailUser.accessToken.isEmpty())
    }

    @Test
    fun getRefreshToken() = runBlocking {
        val email = randomEmail()
        val emailUser = createUserAndLogin(email, "123456")
        assertFalse(emailUser.refreshToken.isEmpty())
        emailUser.logOut()
        // RefreshToken is removed once user is logged out
        assertTrue(emailUser.refreshToken.isEmpty())
    }

    @Test
    fun getDeviceId() = runBlocking {
        val email = randomEmail()
        val emailUser = createUserAndLogin(email, "123456")
        assertFalse(emailUser.deviceId.isEmpty())
        emailUser.logOut()
        // DeviceId is not removed once user is logged out
        assertFalse(emailUser.deviceId.isEmpty())
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
                PARTITION_BASED_SCHEMA
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
                PARTITION_BASED_SCHEMA
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

        assertFailsWith<ServiceException> {
            anonUser.linkCredentials(credentials)
        }.let {
            assertTrue(
                it.message!!.contains("unauthorized"),
                it.message
            )
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
        assertFailsWith<ServiceException> {
            emailUser1.linkCredentials(credentials2)
        }.let {
            assertTrue(
                it.message!!.contains("unauthorized"),
                it.message
            )
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
            assertTrue(
                it.message!!.contains("Cannot add anonymous credentials to an existing user"),
                it.message
            )
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
        assertFailsWith<ServiceException> {
            anonUser.linkCredentials(creds)
        }.let {
            assertTrue(
                it.message!!.contains("unauthorized"),
                it.message
            )
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

    @Serializable
    data class SerializableCustomData(
        @SerialName("_id") val id: String,
        @SerialName("user_id") val userId: String,
        @SerialName("custom_field") val customField: String
    )

    @Test
    fun customData_initiallyNull() {
        val user = runBlocking {
            val (email, password) = randomEmail() to "123456"
            createUserAndLogin(email, password)
        }
        // Newly registered users do not have any custom data with current test server setup
        assertNull(user.customDataAsBsonDocument())
        assertNull(user.customData<SerializableCustomData>())
        assertNull(user.customData(SerializableCustomData.serializer()))
    }

    @Test
    fun customData_refresh() {
        val user = runBlocking {
            val (email, password) = randomEmail() to "123456"
            createUserAndLogin(email, password)
        }
        // Newly registered users do not have any custom data with current test server setup
        assertNull(user.customDataAsBsonDocument())
        assertNull(user.customData<SerializableCustomData>())
        assertNull(user.customData<SerializableCustomData>(SerializableCustomData.serializer()))

        updatecustomDataAsBsonDocument(
            user,
            BsonDocument(CUSTOM_USER_DATA_FIELD to BsonString(CUSTOM_USER_DATA_VALUE))
        )

        runBlocking {
            user.refreshCustomData()
        }
        val userData = user.customDataAsBsonDocument()
        assertNotNull(userData)
        assertEquals(CUSTOM_USER_DATA_VALUE, userData[CUSTOM_USER_DATA_FIELD]!!.asString().value)

        setOf(
            user.customData<SerializableCustomData>(),
            user.customData<SerializableCustomData>(SerializableCustomData.serializer())
        ).forEach { serializableCustomData ->
            assertNotNull(serializableCustomData)
            assertEquals(CUSTOM_USER_DATA_VALUE, serializableCustomData.customField)
        }
    }

    @Test
    fun customData_refreshByLogout() {
        val (email, password) = randomEmail() to "123456"
        val user = runBlocking {
            createUserAndLogin(email, password)
        }
        // Newly registered users do not have any custom data with current test server setup
        assertNull(user.customDataAsBsonDocument())
        assertNull(user.customData<SerializableCustomData>())
        assertNull(user.customData<SerializableCustomData>(SerializableCustomData.serializer()))

        updatecustomDataAsBsonDocument(
            user,
            BsonDocument(CUSTOM_USER_DATA_FIELD to BsonString(CUSTOM_USER_DATA_VALUE))
        )

        // But will be updated when authorization token is refreshed
        runBlocking {
            user.logOut()
            app.login(Credentials.emailPassword(email, password))
        }

        val userData = user.customDataAsBsonDocument()
        assertNotNull(userData)
        assertEquals(CUSTOM_USER_DATA_VALUE, userData[CUSTOM_USER_DATA_FIELD]!!.asString().value)

        setOf(
            user.customData<SerializableCustomData>(),
            user.customData<SerializableCustomData>(SerializableCustomData.serializer())
        ).forEach { serializableCustomData ->
            assertNotNull(serializableCustomData)
            assertEquals(CUSTOM_USER_DATA_VALUE, serializableCustomData.customField)
        }
    }

    @Test
    @OptIn(ExperimentalKBsonSerializerApi::class)
    fun mongoClient_defaultSerializer() = runBlocking<Unit> {
        val (email, password) = randomEmail() to "123456"
        val user = runBlocking {
            createUserAndLogin(email, password)
        }
        @OptIn(ExperimentalRealmSerializerApi::class)
        val client: MongoClient = user.mongoClient(TEST_SERVICE_NAME)
        assertIs<Int>(client.database(app.clientAppId).collection<CollectionDataType>("CollectionDataType").insertOne(CollectionDataType("object-1")))
    }

    @Test
    @OptIn(ExperimentalKBsonSerializerApi::class)
    fun mongoClient_customSerializer() = runBlocking<Unit> {
        val (email, password) = randomEmail() to "123456"
        val user = runBlocking {
            createUserAndLogin(email, password)
        }
        val collectionWithDefaultSerializer =
            user.mongoClient(TEST_SERVICE_NAME)
                .database(app.clientAppId)
                .collection<CustomDataType>("CollectionDataType")
        assertFailsWithMessage<SerializationException>("Serializer for class 'CustomDataType' is not found.") {
            collectionWithDefaultSerializer.insertOne(CustomDataType("dog-1"))
        }

        val collectionWithCustomSerializer =
            user.mongoClient(TEST_SERVICE_NAME, customEjsonSerializer).database(app.clientAppId)
                .collection<CustomDataType>("CollectionDataType")
        assertIs<Int>(collectionWithCustomSerializer.insertOne(CustomDataType("dog-1")))
    }

    @Test
    @OptIn(ExperimentalKBsonSerializerApi::class)
    fun mongoClient_unknownClient() = runBlocking<Unit> {
        val (email, password) = randomEmail() to "123456"
        val user = runBlocking {
            createUserAndLogin(email, password)
        }
        val mongoClient = user.mongoClient("UNKNOWN_SERVICE")
        val collection =
            mongoClient.database(app.clientAppId).collection<CollectionDataType>("CollectionDataType")
        assertFailsWithMessage<ServiceException>("Cannot access member 'insertOne' of undefined") {
            collection.insertOne(CollectionDataType("object-1"))
        }
    }

    @Test
    @OptIn(ExperimentalKBsonSerializerApi::class)
    fun mongoClient_throwsOnLoggedOutUser() = runBlocking<Unit> {
        val (email, password) = randomEmail() to "123456"
        val user = runBlocking {
            createUserAndLogin(email, password)
        }
        user.logOut()
        assertFailsWithMessage<IllegalStateException>("Cannot obtain a MongoClient from a logged out user") {
            user.mongoClient("UNKNOWN_SERVICE")
        }
    }

    private fun updatecustomDataAsBsonDocument(user: User, data: BsonDocument) {
        // Name of collection and property used for storing custom user data. Must match server config.json
        val COLLECTION_NAME = "UserData"
        val USER_ID_FIELD = "user_id"

        runBlocking {
            app.insertDocument(
                COLLECTION_NAME,
                Bson.toJson(data.append(USER_ID_FIELD, BsonString(user.id)))
            )
        }
    }

    private suspend fun createUserAndLogin(
        email: String = randomEmail(),
        password: String = "123456"
    ): User {
        app.emailPasswordAuth.registerUser(email, password)
        return app.login(Credentials.emailPassword(email, password))
    }
}
