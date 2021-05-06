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
package io.realm.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.VersionId
import io.realm.util.PlatformUtils
import io.realm.version
import test.link.Child
import test.link.Parent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RealmObjectTests {

    companion object {
        // Expected version after writing Parent to Realm
        private val EXPECTED_VERSION = VersionId(3, 2)
    }

    private lateinit var tmpDir: String
    private lateinit var realm: Realm
    private lateinit var parent: Parent

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration(path = "$tmpDir/default.realm", schema = setOf(Parent::class, Child::class))
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
    fun version() {
        assertEquals(EXPECTED_VERSION, parent.version)
    }

    @Test
    fun versionThrowsIfRealmIsClosed() {
        realm.close()
        assertFailsWith<IllegalStateException> { parent.version }
    }
}
