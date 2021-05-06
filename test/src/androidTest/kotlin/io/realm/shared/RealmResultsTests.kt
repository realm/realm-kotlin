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
import io.realm.RealmResults
import io.realm.VersionId
import io.realm.util.PlatformUtils
import test.link.Child
import test.link.Parent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RealmResultsTests {

    companion object {
        // Initial version of any new typed Realm (due to schema being written)
        private val INITIAL_VERSION = VersionId(2, 1)
    }

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration(path = "$tmpDir/default.realm", schema = setOf(Parent::class, Child::class))
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
    fun version() {
        val results: RealmResults<Parent> = realm.objects(Parent::class)
        assertEquals(INITIAL_VERSION, results.version)
    }

    @Test
    fun versionThrowsIfRealmIsClosed() {
        val results: RealmResults<Parent> = realm.objects(Parent::class)
        realm.close()
        assertFailsWith<IllegalStateException> { results.version }
    }
}