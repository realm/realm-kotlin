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

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.util.PlatformUtils
import test.Sample
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * This class contains all the Realm encryption integration tests that validate opening a Realm with an encryption key.
 *
 *  [RealmConfigurationTests] tests how the encryption key is passed to a [RealmConfiguration].
 */
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
        Realm.openBlocking(encryptedConf).close()

        // Should be possible to reopen an encrypted Realm
        Realm.openBlocking(encryptedConf).close()
    }

    @Test
    fun openEncryptedRealmWithWrongKey() {
        val actualKey = Random.nextBytes(64)

        // Initialize an encrypted Realm
        val encryptedConf = RealmConfiguration
            .Builder(
                path = "$tmpDir/default.realm",
                schema = setOf(Sample::class)
            )
            .encryptionKey(actualKey)
            .build()
        Realm.openBlocking(encryptedConf).close()

        // Assert fails with no encryption key
        assertFailsWith(RuntimeException::class, "Encrypted Realm should not be openable with no encryption key") {
            val conf = RealmConfiguration.Builder(schema = setOf(Sample::class))
                .path("$tmpDir/default.realm")
                .build()
            Realm.openBlocking(conf)
        }

        // Assert fails with wrong encryption key
        val randomKey = Random.nextBytes(64)
        assertFailsWith(RuntimeException::class, "Encrypted Realm should not be openable with a wrong encryption key") {
            val conf = RealmConfiguration.Builder(schema = setOf(Sample::class))
                .path("$tmpDir/default.realm")
                .encryptionKey(randomKey)
                .build()

            Realm.openBlocking(conf)
        }
    }

    @Test
    fun openUnencryptedRealmWithEncryptionKey() {
        // Initialize an unencrypted Realm
        val unencryptedConf = RealmConfiguration
            .Builder(
                path = "$tmpDir/default.realm",
                schema = setOf(Sample::class)
            )
            .build()
        Realm.openBlocking(unencryptedConf).close()

        // Assert fails opening with encryption key
        val randomKey = Random.nextBytes(64)
        assertFailsWith(RuntimeException::class, "Unencrypted Realm should not be openable with an encryption key") {
            val conf = RealmConfiguration.Builder(schema = setOf(Sample::class))
                .path("$tmpDir/default.realm")
                .encryptionKey(randomKey)
                .build()

            Realm.openBlocking(conf)
        }
    }
}
