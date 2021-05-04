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
package io.realm

import io.realm.util.PlatformUtils
import test.link.Child
import test.link.Parent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MutableRealmTests {

    private lateinit var configuration: RealmConfiguration
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration(path = "$tmpDir/default.realm", schema = setOf(Parent::class, Child::class))
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun cancelingWrite() {
        assertEquals(0, realm.objects(Parent::class).count())
        realm.writeBlocking {
            copyToRealm(Parent())
            cancelWrite()
        }
        assertEquals(0, realm.objects(Parent::class).count())
    }

    @Test
    fun cancellingWriteTwiceThrows() {
        realm.writeBlocking {
            cancelWrite()
            // FIXME Should be IllegalStateException
            assertFailsWith<RuntimeException> {
                cancelWrite()
            }
        }
    }

    @Test
    fun closingRealmInsideWriteCancelsWrite() {
        realm.writeBlocking {
            copyToRealm(Parent())
            realm.close()
        }
        assertTrue(realm.isClosed())
        realm = Realm.open(configuration)
        assertEquals(0, realm.objects(Parent::class).size)
    }
}
