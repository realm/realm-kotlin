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
package io.realm.test

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.link.Child
import io.realm.entities.link.Parent
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RealmTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private val configuration: RealmConfiguration by lazy {
        RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .path("$tmpDir/default.realm")
            .build()
    }

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // Not applicable for Native as we cannot access Realm inside write closure without freezing it
    @Test
    @Suppress("invisible_member")
    fun writeBlockingInsideWriteThrows() {
        runBlocking {
            realm.write {
                assertFailsWith<IllegalStateException> {
                    realm.writeBlocking { }
                }
            }
        }
    }

    // Not applicable for Native as we cannot access Realm inside write closure without freezing it
    @Test
    fun writeBlockIngInsideWriteBlockingThrows() {
        realm.writeBlocking {
            assertFailsWith<IllegalStateException> {
                realm.writeBlocking { }
            }
        }
    }

    // Not applicable for Native as we cannot access Realm inside write closure without freezing it
    @Test
    fun closingRealmInsideWriteThrows() {
        runBlocking {
            realm.write {
                assertFailsWith<IllegalStateException> {
                    realm.close()
                }
            }
        }
        assertFalse(realm.isClosed())
    }

    // Not applicable for Native as we cannot access Realm inside write closure without freezing it
    @Test
    fun closingRealmInsideWriteBlockingThrows() {
        realm.writeBlocking {
            assertFailsWith<IllegalStateException> {
                realm.close()
            }
        }
        assertFalse(realm.isClosed())
    }
}
