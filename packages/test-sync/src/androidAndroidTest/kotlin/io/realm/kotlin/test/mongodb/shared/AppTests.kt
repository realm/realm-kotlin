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
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.ParentPk
import io.realm.kotlin.internal.platform.appFilesDirectory
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.AuthenticationProvider
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.InvalidCredentialsException
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.TestHelper.randomEmail
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AppTests {

    private lateinit var app: App

    @BeforeTest
    fun setup() {
        app = TestApp()
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.asTestApp.close()
        }
    }

    @Test
    fun defaultApp() {
        val defaultApp = App.create("foo")
        assertEquals("foo", defaultApp.configuration.appId)
        assertEquals(AppConfiguration.DEFAULT_BASE_URL, defaultApp.configuration.baseUrl)
    }

    @Test
    fun defaultApp_emptyIdThrows() {
        assertFailsWith<IllegalArgumentException> {
            App.create("")
        }
    }

    // TODO Minimal subset of login tests. Migrate AppTest from realm-java, when full API is in
    //  place
    // TODO Exhaustive test on io.realm.kotlin.mongodb.internal.Provider
    @Test
    fun login_Anonymous() {
        runBlocking {
            app.login(Credentials.anonymous())
        }
    }

    @Test
    fun login_NonCredentialImplThrows() {
        runBlocking {
            assertFailsWith<IllegalArgumentException> {
                app.login(object : Credentials {
                    override val authenticationProvider: AuthenticationProvider =
                        AuthenticationProvider.ANONYMOUS
                })
            }
        }
    }

    // Check that all auth providers throw the same exception for when invalid credentials are
    // presented.
    @Suppress("LoopWithTooManyJumpStatements")
    @Test
    fun login_invalidCredentialsThrows() = runBlocking {
        for (provider in AuthenticationProvider.values()) {
            when (provider) {
                AuthenticationProvider.ANONYMOUS -> {
                    // No user input, so invalid credentials are not possible.
                    null
                }
                AuthenticationProvider.API_KEY -> Credentials.apiKey("foo")
                AuthenticationProvider.EMAIL_PASSWORD ->
                    Credentials.emailPassword("foo@bar.com", "123456")
                AuthenticationProvider.JWT -> {
                    // There doesn't seem to be easy way to test this.
                    null
                }
                AuthenticationProvider.APPLE,
                AuthenticationProvider.FACEBOOK,
                AuthenticationProvider.GOOGLE -> {
                    // There doesn't seem to be a reliable way to throw "InvalidCredentials" for these.
                    null
                }
                AuthenticationProvider.CUSTOM_FUNCTION -> {
                    // There is no way to capture custom authentication credentials exceptions.
                    null
                }
            }?.let { credentials: Credentials ->
                assertFailsWith<InvalidCredentialsException> {
                    app.login(credentials)
                }
            }
        }
    }

    @Test
    fun currentUser() = runBlocking {
        assertNull(app.currentUser)
        val user: User = app.login(Credentials.anonymous())
        assertEquals(user, app.currentUser)
        user.logOut()
        assertNull(app.currentUser)
    }

    @Test
    fun allUsers() = runBlocking {
        assertEquals(0, app.allUsers().size)
        val user1 = app.login(Credentials.anonymous())
        var allUsers = app.allUsers()
        assertEquals(1, allUsers.size)
        assertTrue(allUsers.containsKey(user1.identity))
        assertEquals(user1, allUsers[user1.identity])

        // Only 1 anonymous user exists, so logging in again just returns the old one
        val user2 = app.login(Credentials.anonymous())
        allUsers = app.allUsers()
        assertEquals(1, allUsers.size)
        assertTrue(allUsers.containsKey(user2.identity))

        val user3: User = app.asTestApp.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        allUsers = app.allUsers()
        assertEquals(2, allUsers.size)
        assertTrue(allUsers.containsKey(user3.identity))

        // Logging out users that registered with email/password will just put them in LOGGED_OUT state
        user3.logOut()
        allUsers = app.allUsers()
        assertEquals(2, allUsers.size)
        assertTrue(allUsers.containsKey(user3.identity))
        assertEquals(User.State.LOGGED_OUT, allUsers[user3.identity]!!.state)

        // Logging out anonymous users will remove them completely
        user1.logOut()
        allUsers = app.allUsers()
        assertEquals(1, allUsers.size)
        assertFalse(allUsers.containsKey(user1.identity))
    }

    @Test
    fun allUsers_retrieveRemovedUser() = runBlocking {
        val user1: User = app.login(Credentials.anonymous())
        val allUsers: Map<String, User> = app.allUsers()
        assertEquals(1, allUsers.size)
        user1.logOut()
        assertEquals(1, allUsers.size)
        val userCopy: User = allUsers[user1.identity] ?: fail("Could not find user")
        assertEquals(user1, userCopy)
        assertEquals(User.State.REMOVED, userCopy.state)
        assertTrue(app.allUsers().isEmpty())
    }
