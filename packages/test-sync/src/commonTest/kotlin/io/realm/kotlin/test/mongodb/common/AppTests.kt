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

package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.ParentPk
import io.realm.kotlin.internal.platform.appFilesDirectory
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.AuthenticationChange
import io.realm.kotlin.mongodb.AuthenticationProvider
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.LoggedIn
import io.realm.kotlin.mongodb.LoggedOut
import io.realm.kotlin.mongodb.Removed
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.InvalidCredentialsException
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.mongodb.use
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.TestHelper.randomEmail
import io.realm.kotlin.test.util.receiveOrFail
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class AppTests {

    private lateinit var app: App

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
    fun defaultApp() {
        App.create("foo").use { defaultApp ->
            assertEquals("foo", defaultApp.configuration.appId)
            assertEquals(AppConfiguration.DEFAULT_BASE_URL, defaultApp.configuration.baseUrl)
        }
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

    @Test
    @Ignore // Waiting for https://github.com/realm/realm-core/issues/6514
    fun currentUser_clearedAfterUserIsRemoved() = runBlocking {
        assertNull(app.currentUser)
        val user1 = app.login(Credentials.anonymous())
        assertEquals(user1, app.currentUser)
        user1.remove()
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
    @Test
    fun authenticationChangeAsFlow() = runBlocking<Unit> {
        val c = Channel<AuthenticationChange>(1)
        val job = async {
            app.authenticationChangeAsFlow().collect {
                c.send(it)
            }
        }

        val user1 = app.login(Credentials.anonymous())
        val loggedInEvent = c.receiveOrFail()
        assertTrue(loggedInEvent is LoggedIn)
        assertSame(user1, loggedInEvent.user)

        user1.logOut()
        val loggedOutEvent = c.receiveOrFail()
        assertTrue(loggedOutEvent is LoggedOut)
        assertSame(user1, loggedOutEvent.user)

        // Repeating logout does not trigger a new event
        user1.logOut()
        val user2 = app.login(Credentials.anonymous())
        val reloginEvent = c.receiveOrFail()
        assertEquals(user2, reloginEvent.user)
        assertTrue(reloginEvent is LoggedIn)

        job.cancel()
        c.close()
    }

    @Test
    fun authenticationChangeAsFlow_removeUser() = runBlocking<Unit> {
        val c = Channel<AuthenticationChange>(1)
        val job = async {
            app.authenticationChangeAsFlow().collect {
                c.send(it)
            }
        }
        val user1 = app.login(Credentials.anonymous(reuseExisting = true))
        val loggedInEvent = c.receiveOrFail()
        assertTrue(loggedInEvent is LoggedIn)

        user1.remove()
        val loggedOutEvent = c.receiveOrFail()
        assertTrue(loggedOutEvent is Removed)
        assertSame(user1, loggedOutEvent.user)

        job.cancel()
        c.close()

        // Work-around for https://github.com/realm/realm-core/issues/6514
        // By logging the user back in, the TestApp teardown can correctly remove it.
        app.login(Credentials.anonymous(reuseExisting = true)).logOut()
    }

    @Test
    fun authenticationChangeAsFlow_deleteUser() = runBlocking<Unit> {
        val c = Channel<AuthenticationChange>(1)
        val job = async {
            app.authenticationChangeAsFlow().collect {
                c.send(it)
            }
        }
        val user = app.login(Credentials.anonymous(reuseExisting = true))
        val loggedInEvent = c.receiveOrFail()
        assertTrue(loggedInEvent is LoggedIn)

        user.delete()
        val loggedOutEvent = c.receiveOrFail()
        assertTrue(loggedOutEvent is Removed)
        assertSame(user, loggedOutEvent.user)

        job.cancel()
        c.close()
    }

    @Test
    fun authenticationChangeAsFlow_throwsWhenExceedCapacity() = runBlocking<Unit> {
        val latch = Mutex(locked = true)
        val job = async {
            app.authenticationChangeAsFlow().collect {
                // Block `flow` from collecting any more events beside the first.
                latch.withLock {
                    // Allow flow to continue
                }
            }
        }
        // Logging in 9 users should hit the capacity of the flow, causing the next
        // login to fail.
        repeat(9) {
            app.createUserAndLogIn()
        }
        assertFailsWith<IllegalStateException> {
            app.createUserAndLogIn()
        }
        job.cancel()
    }

    @Test
    fun encryptedMetadataRealm() {
        // Create new test app with a random encryption key
        val key = TestHelper.getRandomKey()
        TestApp(
            "encryptedMetadataRealm",
            appName = TEST_APP_FLEX,
            builder = {
                it
                    .encryptionKey(key)
                    .syncRootDirectory("${appFilesDirectory()}/foo")
            }
        ).use { app ->
            // Create Realm in order to create the sync metadata Realm
            val user = app.asTestApp.createUserAndLogin()
            val syncConfig = SyncConfiguration
                .Builder(user, FLEXIBLE_SYNC_SCHEMA)
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
        }
    }

    @Test
    fun encryptedMetadataRealm_openWithWrongKeyThrows() {
        // Create new test app with a random encryption key
        val correctKey = TestHelper.getRandomKey()
        TestApp(
            "encryptedMetadataRealm_openWithWrongKeyThrows",
            appName = TEST_APP_FLEX,
            builder = {
                it
                    .encryptionKey(correctKey)
                    .syncRootDirectory("${appFilesDirectory()}/foo")
            }
        ).use { app ->
            // Create Realm in order to create the sync metadata Realm
            val user = app.asTestApp.createUserAndLogin()
            val syncConfig = SyncConfiguration
                .Builder(user, FLEXIBLE_SYNC_SCHEMA)
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
            assertFailsWithMessage<IllegalStateException>("Failed to open Realm file at path") {
                Realm.open(config)
            }
        }
    }

    @Test
    fun encryptedMetadataRealm_openWithoutKeyThrows() {
        // Create new test app with a random encryption key
        TestApp(
            "encryptedMetadataRealm_openWithoutKeyThrows",
            appName = TEST_APP_FLEX,
            builder = {
                it
                    .encryptionKey(TestHelper.getRandomKey())
                    .syncRootDirectory("${appFilesDirectory()}/foo")
            }
        ).use { app ->
            // Create Realm in order to create the sync metadata Realm
            val user = app.asTestApp.createUserAndLogin()
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
            assertFailsWithMessage<IllegalStateException>("Failed to open Realm file at path") {
                Realm.open(config)
            }
        }
    }
}
