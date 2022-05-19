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

package io.realm.test.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.Sample
import io.realm.test.platform.PlatformUtils
import io.realm.test.platform.platformFileSystem
import io.realm.test.util.use
import kotlinx.atomicfu.atomic
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompactOnLaunchTests {

    private lateinit var tmpDir: String
    private lateinit var configBuilder: RealmConfiguration.Builder

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configBuilder = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun compactOnLaunch_defaultConfiguration() {
        val defaultConfig = RealmConfiguration.with(schema = setOf())
        assertNull(defaultConfig.compactOnLaunchCallback)
    }

    @Test
    fun compactOnLaunch_defaultWhenEnabled() {
        val config = RealmConfiguration.Builder(setOf())
            .compactOnLaunch()
            .build()
        assertEquals(Realm.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK, config.compactOnLaunchCallback)
    }

    @Test
    fun defaultCallback_boundaries() {
        val callback = Realm.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK
        assertFalse(callback.shouldCompact(50 * 1024 * 1024, 40 * 1024 * 1024))
        assertFalse(callback.shouldCompact(50 * 1024 * 1024 + 8, 25 * 1024 * 1024))
        assertFalse(callback.shouldCompact(50 * 1024 * 1024 + 8, 25 * 1024 * 1024 + 3))
        assertTrue(callback.shouldCompact(50 * 1024 * 1024 + 8, 25 * 1024 * 1024 + 4))
        assertTrue(callback.shouldCompact(50 * 1024 * 1024 + 8, 25 * 1024 * 1024 + 5))
    }

    @Test
    fun compact_emptyRealm() {
        // Compacting a initial empty Realm should do nothing
        var config = configBuilder.compactOnLaunch { _, _ -> false }.build()
        Realm.open(config).close()
        val before: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        val called = atomic(false)
        config = configBuilder
            .compactOnLaunch { _, _ ->
                called.value = true
                true
            }
            .build()
        Realm.open(config).close()
        val after: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        assertTrue(called.value)
        assertTrue(before >= after, "$before >= $after")
    }

    @Test
    fun compact_populatedRealm() {
        // TODO Replace with Binary data once support for ByteArray has been added
        var config = configBuilder.build()
        Realm.open(config).use {
            it.writeBlocking {
                for (i in 0..9999) {
                    copyToRealm(Sample().apply { intField = i })
                }
            }
        }
        val before: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        val called = atomic(false)
        config = configBuilder
            .compactOnLaunch { totalBytes, usedBytes ->
                called.value = true
                assertTrue(totalBytes > usedBytes)
                true
            }
            .build()

        Realm.open(config).close()
        val after: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        assertTrue(called.value)
        assertTrue(before > after, "$before > $after")
    }

    @Test
    fun compact_encryptedRealm() {
        // TODO Replace with Binary data once support for ByteArray has been added
        configBuilder.encryptionKey(Random.nextBytes(ByteArray(64)))

        var config = configBuilder.build()
        Realm.open(config).use {
            it.writeBlocking {
                for (i in 0..9999) {
                    copyToRealm(Sample().apply { intField = i })
                }
            }
        }
        val before: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        config = configBuilder
            .compactOnLaunch { totalBytes, usedBytes ->
                assertTrue(totalBytes > usedBytes)
                true
            }
            .build()

        Realm.open(config).close()
        val after: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        assertTrue(before > after, "$before > $after")
    }

    @Test
    fun compact_throwsInCallback() {
        val config = configBuilder
            .compactOnLaunch { _, _ -> throw IllegalStateException("Boom") }
            .build()

        // TODO We should find a better way to propagate exceptions
        // TODO This throws IllegalArgumentException on JVM but RuntimeException on macOS.
        assertFailsWith<RuntimeException> { Realm.open(config) }
    }
}
