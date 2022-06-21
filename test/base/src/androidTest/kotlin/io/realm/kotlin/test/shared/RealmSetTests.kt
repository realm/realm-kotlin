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

package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.realmSetOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RealmSetTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(
            schema = setOf(RealmSetContainer::class)
        ).directory(tmpDir).build()
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
    fun test() {
        realm.writeBlocking {
            val container = RealmSetContainer()
            val managedContainer = copyToRealm(container)

            val stringSet = managedContainer.stringSetField

            assertEquals(0, stringSet.size)
            assertTrue(stringSet.isEmpty())

            assertTrue(stringSet.add("A"))
            assertFalse(stringSet.add("A"))
            assertEquals(1, stringSet.size)
            assertFalse(stringSet.isEmpty())
        }
    }
}

class RealmSetContainer : RealmObject {

    var stringField: String = "Realm"

    var stringSetField: RealmSet<String> = realmSetOf()
}
