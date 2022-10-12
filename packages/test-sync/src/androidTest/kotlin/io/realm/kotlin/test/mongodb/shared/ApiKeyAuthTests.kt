package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.test.mongodb.TEST_APP_PARTITION
import io.realm.kotlin.test.mongodb.TestApp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    // TODO Just for checking JNI. Create proper test
    @Test
    fun create() = runBlocking {
        val key = user.apiKeyAuth.create("foo")
        assertEquals("foo", key.name)
    }

    @Test
    fun fetch() = runBlocking {
        val key = user.apiKeyAuth.create("foo")
        val test = user.apiKeyAuth.fetch(key.id)
        assertEquals("foo", test.name)
    }

    // TODO Just for checking JNI. Create proper test
    @Test
    fun fetchAll() = runBlocking {
        user.apiKeyAuth.create("foo")
        user.apiKeyAuth.create("bar")
        user.apiKeyAuth.create("baz")

        val keys = user.apiKeyAuth.fetchAll()
        assertEquals(3, keys.size)
    }
}
