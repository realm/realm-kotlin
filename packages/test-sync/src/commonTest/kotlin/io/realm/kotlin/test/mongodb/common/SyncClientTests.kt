package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.Realm
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.use
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [io.realm.kotlin.mongodb.sync.Sync] that is accessed through
 * [io.realm.kotlin.mongodb.App.sync].
 */
class SyncClientTests {

    private lateinit var user: User
    private lateinit var app: App

    @BeforeTest
    fun setup() {
        app = TestApp(
            appName = TEST_APP_FLEX,
            builder = {
                it.syncRootDirectory(PlatformUtils.createTempDir("syncclient-"))
            }
        )
        val (email, password) = TestHelper.randomEmail() to "password1234"
        user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.asTestApp.close()
        }
    }

    @Test
    fun sync() {
        assertNotNull(app.sync)
    }

    // There is no way to test reconnect automatically, so just verify that code path does not crash.
    @Test
    fun reconnect_noRealms() {
        app.sync.reconnect()
    }

    // There is no way to test reconnect automatically, so just verify that code path does not crash.
    @Test
    fun reconnect() {
        val config = SyncConfiguration.create(user, schema = setOf())
        Realm.open(config).use {
            app.sync.reconnect()
        }
    }

    @Test
    fun hasSyncSessions_noRealms() {
        assertFalse(app.sync.hasSyncSessions)
    }

    @Test
    fun hasSyncSessions() {
        val config = SyncConfiguration.create(user, schema = setOf())
        Realm.open(config).use {
            assertTrue(app.sync.hasSyncSessions)
        }
    }

    @Test
    fun waitForSessionsToTerminate_noRealms() {
        app.sync.waitForSessionsToTerminate()
    }

    @Test
    fun waitForSessionsToTerminate() {
        val config1 = SyncConfiguration.Builder(user, schema = setOf()).build()
        val config2 = SyncConfiguration.Builder(user, schema = setOf()).name("other.realm").build()

        Realm.open(config1).use { realm1 ->
            assertTrue(app.sync.hasSyncSessions)
            Realm.open(config2).use { /* do nothing */ }
            assertTrue(app.sync.hasSyncSessions)
        }
        app.sync.waitForSessionsToTerminate()
        assertFalse(app.sync.hasSyncSessions)
    }
}
