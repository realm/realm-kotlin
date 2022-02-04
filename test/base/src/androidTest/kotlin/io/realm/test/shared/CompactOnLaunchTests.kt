package io.realm.test.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.Sample
import io.realm.test.platform.PlatformUtils
import io.realm.test.platform.platformFileSystem
import io.realm.test.util.use
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
            .path("$tmpDir/default.realm")
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
        val config = RealmConfiguration.Builder()
            .compactOnLaunch()
            .build()
        assertEquals(Realm.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK, config.compactOnLaunchCallback)
    }

    @Test
    fun defaultCallback_boundaries() {
        val callback = Realm.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK
        assertFalse(callback.invoke(50 * 1024 * 1024, 40 * 1024 * 1024))
        assertFalse(callback.invoke(50 * 1024 * 1024 + 8, 25 * 1024 * 1024))
        assertFalse(callback.invoke(50 * 1024 * 1024 + 8, 25 * 1024 * 1024 + 3))
        assertTrue(callback.invoke(50 * 1024 * 1024 + 8, 25 * 1024 * 1024 + 4))
        assertTrue(callback.invoke(50 * 1024 * 1024 + 8, 25 * 1024 * 1024 + 5))
    }

    @Test
    fun compact_emptyRealm() {
        // Compacting a initial empty Realm should do nothing
        var config = configBuilder.compactOnLaunch { _, _ -> false }.build()
        Realm.open(config).close()
        val before: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        config = configBuilder.compactOnLaunch { _, _ -> true }.build()
        Realm.open(config).close()
        val after: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        assertEquals(before, after, "$before == $after")
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
        assertFailsWith<IllegalArgumentException> { Realm.open(config) }
    }
}
