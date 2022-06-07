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
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.internal.platform.threadId
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.use
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Class testing [io.realm.Configuration.initialDataCallback] functionality.
 */
class InitialDataTests {

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
    fun initialData_defaultConfiguration() {
        val defaultConfig = RealmConfiguration.create(schema = setOf())
        assertNull(defaultConfig.compactOnLaunchCallback)
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun initialData_errorFailsToOpenRealm() {
        val config = configBuilder.initialData {
            throw RuntimeException("Boom!")
        }.build()

        assertFailsWithMessage<RuntimeException>("Boom!") {
            Realm.open(config).use {
                fail()
            }
        }
    }

    @Test
    fun initialData_failureDeletesRealm() {
        @Suppress("TooGenericExceptionThrown")
        val config = configBuilder.initialData {
            assertTrue(fileExists(this.configuration.path))
            throw RuntimeException("Boom!")
        }.build()
        assertFalse(fileExists(config.path))
        assertFailsWithMessage<RuntimeException>("Boom!") {
            Realm.open(config)
        }
        assertFalse(fileExists(config.path))
    }

    @Test
    fun initialData_runOnWriterThread() {
        val startThread: ULong = threadId()
        val initialDataThread: AtomicRef<ULong?> = atomic(null)
        val config = configBuilder.initialData {
            initialDataThread.value = threadId()
        }.build()

        val writerThread: AtomicRef<ULong?> = atomic(null)
        Realm.open(config).use {
            it.writeBlocking {
                writerThread.value = threadId()
                cancelWrite()
            }
        }

        // `initialData` will run on the writer notifier
        assertEquals(writerThread.value, initialDataThread.value)
        assertNotEquals(startThread, initialDataThread.value)
    }

    @Test
    fun initialData_triggersWhenMigrationDeletesFile() {
        val config1 = RealmConfiguration.Builder(schema = setOf(io.realm.kotlin.entities.migration.before.MigrationSample::class))
            .directory(tmpDir)
            .initialData {
                copyToRealm(io.realm.kotlin.entities.migration.before.MigrationSample())
            }
            .build()

        val config2 = RealmConfiguration.Builder(schema = setOf(io.realm.kotlin.entities.migration.after.MigrationSample::class))
            .directory(tmpDir)
            .deleteRealmIfMigrationNeeded()
            .initialData {
                copyToRealm(io.realm.kotlin.entities.migration.after.MigrationSample())
            }
            .build()

        assertEquals(config1.path, config2.path)

        Realm.open(config1).use {
            assertEquals(1, it.query<io.realm.kotlin.entities.migration.before.MigrationSample>().count().find())
        }
        Realm.open(config2).use {
            assertEquals(1, it.query<io.realm.kotlin.entities.migration.after.MigrationSample>().count().find())
        }
    }

    @Test
    fun initialData_triggerWhenFileIsDeleted() {
        val config = configBuilder.initialData {
            copyToRealm(Sample())
        }.build()

        Realm.open(config).use { realm ->
            assertEquals(1, realm.query<Sample>().count().find())
        }
        Realm.deleteRealm(config)
        assertTrue(!fileExists(config.path))
        Realm.open(config).use { realm ->
            assertEquals(1, realm.query<Sample>().count().find())
        }
    }

    @Test
    fun initialData_runOnlyOncePrFile() {
        val config = configBuilder.initialData {
            copyToRealm(Sample())
        }.build()

        Realm.open(config).use { realm ->
            assertEquals(1, realm.query<Sample>().count().find())
        }
        Realm.open(config).use { realm ->
            assertEquals(1, realm.query<Sample>().count().find())
        }
    }
}
