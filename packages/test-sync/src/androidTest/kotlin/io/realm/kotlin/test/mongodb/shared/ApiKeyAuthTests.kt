/*
 * Copyright 2022 Realm Inc.
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

import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.auth.ApiKeyAuth
import io.realm.kotlin.mongodb.exceptions.AppException
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.TEST_APP_PARTITION
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.addEmailProvider
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.initializePartitionSync
import io.realm.kotlin.types.ObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ApiKeyAuthTests {
    private lateinit var app: TestApp
    private lateinit var user: User
    private lateinit var provider: ApiKeyAuth

    private enum class Method {
        CREATE,
        FETCH_SINGLE,
        FETCH_ALL,
        DELETE,
        ENABLE,
        DISABLE
    }

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
    fun create_throwsWithInvalidName() {
        assertFailsWithMessage<IllegalArgumentException>("[Service][InvalidParameter(6)] can only contain ASCII letters, numbers, underscores, and hyphens.") {
            runBlocking {
                provider.create("%s")
            }
        }
    }

    @Test
    fun create_throwsWithNoName() {
        assertFailsWithMessage<IllegalArgumentException>("[Service][Unknown(-1)] 'name' is a required string.") {
            runBlocking {
                provider.create("")
            }
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
    fun fetch_nonExistingKeyThrows() = runBlocking {
        assertNull(provider.fetch(ObjectId.create()))
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
    fun fetchAll_noExistingKeysGiveEmptyList() = runBlocking {
        val keys = provider.fetchAll()
        assertEquals(0, keys.size)
    }

    @Test
    fun delete() = runBlocking {
        val key1 = provider.create("foo")
        assertNotNull(provider.fetch(key1.id))
        provider.delete(key1.id)
        assertNull(provider.fetch(key1.id))
    }

    @Test
    fun delete_nonExisitingKeyNoOps(): Unit = runBlocking {
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
    fun enable_nonExistingKeyThrows() {
        assertFailsWithMessage<IllegalArgumentException>("[Service][ApiKeyNotFound(35)] API key not found.") {
            runBlocking {
                provider.enable(ObjectId.create())
            }
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
    fun disable_nonExistingKeyThrows() {
        assertFailsWithMessage<IllegalArgumentException>("[Service][ApiKeyNotFound(35)] API key not found.") {
            runBlocking {
                provider.disable(ObjectId.create())
            }
        }
    }

    @Test
    fun callMethodWithLoggedOutUser() {
        runBlocking {
            user.logOut()
            for (method in Method.values()) {
                try {
                    when (method) {
                        Method.CREATE -> provider.create("name")
                        Method.FETCH_SINGLE -> provider.fetch(ObjectId.create())
                        Method.FETCH_ALL -> provider.fetchAll()
                        Method.DELETE -> provider.delete(ObjectId.create())
                        Method.ENABLE -> provider.enable(ObjectId.create())
                        Method.DISABLE -> provider.disable(ObjectId.create())
                    }
                    fail("$method should have thrown an exception")
                } catch (error: AppException) {
                    assertEquals("[Service][Unknown(-1)] expected Authorization header with JWT (Bearer schema).", error.message)
                }
            }
        }
    }

    @Test
    @Ignore("Wait for https://github.com/realm/realm-kotlin/issues/1076 to be resolved before wrapping this up")
    fun callMethodsWithApiKeysDisabled() {
    }

    @Test
    fun getUser() {
        assertEquals(app.currentUser, provider.user)
    }

    @Test
    fun getApp() {
        // Testapp is a pair of <App, AdminApp>, that's why further delving needs to be done to
        // get the actual app
        assertEquals(app.app, provider.app)
    }
}
