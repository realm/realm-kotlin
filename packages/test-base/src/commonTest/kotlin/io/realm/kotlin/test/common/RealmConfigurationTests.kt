@file:Suppress("invisible_member", "invisible_reference")
@file:OptIn(ExperimentalCoroutinesApi::class)

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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm.kotlin.test.common

import io.realm.kotlin.Configuration
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.internal.InternalConfiguration
import io.realm.kotlin.internal.platform.PATH_SEPARATOR
import io.realm.kotlin.internal.platform.appFilesDirectory
import io.realm.kotlin.internal.platform.pathOf
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.internal.util.CoroutineDispatcherFactory
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.test.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.platform.platformFileSystem
import io.realm.kotlin.test.util.use
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealmConfigurationTests {

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
    fun realmConfigurationAsConfiguration() {
        val configFromBuilder: RealmConfiguration =
            RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        val configFromBuilderAsRealmConfig: Configuration = configFromBuilder

        val configFromWith: RealmConfiguration = RealmConfiguration.create(schema = setOf(Sample::class))
        val configFromWithAsRealmConfig: Configuration = configFromWith
    }

    @Test
    fun with() {
        val config = RealmConfiguration.create(schema = setOf(Sample::class))
        assertEquals(
            pathOf(appFilesDirectory(), Realm.DEFAULT_FILE_NAME),
            config.path
        )
        assertEquals(Realm.DEFAULT_FILE_NAME, config.name)
        assertEquals(setOf(Sample::class), config.schema)
    }

    @Test
    fun schemaInExternalVariable() {
        val schema = setOf(Sample::class)
        assertIs<RealmConfiguration>(RealmConfiguration.create(schema = schema))
        assertIs<RealmConfiguration>(RealmConfiguration.Builder(schema = schema).build())
    }

    @Test
    fun defaultPath() {
        val config = RealmConfiguration.create(schema = setOf(Sample::class))
        assertEquals(
            pathOf(appFilesDirectory(), Realm.DEFAULT_FILE_NAME),
            config.path
        )

        val configFromBuilderWithDefaultName: RealmConfiguration =
            RealmConfiguration.Builder(schema = setOf(Sample::class))
                .build()
        assertEquals(
            pathOf(appFilesDirectory(), Realm.DEFAULT_FILE_NAME),
            configFromBuilderWithDefaultName.path
        )

        val configFromBuilderWithCustomName: RealmConfiguration =
            RealmConfiguration.Builder(schema = setOf(Sample::class))
                .name("custom.realm")
                .build()
        assertEquals(
            pathOf(appFilesDirectory(), "custom.realm"),
            configFromBuilderWithCustomName.path
        )

        val configFromBuilderWithCurrentDir: RealmConfiguration =
            RealmConfiguration.Builder(schema = setOf(Sample::class))
                .directory(pathOf(".", "my_dir"))
                .name("foo.realm")
                .build()
        assertEquals(
            pathOf(appFilesDirectory(), "my_dir", "foo.realm"),
            configFromBuilderWithCurrentDir.path
        )
    }

    @Test
    fun directory() {
        val realmDir = tmpDir
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(realmDir)
            .build()
        assertEquals(pathOf(tmpDir, Realm.DEFAULT_FILE_NAME), config.path)
    }

    @Test
    fun directory_withSpace() {
        val realmDir = pathOf(tmpDir, "dir with space")
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(realmDir)
            .build()
        assertEquals(pathOf(realmDir, Realm.DEFAULT_FILE_NAME), config.path)
        // Just verifying that we can open the realm
        Realm.open(config).use { }
    }

    @Test
    fun directory_endsWithSeparator() {
        val realmDir = appFilesDirectory() + PATH_SEPARATOR
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(realmDir)
            .build()
        assertEquals("$realmDir${Realm.DEFAULT_FILE_NAME}", config.path)
    }

    @Test
    fun directory_createIntermediateDirs() {
        val realmDir = pathOf(tmpDir, "my", "intermediate", "dir")
        val configBuilder = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(realmDir)

        // Building the config is what creates the folders
        configBuilder.build()
    }

    @Test
    fun directory_isFileThrows() {
        val tmpFile = pathOf(tmpDir, "file")
        platformFileSystem.write(tmpFile.toPath(), mustCreate = true) {
            write(ByteArray(0))
        }

        val configBuilder = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpFile)
            .name("file.realm")

        assertFailsWithMessage<IllegalArgumentException>("Provided directory is a file") {
            configBuilder.build()
        }
    }

    @Test
    fun directoryAndNameCombine() {
        val realmDir = tmpDir
        val realmName = "my.realm"
        val expectedPath = pathOf(realmDir, realmName)

        val config =
            RealmConfiguration.Builder(setOf(Sample::class))
                .directory(realmDir)
                .name(realmName)
                .build()
        assertEquals(expectedPath, config.path)
        assertEquals(realmName, config.name)
    }

    @Test
    fun defaultName() {
        val config = RealmConfiguration.create(schema = setOf(Sample::class))
        assertEquals(Realm.DEFAULT_FILE_NAME, config.name)
        assertTrue(config.path.endsWith(Realm.DEFAULT_FILE_NAME))

        val configFromBuilder: RealmConfiguration =
            RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertEquals(Realm.DEFAULT_FILE_NAME, configFromBuilder.name)
        assertTrue(configFromBuilder.path.endsWith(Realm.DEFAULT_FILE_NAME))
    }

    @Test
    fun name() {
        val realmName = "my.realm"
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class)).name(realmName).build()
        assertEquals(realmName, config.name)
        assertTrue(config.path.endsWith(realmName))
    }

    @Test
    fun name_startsWithSeparator() {
        val realmDir = tmpDir
        val builder = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(realmDir)
        assertFailsWithMessage<IllegalArgumentException>(
            "Name cannot contain path separator"
        ) {
            builder.name("${PATH_SEPARATOR}foo.realm")
        }
    }

    @Test
    fun name_withSpace() {
        val name = "name with space.realm"
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .name(name)
            .build()
        assertEquals(pathOf(tmpDir, name), config.path)
        // Just verifying that we can open the realm
        Realm.open(config).use { }
    }

    @Test
    fun defaultMaxNumberOfActiveVersions() {
        val config = RealmConfiguration.create(schema = setOf(Sample::class))
        assertEquals(Long.MAX_VALUE, config.maxNumberOfActiveVersions)
    }

    @Test
    fun maxNumberOfActiveVersions() {
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .maxNumberOfActiveVersions(42)
            .build()
        assertEquals(42, config.maxNumberOfActiveVersions)
    }

    @Test
    fun maxNumberOfActiveVersionsThrowsIfZeroOrNegative() {
        val builder = RealmConfiguration.Builder(schema = setOf(Sample::class))
        assertFailsWith<IllegalArgumentException> { builder.maxNumberOfActiveVersions(0) }
        assertFailsWith<IllegalArgumentException> { builder.maxNumberOfActiveVersions(-1) }
    }

    @Test
    fun notificationDispatcherRealmConfigurationDefault() {
        val configuration = RealmConfiguration.create(schema = setOf(Sample::class))
        assertIs<CoroutineDispatcherFactory>((configuration as InternalConfiguration).notificationDispatcherFactory)
    }

    @Test
    fun notificationDispatcherRealmConfigurationBuilderDefault() {
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertIs<CoroutineDispatcherFactory>((configuration as InternalConfiguration).notificationDispatcherFactory)
    }

    @Test
    @Suppress("invisible_member")
    @DelicateCoroutinesApi
    fun notificationDispatcherRealmConfigurationBuilder() {
        val dispatcher = newSingleThreadContext("ConfigurationTest")
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .notificationDispatcher(dispatcher).build()
        assertTrue { dispatcher === (configuration as InternalConfiguration).notificationDispatcherFactory.create().dispatcher }
    }

    @Test
    fun writeDispatcherRealmConfigurationDefault() {
        val configuration = RealmConfiguration.create(schema = setOf(Sample::class))
        assertIs<CoroutineDispatcher>((configuration as InternalConfiguration).writeDispatcherFactory.create().dispatcher)
    }

    @Test
    fun writeDispatcherRealmConfigurationBuilderDefault() {
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertIs<CoroutineDispatcher>((configuration as InternalConfiguration).writeDispatcherFactory.create().dispatcher)
    }

    @Test
    @Suppress("invisible_member")
    @DelicateCoroutinesApi
    fun writeDispatcherRealmConfigurationBuilder() {
        val dispatcher = newSingleThreadContext("ConfigurationTest")
        val configuration =
            RealmConfiguration.Builder(schema = setOf(Sample::class)).writeDispatcher(dispatcher)
                .build()
        assertTrue { dispatcher === (configuration as InternalConfiguration).writeDispatcherFactory.create().dispatcher }
    }

    @Test
    @Suppress("invisible_member")
    @DelicateCoroutinesApi
    fun writesExecutesOnWriteDispatcher() {
        val dispatcher = newSingleThreadContext("ConfigurationTest")
        val configuration =
            RealmConfiguration.Builder(schema = setOf(Sample::class))
                .writeDispatcher(dispatcher)
                .directory(tmpDir)
                .build()
        val threadId: ULong =
            runBlocking((configuration as InternalConfiguration).writeDispatcherFactory.create().dispatcher) { PlatformUtils.threadId() }
        Realm.open(configuration).use { realm: Realm ->
            realm.writeBlocking {
                assertEquals(threadId, PlatformUtils.threadId())
            }
        }
    }

    @Test
    fun defaultSchemaVersionNumber() {
        val config = RealmConfiguration.create(schema = setOf(Sample::class))
        assertEquals(0, config.schemaVersion)
    }

    @Test
    fun schemaVersionNumber() {
        val config =
            RealmConfiguration.Builder(schema = setOf(Sample::class)).schemaVersion(123).build()
        assertEquals(123, config.schemaVersion)
    }

    @Test
    fun defaultDeleteRealmIfMigrationNeeded() {
        val config = RealmConfiguration.create(schema = setOf(Sample::class))
        assertFalse(config.deleteRealmIfMigrationNeeded)
    }

    @Test
    fun deleteRealmIfMigrationNeeded() {
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .deleteRealmIfMigrationNeeded()
            .build()
        assertTrue(config.deleteRealmIfMigrationNeeded)
    }

    @Test
    fun migration() {
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .migration(AutomaticSchemaMigration { })
            .build()
        // There is not really anything we can test, so basically just validating that we can call
        // .migrate(...)
        assertNotNull(config)
    }

    @Test
    fun defaultEncryptionKey() {
        val config = RealmConfiguration.create(schema = setOf(Sample::class))
        assertNull(config.encryptionKey)
    }

    @Test
    fun encryptionKey() {
        val encryptionKey = Random.nextBytes(Realm.ENCRYPTION_KEY_LENGTH)

        val config = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .encryptionKey(encryptionKey)
            .build()

        // Validate that the key stored in core is the same that the one we provided
        assertContentEquals(encryptionKey, config.encryptionKey)
    }

    @Test
    fun durability() {
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .build()
        val inMemoryConfig = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .inMemory()
            .build()
        assertFalse(config.inMemory)
        assertTrue(inMemoryConfig.inMemory)
    }

    @Test
    fun wrongEncryptionKeyThrowsIllegalArgumentException() {
        val builder = RealmConfiguration.Builder(schema = setOf(Sample::class))

        assertFailsWithEncryptionKey(builder, 3)
        assertFailsWithEncryptionKey(builder, 8)
        assertFailsWithEncryptionKey(builder, 32)
        assertFailsWithEncryptionKey(builder, 128)
        assertFailsWithEncryptionKey(builder, 256)
    }

    @Test
    fun assetFile_defaultIsNull() {
        val builder = RealmConfiguration.Builder(setOf(Sample::class))
        val config = builder.build()
        assertNull(config.initialRealmFileConfiguration)
    }

    @Test
    fun assetFile_roundTrip() {
        RealmConfiguration.Builder(setOf(Sample::class))
            .initialRealmFile("FILENAME", "SHA256")
            .build()
            .initialRealmFileConfiguration!!
            .run {
                assertEquals("FILENAME", assetFile)
                assertEquals("SHA256", checksum)
            }
    }

    @Test
    fun assetFile_throwsOnEmptyFilename() {
        val builder = RealmConfiguration.Builder(setOf(Sample::class))
        assertFailsWithMessage<IllegalArgumentException>("Asset file must be a non-empty filename.") {
            builder.initialRealmFile("")
        }
    }

    @Test
    fun assetFile_throwsIfDeleteRealmIfMigrationNeeded() {
        val builder = RealmConfiguration.Builder(setOf(Sample::class))
            .initialRealmFile("ASSETFILE")
            .deleteRealmIfMigrationNeeded()
        assertFailsWithMessage<IllegalStateException>("Cannot combine `initialRealmFile` and `deleteRealmIfMigrationNeeded` configuration options") {
            builder.build()
        }
    }

    @Test
    fun assetFile_throwsIfInMemory() {
        val builder = RealmConfiguration.Builder(setOf(Sample::class))
            .initialRealmFile("ASSETFILE")
            .inMemory()
        assertFailsWithMessage<IllegalStateException>("Cannot combine `initialRealmFile` and `inMemory` configuration options") {
            builder.build()
        }
    }

    @Test
    fun logLevelDoesNotGetOverwrittenByConfig() {
        val expectedLogLevel = LogLevel.ALL
        RealmLog.setLevel(expectedLogLevel)

        assertEquals(expectedLogLevel, RealmLog.getLevel())

        RealmConfiguration.Builder(setOf(Sample::class))
            .build()

        assertEquals(expectedLogLevel, RealmLog.getLevel())

        RealmLog.reset()
    }

    private fun assertFailsWithEncryptionKey(builder: RealmConfiguration.Builder, keyLength: Int) {
        val key = Random.nextBytes(keyLength)
        assertFailsWith(
            IllegalArgumentException::class,
            "Encryption key with length $keyLength should not be valid"
        ) {
            builder.encryptionKey(key)
        }
    }
}
