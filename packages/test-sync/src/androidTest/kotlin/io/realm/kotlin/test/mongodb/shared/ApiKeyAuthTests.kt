package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.auth.ApiKeyAuth
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.test.mongodb.TEST_APP_PARTITION
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.types.ObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiKeyAuthTests {
    private lateinit var app: TestApp
    private lateinit var user: User
    private lateinit var provider: ApiKeyAuth
    @BeforeTest
    fun setup() {
        app = TestApp(appName = TEST_APP_PARTITION)
        user = app.createUserAndLogin()
        provider = user.apiKeyAuth
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun create() = runBlocking {
        val key = provider.create("foo")
        assertEquals("foo", key.name)
        assertNotNull(key.value)
        assertNotNull(key.id)
        assertTrue(key.enabled)
    }

    @Test
    fun create_throwsWithInvalidName(): Unit = runBlocking {
        // worth creating a more specific exception?
        assertFailsWith<IllegalArgumentException> {
            provider.create("%s")
        }
    }

    @Test
    fun create_throwsWithNoName(): Unit = runBlocking {
        // worth creating a more specific exception?
        assertFailsWith<IllegalArgumentException> {
            provider.create("")
        }
    }

    @Test
    fun fetch() = runBlocking {
        val key1 = provider.create("foo")
        val key2 = provider.fetch(key1.id)
        assertEquals(key1.id, key2!!.id)
        assertEquals(key1.name, key2.name)
        assertNull(key2.value)
        assertEquals(key1.enabled, key2.enabled)
    }

    @Test
    fun fetch_nonExistingKeyThrows(): Unit = runBlocking {
        // worth creating a more specific exception?
        assertFailsWith<ServiceException> {
            provider.fetch(ObjectId.create())
        }
    }

    @Test
    fun fetchAll() = runBlocking {
        val key1 = provider.create("foo")
        val key2 = provider.create("bar")
        val keys = provider.fetchAll()
        assertEquals(2, keys.size)
        assertTrue(keys.any { it.id == key1.id })
        assertTrue(keys.any { it.id == key2.id })
    }

    @Test
    fun delete(): Unit = runBlocking {
        val key1 = provider.create("foo")
        assertNotNull(provider.fetch(key1.id))
        provider.delete(key1.id)
        assertFailsWith<ServiceException> {
            provider.fetch(key1.id)
        }
    }

    @Test
    fun delete_nonExisitingKeyNoOps(): Unit = runBlocking {
        // worth creating a more specific exception?
        provider.create("foo")
        val keys = provider.fetchAll()
        assertEquals(1, keys.size)
        provider.delete(ObjectId.create())
        val keysAfterInvalidDelete = provider.fetchAll()
        assertEquals(1, keysAfterInvalidDelete.size)
    }

    @Test
    fun enable(): Unit = runBlocking {
        val key = provider.create("foo")
        provider.disable(key.id)
        assertFalse(provider.fetch(key.id)!!.enabled)
        provider.enable(key.id)
        assertTrue(provider.fetch(key.id)!!.enabled)
    }

    @Test
    fun enable_alreadyEnabled() = runBlocking {
        val key = provider.create("foo")
        provider.disable(key.id)
        assertFalse(provider.fetch(key.id)!!.enabled)
        provider.enable(key.id)
        assertTrue(provider.fetch(key.id)!!.enabled)
        provider.enable(key.id)
        assertTrue(provider.fetch(key.id)!!.enabled)
    }

    @Test
    fun enable_nonExistingKeyThrows(): Unit = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            provider.enable(ObjectId.create())
        }
    }

    @Test
    fun disable() = runBlocking {
        val key = provider.create("foo")
        provider.disable(key.id)
        assertFalse(provider.fetch(key.id)!!.enabled)
    }

    @Test
    fun disable_alreadyDisabled() = runBlocking {
        val key = provider.create("foo")
        provider.disable(key.id)
        assertFalse(provider.fetch(key.id)!!.enabled)
        provider.disable(key.id)
        assertFalse(provider.fetch(key.id)!!.enabled)
    }

    @Test
    fun disable_nonExistingKeyThrows(): Unit = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            provider.disable(ObjectId.create())
        }
    }
}
