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

package io.realm.kotlin.test.shared.dynamic

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.internal.asDynamicRealm
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DynamicRealmTests {
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(schema = setOf(Sample::class))
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

    // TODO Add test for all BaseRealm methods

    // Tested as part of RealmMigrationTests.migrationContext_schemaVerification
    // fun schema() { }

    @Test
    fun query_smokeTest() {
        realm.writeBlocking {
            for (i in 0..9) {
                copyToRealm(Sample().apply { intField = i % 2 })
            }
        }
        val dynamicRealm = realm.asDynamicRealm()
        val result = dynamicRealm.query("Sample", "intField = $0", 0).find()
        assertEquals(5, result.size)
        result.forEach { sample ->
            assertEquals(0L, sample.getValue("intField"))
        }
    }

    @Test
    fun query_unknownNameThrows() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        val dynamicRealm = realm.asDynamicRealm()
        assertFailsWithMessage<IllegalArgumentException>("Schema does not contain a class named 'UNKNOWN_CLASS'") {
            dynamicRealm.query("UNKNOWN_CLASS")
        }
    }
}
