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
package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.test.platform.PlatformUtils
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * This class contains all the Realm encryption integration tests that validate opening a Realm with an encryption key.
 *
 *  [RealmConfigurationTests] tests how the encryption key is passed to a [Configuration].
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
                schema = setOf(Sample::class)
            )
            .directory(tmpDir)
            .encryptionKey(key)
            .build()

        // Initializes an encrypted Realm
        Realm.open(encryptedConf).close()

        // Should be possible to reopen an encrypted Realm
        Realm.open(encryptedConf).close()
    }

    @Test
    fun openEncryptedRealmWithWrongKey() {
        val actualKey = Random.nextBytes(64)

        // Initialize an encrypted Realm
        val encryptedConf = RealmConfiguration
            .Builder(
                schema = setOf(Sample::class)
            )
            .directory(tmpDir)
            .encryptionKey(actualKey)
            .build()
        Realm.open(encryptedConf).close()

        // Assert fails with no encryption key
        RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
            .let { conf ->
                assertFailsWith(IllegalArgumentException::class, "Encrypted Realm should not be openable with no encryption key") {
                    Realm.open(conf)
                }
            }

        // Assert fails with wrong encryption key
        val randomKey = Random.nextBytes(64)
        RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .encryptionKey(randomKey)
            .build()
            .let { conf ->
                assertFailsWith(IllegalArgumentException::class, "Encrypted Realm should not be openable with a wrong encryption key") {
                    Realm.open(conf)
                }
            }
    }

    @Test
    fun openUnencryptedRealmWithEncryptionKey() {
        // Initialize an unencrypted Realm
        val unencryptedConf = RealmConfiguration
            .Builder(
                schema = setOf(Sample::class)
            )
            .directory(tmpDir)
            .build()
        Realm.open(unencryptedConf).close()

        // Assert fails opening with encryption key
        val randomKey = Random.nextBytes(64)
        RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .encryptionKey(randomKey)
            .build()
            .let { conf ->
                assertFailsWith(IllegalArgumentException::class, "Unencrypted Realm should not be openable with an encryption key") {
                    Realm.open(conf)
                }
            }
    }
}
