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
package io.realm.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.internal.platform.appFilesDirectory
import io.realm.internal.platform.runBlocking
import io.realm.log.LogLevel
import io.realm.test.platform.PlatformUtils
import io.realm.util.TestLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.newSingleThreadContext
import test.Sample
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealmConfigurationTests {

    @Test
    fun defaultConfig() {
        val config = RealmConfiguration.defaultConfig(schema = setOf(Sample::class))
        assertEquals(
            "${appFilesDirectory()}/${Realm.DEFAULT_FILE_NAME}",
            config.path
        )
        assertEquals(Realm.DEFAULT_FILE_NAME, config.name)
        assertEquals(setOf(Sample::class), config.schema)
    }

    @Test
    fun defaultPath() {
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertEquals(
            "${appFilesDirectory()}/${Realm.DEFAULT_FILE_NAME}",
            config.path
        )

        val configFromBuilder: RealmConfiguration =
            RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertEquals(
            "${appFilesDirectory()}/${Realm.DEFAULT_FILE_NAME}",
            configFromBuilder.path
        )
    }

    @Test
    fun path() {
        val realmPath = "HowToGetPlatformPath/default.realm"

        val config = RealmConfiguration.Builder(path = realmPath, schema = setOf(Sample::class)).build()
        assertEquals(realmPath, config.path)

        val configFromBuilder: RealmConfiguration =
            RealmConfiguration.Builder(path = realmPath, schema = setOf(Sample::class)).build()
        assertEquals(realmPath, configFromBuilder.path)
    }

    @Test
    fun pathOverrideName() {
        val realmPath = "<HowToGetPlatformPath>/custom.realm"
        val realmName = "my.realm"

        val config = RealmConfiguration.Builder(realmPath, realmName, setOf(Sample::class)).build()
        assertEquals(realmPath, config.path)
        // Correct assert: assertEquals("custom.realm", config.name)
        assertEquals("my.realm", config.name) // Current result

        val configFromBuilder: RealmConfiguration =
            RealmConfiguration.Builder(realmPath, realmName, setOf(Sample::class)).build()
        assertEquals(realmPath, configFromBuilder.path)
        // Correct assert: assertEquals("custom.realm", configFromBuilder.name)
        assertEquals("my.realm", configFromBuilder.name) // Current result
    }

    @Test
    fun defaultName() {
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
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

        val config = RealmConfiguration.Builder(name = realmName, schema = setOf(Sample::class)).build()
        assertEquals(realmName, config.name)
        assertTrue(config.path.endsWith(realmName))

        val configFromBuilder: RealmConfiguration =
            RealmConfiguration.Builder(name = realmName, schema = setOf(Sample::class)).build()
        assertEquals(realmName, configFromBuilder.name)
        assertTrue(configFromBuilder.path.endsWith(realmName))
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
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
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
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertTrue(configuration.notificationDispatcher is CoroutineDispatcher)
    }

    @Test
    fun notificationDispatcherRealmConfigurationBuilderDefault() {
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertTrue(configuration.notificationDispatcher is CoroutineDispatcher)
    }

    @Test
    fun notificationDispatcherRealmConfigurationBuilder() {
        val dispatcher = newSingleThreadContext("ConfigurationTest")
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class)).notificationDispatcher(dispatcher).build()
        assertTrue { dispatcher === configuration.notificationDispatcher }
    }

    @Test
    fun writeDispatcherRealmConfigurationDefault() {
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertTrue(configuration.writeDispatcher is CoroutineDispatcher)
    }

    @Test
    fun writeDispatcherRealmConfigurationBuilderDefault() {
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertTrue(configuration.writeDispatcher is CoroutineDispatcher)
    }

    @Test
    fun writeDispatcherRealmConfigurationBuilder() {
        val dispatcher = newSingleThreadContext("ConfigurationTest")
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class)).writeDispatcher(dispatcher).build()
        assertTrue { dispatcher === configuration.writeDispatcher }
    }

    @Test
    fun writesExecutesOnWriteDispatcher() {
        val dispatcher = newSingleThreadContext("ConfigurationTest")
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class)).writeDispatcher(dispatcher).build()
        val threadId: ULong = runBlocking(configuration.writeDispatcher) { PlatformUtils.threadId() }
        val realm = Realm.openBlocking(configuration)
        realm.writeBlocking {
            assertEquals(threadId, PlatformUtils.threadId())
        }
        realm.close()
    }

    @Test
    fun defaultSchemaVersionNumber() {
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertEquals(0, config.schemaVersion)
    }

    @Test
    fun schemaVersionNumber() {
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class)).schemaVersion(123).build()
        assertEquals(123, config.schemaVersion)
    }

    @Test
    fun defaultDeleteRealmIfMigrationNeeded() {
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
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
    fun defaultEncryptionKey() {
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
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
        assertFailsWith(IllegalArgumentException::class, "Encryption key with length $keyLength should not be valid") {
            builder.encryptionKey(key)
        }
    }
}
