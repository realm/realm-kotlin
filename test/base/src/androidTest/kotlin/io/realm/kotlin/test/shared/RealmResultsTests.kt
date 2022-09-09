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
package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.VersionId
import io.realm.kotlin.entities.link.Child
import io.realm.kotlin.entities.link.Parent
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.find
import io.realm.kotlin.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RealmResultsTests {

    companion object {
        // Initial version of any new typed Realm (due to schema being written)
        private val INITIAL_VERSION = VersionId(2)
    }

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun query() {
        realm.writeBlocking {
            copyToRealm(Parent().apply { name = "1" })
            copyToRealm(Parent().apply { name = "2" })
            copyToRealm(Parent().apply { name = "12" })
        }
        assertEquals(2, realm.query<Parent>("name CONTAINS '1'").find().size)
        assertEquals(2, realm.query<Parent>("name CONTAINS '2'").find().size)
        assertEquals(1, realm.query<Parent>("name CONTAINS '1'").find().query("name CONTAINS '2'").size)
    }

    @Test
    fun version() {
        realm.query<Parent>()
            .find { results ->
                assertEquals(INITIAL_VERSION, results.version())
            }
    }

    @Test
    fun versionThrowsIfRealmIsClosed() {
        realm.query<Parent>()
            .find { results ->
                realm.close()
                assertFailsWith<IllegalStateException> { results.version() }
            }
    }
}
