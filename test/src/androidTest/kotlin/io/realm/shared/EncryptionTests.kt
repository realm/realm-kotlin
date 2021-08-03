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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm.shared

import io.realm.RealmConfiguration
import io.realm.Realm
import io.realm.util.PlatformUtils
import test.Sample
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertFails

class EncryptionTests {
    private lateinit var tmpDir: String

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun openEncryptedRealm() {
        val key = Random.nextBytes(64)
        val encryptedConf = RealmConfiguration
            .Builder(
                path = "$tmpDir/default.realm",
                schema = setOf(Sample::class)
            )
            .encryptionKey(key)
            .build()

        // Initializes an encrypted Realm
        Realm(encryptedConf).close()

        // Should be possible to reopen an encrypted Realm
        Realm(encryptedConf).close()
    }

    @Test
    fun openEncryptedRealmWithWrongKey() {
        val key = Random.nextBytes(64)
        val encryptedConf = RealmConfiguration
            .Builder(
                path = "$tmpDir/default.realm",
                schema = setOf(Sample::class)
            )
            .encryptionKey(key)
            .build()

        // Initializes an encrypted Realm
        Realm(encryptedConf).close()

        assertFails {
            val unencryptedConf = RealmConfiguration.Builder(schema = setOf(Sample::class))
                .path("$tmpDir/default.realm")
                .build()

            Realm(unencryptedConf)
        }

    }
}
