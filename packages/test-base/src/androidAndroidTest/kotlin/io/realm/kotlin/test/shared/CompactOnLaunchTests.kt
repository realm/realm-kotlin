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
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.platform.platformFileSystem
import io.realm.kotlin.test.util.use
import kotlinx.atomicfu.atomic
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        val defaultConfig = RealmConfiguration.create(schema = setOf())
        assertNull(defaultConfig.compactOnLaunchCallback)
    }

    @Test
    fun compactOnLaunch_defaultWhenEnabled() {
        val config = RealmConfiguration.Builder(setOf())
            .compactOnLaunch()
            .build()
        assertEquals(Realm.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK, config.compactOnLaunchCallback)
    }

    private val Int.MB get() = (this * 1024 * 1024).toLong()

    private val Int.Bytes get() = (this).toLong()

    @Test
    fun defaultCallback_boundaries() {
        // This callback will only return [True] if the file is above 50 MB and 50% or more of the space can be reclaimed.
        val callback = Realm.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK

        fun assert(shouldCompact: Boolean, fileSize: Long, usage: Double) {
            assertEquals(
                expected = shouldCompact,
                actual = callback.shouldCompact(
                    totalBytes = fileSize,
                    usedBytes = (fileSize * usage).toLong()
                ),
                message = "fileSize: $fileSize usage: $usage"
            )
        }

        // File size <= 50MB, it will never reclaim
        assert(
            shouldCompact = false,
            fileSize = 50.MB,
            usage = 0.49
        )
        assert(
            shouldCompact = false,
            fileSize = 50.MB,
            usage = 0.5
        )
        assert(
            shouldCompact = false,
            fileSize = 50.MB,
            usage = 0.51
        )

        // File size > 50MB, only reclaims if usage < 0.5
        assert(
            shouldCompact = true,
            fileSize = 50.MB + 1.Bytes,
            usage = 0.49
        )
        assert(
            shouldCompact = true,
            fileSize = 50.MB + 1.Bytes,
            usage = 0.5
        )
        assert(
            shouldCompact = false,
            fileSize = 50.MB + 1.Bytes,
            usage = 0.51
        )
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
