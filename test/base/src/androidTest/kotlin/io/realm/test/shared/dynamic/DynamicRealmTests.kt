@file:Suppress("invisible_member", "invisible_reference")
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

package io.realm.test.shared.dynamic

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.Sample
import io.realm.internal.asDynamicRealm
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DynamicRealmTests {
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(schema = setOf(Sample::class))
                .path("$tmpDir/default.realm").build()

        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // Tested as part of RealmMigrationTests.schema
    // fun schema() { }

    @Test
    fun query_unknownNameThrows() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        assertFailsWith<IllegalArgumentException> {
            dynamicRealm.query("UNKNOWN_CLASS")
        }.run {
            assertEquals("Schema does not contain a class named 'UNKNOWN_CLASS'", message)
        }
    }
}