//
//    @Test
//    fun switchUser() {
//        val user1: User = app.login(Credentials.anonymous())
//        assertEquals(user1, app.currentUser())
//        val user2: User = app.login(Credentials.anonymous())
//        assertEquals(user2, app.currentUser())
//
//        assertEquals(user1, app.switchUser(user1))
//        assertEquals(user1, app.currentUser())
//    }
//
//    @Test
//    fun switchUser_throwIfUserNotLoggedIn() = runBlocking {
//        val user1 = app.login(Credentials.anonymous())
//        val user2 = app.login(Credentials.anonymous())
//        assertEquals(user2, app.currentUser)
//
//        user1.logOut()
//        try {
//            app.switchUser(user1)
//            fail()
//        } catch (ignore: IllegalArgumentException) {
//        }
//    }

    @Test
    fun currentUser_FallbackToNextValidUser() = runBlocking {
        assertNull(app.currentUser)

        val user1 = app.createUserAndLogIn(randomEmail(), "123456")
        assertEquals(user1, app.currentUser)

        val user2 = app.createUserAndLogIn(randomEmail(), "123456")
        assertEquals(user2, app.currentUser)

        user2.logOut()
        assertEquals(user1, app.currentUser)

        user1.logOut()
        assertNull(app.currentUser)
    }

