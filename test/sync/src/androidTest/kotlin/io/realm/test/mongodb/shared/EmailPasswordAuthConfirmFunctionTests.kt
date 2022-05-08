package io.realm.test.mongodb.shared

import io.realm.internal.platform.runBlocking
import io.realm.mongodb.Credentials
import io.realm.mongodb.auth.EmailPasswordAuth
import io.realm.mongodb.exceptions.AuthException
import io.realm.mongodb.exceptions.UserAlreadyConfirmedException
import io.realm.mongodb.exceptions.UserNotFoundException
import io.realm.test.mongodb.TEST_APP_2
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.asTestApp
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for EmailPasswordAuth that requires that
 * the custom confirm function is enabled.
 *
 * These tests cannot be combined with [EmailPasswordAuthAutoConfirmTests]
 * as there seem to be some kind of race condition on the server
 * when switching between confirmation methods which result in
 * flaky tests.
 */
class EmailPasswordAuthConfirmFunctionTests {

    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        app = TestApp(appName = TEST_APP_2)
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun retryCustomConfirmation() {
        val (email, password) = "realm_pending_${Random.nextInt()}@10gen.com" to "123456"
        val adminApi = app.asTestApp
        runBlocking {
            val provider = app.emailPasswordAuth
            provider.registerUser(email, password) // Will move to "pending"
            assertFailsWith<AuthException> {
                app.login(Credentials.emailPassword(email, password))
            }
            provider.retryCustomConfirmation(email) // Will properly "confirm"
            app.login(Credentials.emailPassword(email, password))
        }
    }

    @Test
    fun retryCustomConfirmation_failConfirmation() = runBlocking {
        // Only emails containing realm_tests_do_autoverify will be confirmed
        val email = "do_not_confirm@10gen.com"
        val provider = app.emailPasswordAuth
        val exception = assertFailsWith<UserNotFoundException> {
            provider.retryCustomConfirmation(email)
        }
        assertTrue(exception.message!!.contains("user not found"), exception.message)
    }

    @Test
    fun retryCustomConfirmation_noUserThrows() {
        val email = "realm_pending_${Random.nextInt()}@10gen.com"
        runBlocking {
            val provider = app.emailPasswordAuth
            provider.registerUser(email, "123456")
            assertFailsWith<UserNotFoundException> {
                provider.retryCustomConfirmation("foo@gen.com")
            }
        }
    }

    @Test
    fun retryCustomConfirmation_alreadyConfirmedThrows() {
        val email = "realm_verify_${Random.nextInt()}@10gen.com"
        runBlocking {
            val provider = app.emailPasswordAuth
            provider.registerUser(email, "123456")
            assertFailsWith<UserAlreadyConfirmedException> {
                provider.retryCustomConfirmation(email)
            }
        }
    }

    @Test
    fun retryCustomConfirmation_invalidArgumentsThrows() {
        val provider: EmailPasswordAuth = app.emailPasswordAuth
        runBlocking {
            assertFailsWith<IllegalArgumentException> { provider.retryCustomConfirmation("") }
        }
    }
}
