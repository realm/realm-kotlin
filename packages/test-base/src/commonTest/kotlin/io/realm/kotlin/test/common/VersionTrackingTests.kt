/*
 * Copyright 2023 Realm Inc.
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

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.SampleWithPrimaryKey
import io.realm.kotlin.entities.StringPropertyWithPrimaryKey
import io.realm.kotlin.entities.embedded.embeddedSchema
import io.realm.kotlin.entities.link.Child
import io.realm.kotlin.entities.link.Parent
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.RealmImpl
import io.realm.kotlin.internal.VersionInfo
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.notifications.RealmChange
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestChannel
import io.realm.kotlin.test.util.receiveOrFail
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionTrackingTests {
    private lateinit var initialLogLevel: LogLevel
    private lateinit var configuration: RealmConfiguration
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        initialLogLevel = RealmLog.getLevel()
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(
            schema = setOf(
                Parent::class,
                Child::class,
                StringPropertyWithPrimaryKey::class,
                Sample::class,
                SampleWithPrimaryKey::class
            ) + embeddedSchema
        ).directory(tmpDir).build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
        RealmLog.setLevel(initialLogLevel)
    }

    @Test
    fun open() = runBlocking {
        realm.activeVersions().run {
            assertEquals(1, all.size)
            assertEquals(1, allTracked.size)
            // The notifier might or might not had time to run
            notifier?.let {
                assertEquals(2, it.current?.version)
                assertEquals(0, it.active.size)
            }
            assertNull(writer)
        }
    }

    @Test
    fun write_voidBlockIsNotTracked() = runBlocking {
        realm.activeVersions().run {
            assertEquals(1, all.size)
            assertEquals(1, allTracked.size)
            assertNull(writer)
        }

        // Write that doesn't return objects does not trigger tracking additional versions
        realm.write<Unit> { copyToRealm(Sample()) }
        realm.activeVersions().run {
            assertTrue(1 >= allTracked.size, toString())
            assertNotNull(writer, toString())
            assertEquals(0, writer?.active?.size, toString())
        }

        // Until we actually query the object
        realm.query<Sample>().find()
        realm.activeVersions().run {
            assertTrue(2 >= allTracked.size, toString())
            assertNotNull(writer, toString())
            assertEquals(1, writer?.active?.size, toString())
        }
    }

    @Test
    fun write_returnedObjectIsTracked() = runBlocking {
        realm.activeVersions().run {
            assertEquals(1, all.size)
            assertEquals(1, allTracked.size)
            assertNull(writer)
        }

        // Or if we immediately return the frozen object instance (object is returned even though
        // not assigned to a variable unless the generic return type is <Unit>)
        realm.write { copyToRealm(Sample()) }
        realm.activeVersions().run {
            assertTrue(2 >= allTracked.size, toString())
            assertNotNull(writer, toString())
            assertEquals(1, writer?.active?.size, toString())
        }
    }

    @Test
    fun realmAsFlow_doesNotTrackVersions() = runBlocking {
        realm.activeVersions().run {
            assertEquals(1, all.size)
            assertEquals(1, allTracked.size)
            assertNull(writer)
        }

        // Listening to overall global changes doesn't increase tracked version but will initialize
        // the notifier
        val realmEvents = mutableListOf<RealmChange<*>>()
        val listener = realm.asFlow().onEach { realmEvents.add(it) }.launchIn(GlobalScope)
        realm.write<Unit> { copyToRealm(Sample()) }
        realm.write<Unit> { copyToRealm(Sample()) }
        realm.write<Unit> { copyToRealm(Sample()) }
        realm.activeVersions().run {
            // Initially tracked version from user facing realm might have been released by now
            assertTrue(allTracked.size <= 1, toString())
            assertNotNull(notifier, toString())
            assertEquals(0, notifier?.active?.size, toString())
            assertNotNull(writer, toString())
            assertEquals(0, writer?.active?.size, toString())
        }
        listener.cancel()
    }

    @Test
    fun objectNotificationsCausesTracking() = runBlocking {
        realm.activeVersions().run {
            assertEquals(1, all.size)
            assertEquals(1, allTracked.size)
            assertNull(writer)
        }

        // Listening to object causes tracking of all versions even if not returned by the write
        val samples = mutableListOf<ResultsChange<Sample>>()
        val channel = TestChannel<ResultsChange<Sample>>()
        val initialVersion = realm.version().version
        val writes = 5
        val objectListener = async {
            realm.query<Sample>().asFlow().collect {
                channel.send(it)
            }
        }

        var result = channel.receive()
        samples.add(result)
        while (result.list.version().version < initialVersion + writes) {
            realm.write<Unit> { copyToRealm(Sample()) }
            result = channel.receive()
            samples.add(result)
        }
        objectListener.cancel()
        realm.activeVersions().run {
            assertEquals(writes + 1, allTracked.size, toString())
            assertNotNull(notifier, toString())
            assertEquals(writes + 1, notifier?.active?.size, toString())
            assertNotNull(writer, toString())
            assertEquals(0, writer?.active?.size, toString())
        }

        // Canceling listen will stop tracking versions
        objectListener.cancel()
        realm.write<Unit> { copyToRealm(Sample()) }
        realm.write<Unit> { copyToRealm(Sample()) }
        realm.write<Unit> { copyToRealm(Sample()) }
        realm.activeVersions().run {
            assertEquals(writes + 1, allTracked.size, toString())
            assertNotNull(notifier, toString())
            assertEquals(writes + 1, notifier?.active?.size, toString())
            assertNotNull(writer, toString())
            assertEquals(0, writer?.active?.size, toString())
        }
        assertEquals(
            6,
            samples.size,
            samples.map { it.list.version() }.joinToString { it.toString() }
        )
    }

    @Test
    @Suppress("invisible_member", "invisible_reference")
    fun initialVersionDereferencedAfterFirstWrite() {
        (realm as RealmImpl).let { realm ->
            val intermediateVersions = realm.versionTracker.versions()
            assertEquals(1, intermediateVersions.size, intermediateVersions.toString())

            val realmUpdates = TestChannel<Unit>()

            runBlocking {
                val deferred = async {
                    realm.asFlow().collect {
                        realmUpdates.send(Unit)
                    }
                }

                // Wait for the notifier to start
                realmUpdates.receiveOrFail()

                realm.write { }

                // Wait for the notifier to start
                realmUpdates.receiveOrFail()
                assertNull(realm.initialRealmReference.value, toString())
                // Depending on the exact timing, the first version might or might not have been
                // GC'ed. If GC'ed, there are no intermediate versions.
                val trackedVersions = realm.versionTracker.versions()
                assertTrue(1 >= trackedVersions.size, trackedVersions.toString())

                deferred.cancel()
                realmUpdates.close()
            }
        }
    }
}

@Suppress("invisible_member", "invisible_reference")
internal fun Realm.userFacingRealmVersions(): Int = (this as RealmImpl).let { realm ->
    if (realm.initialRealmReference.value != null) 1
    else 0
}

internal fun Realm.activeVersions(): VersionInfo = (this as RealmImpl).activeVersions()
