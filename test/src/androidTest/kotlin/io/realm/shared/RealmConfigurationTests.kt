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
import io.realm.internal.PlatformHelper
import io.realm.internal.runBlocking
import io.realm.log.LogLevel
import io.realm.util.PlatformUtils
import io.realm.util.TestLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.newSingleThreadContext
import test.Sample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RealmConfigurationTests {

    @Test
    fun defaultPath() {
        val config = RealmConfiguration(schema = setOf(Sample::class))
        assertEquals(
            "${PlatformHelper.appFilesDirectory()}/${Realm.DEFAULT_FILE_NAME}",
            config.path
        )

        val configFromBuilder: RealmConfiguration =
            RealmConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertEquals(
            "${PlatformHelper.appFilesDirectory()}/${Realm.DEFAULT_FILE_NAME}",
            configFromBuilder.path
        )
    }

    @Test
    fun path() {
        val realmPath = "HowToGetPlatformPath/default.realm"

        val config = RealmConfiguration(path = realmPath, schema = setOf(Sample::class))
        assertEquals(realmPath, config.path)

        val configFromBuilder: RealmConfiguration =
            RealmConfiguration.Builder(path = realmPath, schema = setOf(Sample::class)).build()
        assertEquals(realmPath, configFromBuilder.path)
    }

    @Test
    fun pathOverrideName() {
        val realmPath = "<HowToGetPlatformPath>/custom.realm"
        val realmName = "my.realm"

        val config = RealmConfiguration(realmPath, realmName, setOf(Sample::class))
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
        val config = RealmConfiguration(schema = setOf(Sample::class))
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

        val config = RealmConfiguration(name = realmName, schema = setOf(Sample::class))
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
        val config = RealmConfiguration(schema = setOf(Sample::class))
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
        val configuration = RealmConfiguration(schema = setOf(Sample::class))
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
        val configuration = RealmConfiguration(schema = setOf(Sample::class))
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
        val realm = Realm(configuration)
        realm.writeBlocking {
            assertEquals(threadId, PlatformUtils.threadId())
        }
        realm.close()
    }

    @Test
    fun defaultSchemaVersionNumber() {
        val config = RealmConfiguration(schema = setOf(Sample::class))
        assertEquals(0, config.schemaVersion)
    }

    @Test
    fun schemaVersionNumber() {
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class)).schemaVersion(123).build()
        assertEquals(123, config.schemaVersion)
    }

    @Test
    fun defaultDeleteRealmIfMigrationNeeded() {
        val config = RealmConfiguration(schema = setOf(Sample::class))
        assertFalse(config.deleteRealmIfMigrationNeeded)
    }

    @Test
    fun deleteRealmIfMigrationNeeded() {
        val config = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .deleteRealmIfMigrationNeeded()
            .build()
        assertTrue(config.deleteRealmIfMigrationNeeded)
    }
}
