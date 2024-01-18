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
package io.realm.kotlin.test.common

import io.realm.kotlin.EncryptionKeyCallback
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.annotations.ExperimentalEncryptionCallbackApi
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.use
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
                assertFailsWith(IllegalStateException::class, "Failed to open Realm file at path") {
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
                assertFailsWith(IllegalStateException::class, "Failed to open Realm file at path") {
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
                assertFailsWith(IllegalStateException::class, "Failed to open Realm file at path") {
                    Realm.open(conf)
                }
            }
    }

    @OptIn(ExperimentalEncryptionCallbackApi::class)
    @Test
    fun openEncryptedRealmWithEncryptionKeyCallback() = runBlocking {
        val key: ByteArray = Random.nextBytes(64)
        val keyPointer: Long = PlatformUtils.allocateEncryptionKeyOnNativeMemory(key)

        val keyPointerCallbackInvocation = atomic(0)
        val keyPointerReleaseCallbackInvocation = atomic(0)

        val encryptedConf = RealmConfiguration
            .Builder(
                schema = setOf(Sample::class)
            )
            .directory(tmpDir)
            .encryptionKey(object : EncryptionKeyCallback {
                override fun keyPointer(): Long {
                    keyPointerCallbackInvocation.incrementAndGet()
                    return keyPointer
                }

                override fun releaseKey() {
                    keyPointerReleaseCallbackInvocation.incrementAndGet()
                    PlatformUtils.freeEncryptionKeyFromNativeMemory(keyPointer)
                }
            })
            .build()

        // Initializes an encrypted Realm
        Realm.open(encryptedConf).use {
            it.writeBlocking {
                copyToRealm(Sample().apply { stringField = "Foo Bar" })
            }
        }

        assertEquals(3, keyPointerCallbackInvocation.value, "Encryption key pointer should have been invoked 3 times (Frozen Realm, Notifier and Writer Realms)")
        assertEquals(1, keyPointerReleaseCallbackInvocation.value, "Releasing the key should only be invoked once all the 3 Realms have been opened")

        val keyPointer2 = PlatformUtils.allocateEncryptionKeyOnNativeMemory(key)
        val encryptedConf2 = RealmConfiguration
            .Builder(
                schema = setOf(Sample::class)
            )
            .directory(tmpDir)
            .encryptionKey(object : EncryptionKeyCallback {
                override fun keyPointer() = keyPointer2
                override fun releaseKey() = PlatformUtils.freeEncryptionKeyFromNativeMemory(keyPointer2)
            })
            .build()

        Realm.open(encryptedConf2).use {
            val sample: Sample = it.query(Sample::class).find().first()
            assertEquals("Foo Bar", sample.stringField)
        }
    }
}