//    @Test
//    fun switchUser_nullThrows() {
//        try {
//            app.switchUser(TestHelper.getNull())
//            fail()
//        } catch (ignore: IllegalArgumentException) {
//        }
//    }
//
//    @Ignore("Add this test once we have support for both EmailPassword and ApiKey Auth Providers")
//    @Test
//    fun switchUser_authProvidersLockUsers() {
//        TODO("FIXME")
//    }
//
//    @Test
//    fun authListener() {
//        val userRef = AtomicReference<User>(null)
//        looperThread.runBlocking {
//            val authenticationListener = object : AuthenticationListener {
//                override fun loggedIn(user: User) {
//                    userRef.set(user)
//                    user.logOutAsync { /* Ignore */ }
//                }
//
//                override fun loggedOut(user: User) {
//                    assertEquals(userRef.get(), user)
//                    looperThread.testComplete()
//                }
//            }
//            app.addAuthenticationListener(authenticationListener)
//            app.login(Credentials.anonymous())
//        }
//    }
//
//    @Test
//    fun authListener_nullThrows() {
//        assertFailsWith<IllegalArgumentException> { app.addAuthenticationListener(TestHelper.getNull()) }
//    }
//
//    @Test
//    fun authListener_remove() = looperThread.runBlocking {
//        val failListener = object : AuthenticationListener {
//            override fun loggedIn(user: User) { fail() }
//            override fun loggedOut(user: User) { fail() }
//        }
//        val successListener = object : AuthenticationListener {
//            override fun loggedOut(user: User) { fail() }
//            override fun loggedIn(user: User) { looperThread.testComplete() }
//        }
//        // This test depends on listeners being executed in order which is an
//        // implementation detail, but there isn't a sure fire way to do this
//        // without depending on implementation details or assume a specific timing.
//        app.addAuthenticationListener(failListener)
//        app.addAuthenticationListener(successListener)
//        app.removeAuthenticationListener(failListener)
//        app.login(Credentials.anonymous())
//    }
//
//    @Test
//    fun functions_defaultCodecRegistry() {
//        var user = app.login(Credentials.anonymous())
//        assertEquals(app.configuration.defaultCodecRegistry, app.getFunctions(user).defaultCodecRegistry)
//    }
//
//    @Test
//    fun functions_customCodecRegistry() {
//        var user = app.login(Credentials.anonymous())
//        val registry = CodecRegistries.fromCodecs(StringCodec())
//        assertEquals(registry, app.getFunctions(user, registry).defaultCodecRegistry)
//    }
//

    @Test
    fun encryptedMetadataRealm() {
        // Create new test app with a random encryption key
        val key = TestHelper.getRandomKey()
        val app = TestApp(
            appName = TEST_APP_FLEX,
            builder = {
                it
                    .encryptionKey(key)
                    .syncRootDirectory("${appFilesDirectory()}/foo")
            }
        )

        try {
            // Create Realm in order to create the sync metadata Realm
            val user = app.createUserAndLogin()
            val syncConfig = SyncConfiguration
                .Builder(user, setOf(ParentPk::class, ChildPk::class))
                .build()
            Realm.open(syncConfig).close()

            // Create a configuration pointing to the metadata Realm for that app
            val lastSetSchemaVersion = 6L
            val metadataDir = "${app.configuration.syncRootDirectory}/mongodb-realm/${app.configuration.appId}/server-utility/metadata/"
            val config = RealmConfiguration
                .Builder(setOf())
                .name("sync_metadata.realm")
                .directory(metadataDir)
                .schemaVersion(lastSetSchemaVersion)
                .encryptionKey(key)
                .build()
            assertTrue(fileExists(config.path))

            // Should be possible to open the encrypted metadata realm file with the encryption key
            Realm.open(config).close()
        } finally {
            app.close()
        }
    }

    @Test
    fun encryptedMetadataRealm_openWithWrongKeyThrows() {
        // Create new test app with a random encryption key
        val correctKey = TestHelper.getRandomKey()
        val app = TestApp(
            appName = TEST_APP_FLEX,
            builder = {
                it
                    .encryptionKey(correctKey)
                    .syncRootDirectory("${appFilesDirectory()}/foo")
            }
        )

        try {
            // Create Realm in order to create the sync metadata Realm
            val user = app.createUserAndLogin()
            val syncConfig = SyncConfiguration
                .Builder(user, setOf(ParentPk::class, ChildPk::class))
                .build()
            Realm.open(syncConfig).close()

            // Create a configuration pointing to the metadata Realm for that app
            val metadataDir = "${app.configuration.syncRootDirectory}/mongodb-realm/${app.configuration.appId}/server-utility/metadata/"
            val wrongKey = TestHelper.getRandomKey()
            val config = RealmConfiguration
                .Builder(setOf())
                .name("sync_metadata.realm")
                .directory(metadataDir)
                .encryptionKey(wrongKey)
                .build()
            assertTrue(fileExists(config.path))

            // Open the metadata realm file with an invalid encryption key
            assertNotEquals(correctKey, wrongKey)
            assertFailsWithMessage<IllegalArgumentException>("Could not open Realm with the given configuration") {
                Realm.open(config)
            }
        } finally {
            app.close()
        }
    }

    @Test
    fun encryptedMetadataRealm_openWithoutKeyThrows() {
        // Create new test app with a random encryption key
        val app = TestApp(
            appName = TEST_APP_FLEX,
            builder = {
                it
                    .encryptionKey(TestHelper.getRandomKey())
                    .syncRootDirectory("${appFilesDirectory()}/foo")
            }
        )

        try {
            // Create Realm in order to create the sync metadata Realm
            val user = app.createUserAndLogin()
            val syncConfig = SyncConfiguration
                .Builder(user, setOf(ParentPk::class, ChildPk::class))
                .build()
            Realm.open(syncConfig).close()

            // Create a configuration pointing to the metadata Realm for that app
            val metadataDir = "${app.configuration.syncRootDirectory}/mongodb-realm/${app.configuration.appId}/server-utility/metadata/"
            val config = RealmConfiguration
                .Builder(setOf())
                .name("sync_metadata.realm")
                .directory(metadataDir)
                .build()
            assertTrue(fileExists(config.path))

            // Open the metadata realm file without a valid encryption key
            assertFailsWithMessage<IllegalArgumentException>("Could not open Realm with the given configuration") {
                Realm.open(config)
            }
        } finally {
            app.close()
        }
    }

//
//    // Check that it is possible to have two Java instances of an App class, but they will
//    // share the underlying App state.
//    @Test
//    fun multipleInstancesSameApp() {
//        // Create a second copy of the test app
//        val app2 = TestApp()
//        try {
//            // User handling are shared between each app
//            val user = app.login(Credentials.anonymous());
//            assertEquals(user, app2.currentUser())
//            assertEquals(user, app.allUsers().values.first())
//            assertEquals(user, app2.allUsers().values.first())
//
//            user.logOut();
//
//            assertNull(app.currentUser())
//            assertNull(app2.currentUser())
//        } finally {
//            app2.close()
//        }
//    }
}
