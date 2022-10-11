package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.auth.EmailPasswordAuth
import io.realm.kotlin.mongodb.exceptions.AppException
import io.realm.kotlin.mongodb.exceptions.AuthException
import io.realm.kotlin.mongodb.exceptions.BadRequestException
import io.realm.kotlin.mongodb.exceptions.UserAlreadyConfirmedException
import io.realm.kotlin.mongodb.exceptions.UserAlreadyExistsException
import io.realm.kotlin.mongodb.exceptions.UserNotFoundException
import io.realm.kotlin.test.mongodb.TEST_APP_PARTITION
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.util.BaasApp
import io.realm.kotlin.test.mongodb.util.Service
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.addEmailProvider
import io.realm.kotlin.test.util.TestHelper
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ApiKeyAuthTests {
    private lateinit var app: TestApp
    private lateinit var user: User
    @BeforeTest
    fun setup() {
        app = TestApp(appName = TEST_APP_PARTITION)
        user = app.createUserAndLogin()
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun create() = runBlocking {
        val key = user.apiKeyAuth.create("foo")
        assertEquals("foo", key.name)
    }

}
