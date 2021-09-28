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
package io.realm.test.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.VersionId
import io.realm.entities.link.Child
import io.realm.entities.link.Parent
import io.realm.isFrozen
import io.realm.isValid
import io.realm.test.RealmStateTest
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.Utils.createRandomString
import io.realm.version
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RealmObjectTests : RealmStateTest {

    companion object {
        // Expected version after writing Parent to Realm
        private val EXPECTED_VERSION = VersionId(3)
    }

    private lateinit var tmpDir: String
    private lateinit var realm: Realm
    private lateinit var parent: Parent

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.with(path = "$tmpDir/${createRandomString(16)}.realm", schema = setOf(Parent::class, Child::class))
        realm = Realm.open(configuration)
        parent = realm.writeBlocking { copyToRealm(Parent()) }
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    override fun version() {
        assertEquals(EXPECTED_VERSION, parent.version())
    }

    override fun version_throwsOnUnmanagedObject() {
        val unmanagedParent = Parent()
        assertFailsWith<IllegalArgumentException> {
            unmanagedParent.version()
        }
    }

    @Test
    override fun version_throwsIfRealmIsClosed() {
        realm.close()
        assertFailsWith<IllegalStateException> { parent.version() }
    }

    @Test
    fun isValid() {
        val unmanagedParent = Parent().apply { _id = "Foo" }
        assertTrue(unmanagedParent.isValid())
        val obj: Parent = realm.writeBlocking { copyToRealm(unmanagedParent) }
        assertTrue(obj.isValid())
        realm.close()
        assertFalse(obj.isValid())
    }

    @Test
    override fun isFrozen() {
        assertTrue { parent.isFrozen() }
        realm.writeBlocking {
            val parent = copyToRealm(Parent().apply { _id = "Foo" })
            assertFalse { parent.isFrozen() }
        }
    }

    @Test
    override fun isFrozen_throwsOnUnmanagedObject() {
        val unmanagedParent = Parent()
        assertFailsWith<IllegalArgumentException> {
            unmanagedParent.isFrozen()
        }
    }

    @Test
    fun observeWhenObjectIsDeleted() {
        // FIXME
    }

    override fun isFrozen_throwsIfRealmIsClosed() {
        realm.close()
        assertFailsWith<IllegalStateException> {
            parent.isFrozen()
        }
    }

    // FIXME RealmObject doesn't actually implement RealmState yet
    @Ignore
    override fun isClosed() {
        TODO("Not yet implemented")
    }
}
