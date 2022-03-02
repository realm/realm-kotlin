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
package io.realm.test.shared

import io.realm.Configuration
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.Sample
import io.realm.internal.InternalConfiguration
import io.realm.internal.platform.appFilesDirectory
import io.realm.internal.platform.runBlocking
import io.realm.log.LogLevel
import io.realm.migration.AutomaticSchemaMigration
import io.realm.test.assertFailsWithMessage
import io.realm.test.platform.PlatformUtils
import io.realm.test.platform.platformFileSystem
import io.realm.test.util.TestLogger
import io.realm.test.util.use
import kotlinx.coroutines.CoroutineDispatcher
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

        val configFromWith: RealmConfiguration = RealmConfiguration.with(schema = setOf(Sample::class))
        val configFromWithAsRealmConfig: Configuration = configFromWith
    }

    @Test
    fun with() {
        val config = RealmConfiguration.with(schema = setOf(Sample::class))
        assertEquals(
            "${appFilesDirectory()}/${Realm.DEFAULT_FILE_NAME}",
            config.path
        )
        assertEquals(Realm.DEFAULT_FILE_NAME, config.name)
        assertEquals(setOf(Sample::class), config.schema)
    }

    @Test
    fun schemaInExternalVariable() {
        val schema = setOf(Sample::class)
        assertIs<RealmConfiguration>(RealmConfiguration.with(schema = schema))
        assertIs<RealmConfiguration>(RealmConfiguration.Builder(schema = schema).build())
    }

    @Test
    fun defaultPath() {
        val config = RealmConfiguration.with(schema = setOf(Sample::class))
        assertEquals(
            "${appFilesDirectory()}/${Realm.DEFAULT_FILE_NAME}",
            config.path
        )

        val configFromBuilderWithDefaultName: RealmConfiguration =
            RealmConfiguration.Builder(schema = setOf(Sample::class))
                .build()
        assertEquals(
            "${appFilesDirectory()}/${Realm.DEFAULT_FILE_NAME}",
            configFromBuilderWithDefaultName.path
        )

        val configFromBuilderWithCustomName: RealmConfiguration =
            RealmConfiguration.Builder(schema = setOf(Sample::class))
                .name("custom.realm")
                .build()
        assertEquals(
            "${appFilesDirectory()}/custom.realm",
            configFromBuilderWithCustomName.path
        )

        val configFromBuilderWithCurrentDir: RealmConfiguration =
            RealmConfiguration.Builder(schema = setOf(Sample::class))
                .directory("./my_dir")
                .name("foo.realm")
                .build()
        assertEquals(
            "${appFilesDirectory()}/my_dir/foo.realm",
            configFromBuilderWithCurrentDir.path
        )
    }

    @Test
    fun directory() {
        val realmDir = tmpDir
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(realmDir)
            .build()
        assertEquals("$tmpDir/${Realm.DEFAULT_FILE_NAME}", config.path)
    }

    @Test
    fun directory_endsWithSeparator() {
        val realmDir = appFilesDirectory() + "/"
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(realmDir)
            .build()
        assertEquals("$realmDir${Realm.DEFAULT_FILE_NAME}", config.path)
    }

    @Test
    fun directory_createIntermediateDirs() {
        val realmDir = tmpDir + "/my/intermediate/dir"
        val configBuilder = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(realmDir)

        // Building the config is what creates the folders
        configBuilder.build()
    }

    @Test
    fun directory_isFileThrows() {
        val tmpFile = "$tmpDir/file"
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
        val expectedPath = "$realmDir/$realmName"

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
        val config = RealmConfiguration.with(schema = setOf(Sample::class))
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

    // TODO This should probably throw an IllegalArgumentException, for now just document behaviour.
    @Test
    fun name_startsWithSeparetor() {
        val realmDir = tmpDir
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(realmDir)
            .name("/foo.realm")
            .build()
        assertEquals(config.path, "$realmDir/foo.realm")
    }

    @Test
    fun defaultLogLevel() {
        val config: RealmConfiguration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .build()
        assertEquals(LogLevel.WARN, config.log.level)
    }

    @Test
    fun logLevel() {
        val config: RealmConfiguration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .log(LogLevel.NONE)
            .build()
        assertEquals(LogLevel.NONE, config.log.level)
    }

    @Test
    fun defaultCustomLoggers() {
        val config: RealmConfiguration =
            RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertEquals(1, config.log.loggers.size)
    }

    @Test
    fun customLoggers() {
        val logger = TestLogger()
        val config: RealmConfiguration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .log(customLoggers = listOf(logger))
            .build()
        assertEquals(2, config.log.loggers.size)
        assertEquals(logger, config.log.loggers.last())
    }

    @Suppress("invisible_member")
    @Test
    fun removeSystemLogger() {
        val config: RealmConfiguration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .removeSystemLogger()
            .build()
        assertTrue(config.log.loggers.isEmpty())
    }

    @Test
    fun defaultMaxNumberOfActiveVersions() {
        val config = RealmConfiguration.with(schema = setOf(Sample::class))
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
        val configuration = RealmConfiguration.with(schema = setOf(Sample::class))
        assertTrue((configuration as InternalConfiguration).notificationDispatcher is CoroutineDispatcher)
    }

    @Test
    fun notificationDispatcherRealmConfigurationBuilderDefault() {
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertTrue((configuration as InternalConfiguration).notificationDispatcher is CoroutineDispatcher)
    }

    @Test
    @Suppress("invisible_member")
    fun notificationDispatcherRealmConfigurationBuilder() {
        val dispatcher = newSingleThreadContext("ConfigurationTest")
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .notificationDispatcher(dispatcher).build()
        assertTrue { dispatcher === (configuration as InternalConfiguration).notificationDispatcher }
    }

    @Test
    fun writeDispatcherRealmConfigurationDefault() {
        val configuration = RealmConfiguration.with(schema = setOf(Sample::class))
        assertTrue((configuration as InternalConfiguration).writeDispatcher is CoroutineDispatcher)
    }

    @Test
    fun writeDispatcherRealmConfigurationBuilderDefault() {
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertTrue((configuration as InternalConfiguration).writeDispatcher is CoroutineDispatcher)
    }

    @Test
    @Suppress("invisible_member")
    fun writeDispatcherRealmConfigurationBuilder() {
        val dispatcher = newSingleThreadContext("ConfigurationTest")
        val configuration =
            RealmConfiguration.Builder(schema = setOf(Sample::class)).writeDispatcher(dispatcher)
                .build()
        assertTrue { dispatcher === (configuration as InternalConfiguration).writeDispatcher }
    }

    @Test
    @Suppress("invisible_member")
    fun writesExecutesOnWriteDispatcher() {
        val dispatcher = newSingleThreadContext("ConfigurationTest")
        val configuration =
            RealmConfiguration.Builder(schema = setOf(Sample::class))
                .writeDispatcher(dispatcher)
                .directory(tmpDir)
                .build()
        val threadId: ULong =
            runBlocking((configuration as InternalConfiguration).writeDispatcher) { PlatformUtils.threadId() }
        Realm.open(configuration).use { realm: Realm ->
            realm.writeBlocking {
                assertEquals(threadId, PlatformUtils.threadId())
            }
        }
    }

    @Test
    fun defaultSchemaVersionNumber() {
        val config = RealmConfiguration.with(schema = setOf(Sample::class))
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
        val config = RealmConfiguration.with(schema = setOf(Sample::class))
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
        val config = RealmConfiguration.with(schema = setOf(Sample::class))
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
    fun wrongEncryptionKeyThrowsIllegalArgumentException() {
        val builder = RealmConfiguration.Builder(schema = setOf(Sample::class))

        assertFailsWithEncryptionKey(builder, 3)
        assertFailsWithEncryptionKey(builder, 8)
        assertFailsWithEncryptionKey(builder, 32)
        assertFailsWithEncryptionKey(builder, 128)
        assertFailsWithEncryptionKey(builder, 256)
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
