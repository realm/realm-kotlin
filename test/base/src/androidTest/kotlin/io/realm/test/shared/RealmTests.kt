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

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.VersionId
import io.realm.entities.link.Child
import io.realm.entities.link.Parent
import io.realm.internal.interop.NativePointer
import io.realm.internal.platform.WeakReference
import io.realm.isManaged
import io.realm.objects
import io.realm.test.platform.PlatformUtils
import io.realm.test.platform.PlatformUtils.triggerGC
import io.realm.test.util.Utils.createRandomString
import io.realm.version
import kotlinx.atomicfu.AtomicRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

@OptIn(ExperimentalTime::class)
class RealmTests {

    companion object {
        // Initial version of any new typed Realm (due to schema being written)
        private val INITIAL_VERSION = VersionId(2)
    }

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private lateinit var  configuration: RealmConfiguration

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder()
            .path("$tmpDir/default.realm")
            .schema(setOf(Parent::class, Child::class))
            .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun initialVersion() {
        assertEquals(INITIAL_VERSION, realm.version())
    }

    @Test
    fun versionIncreaseOnWrite() {
        assertEquals(INITIAL_VERSION, realm.version())
        realm.writeBlocking { /* Do Nothing */ }
        assertEquals(VersionId(3), realm.version())
    }

    @Test
    fun versionDoesNotChangeWhenCancellingWrite() {
        assertEquals(INITIAL_VERSION, realm.version())
        realm.writeBlocking { cancelWrite() }
        assertEquals(INITIAL_VERSION, realm.version())
    }

    @Test
    fun versionThrowsIfRealmIsClosed() {
        realm.close()
        assertFailsWith<IllegalStateException> { realm.version() }
    }

    @Test
    fun versionInsideWriteIsLatest() {
        assertEquals(INITIAL_VERSION, realm.version())
        realm.writeBlocking {
            assertEquals(INITIAL_VERSION, version())
            cancelWrite()
        }
        assertEquals(INITIAL_VERSION, realm.version())
    }

    @Test
    fun numberOfActiveVersions() {
        assertEquals(2, realm.getNumberOfActiveVersions())
        realm.writeBlocking {
            assertEquals(2, getNumberOfActiveVersions())
        }
        assertEquals(2, realm.getNumberOfActiveVersions())
    }

    @Test
    @Ignore // FIXME This fails on MacOS only. Are versions cleaned up more aggressively there?
    fun throwsIfMaxNumberOfActiveVersionsAreExceeded() {
        realm.close()
        val config = RealmConfiguration.Builder(
            path = "$tmpDir/exceed-versions.realm",
            schema = setOf(Parent::class, Child::class)
        ).maxNumberOfActiveVersions(1).build()
        realm = Realm.open(config)
        // Pin the version, so when starting a new transaction on the first Realm,
        // we don't release older versions.
        val otherRealm = Realm.open(config)

        try {
            assertFailsWith<IllegalStateException> { realm.writeBlocking { } }
        } finally {
            otherRealm.close()
        }
    }

    @Suppress("invisible_member")
    @Test
    fun write() = runBlocking {
        val name = "Realm"
        val child: Child = realm.write {
            this.copyToRealm(Child()).apply { this.name = name }
        }
        assertEquals(name, child.name)
        val objects = realm.objects<Child>()
        val childFromResult = objects[0]
        assertEquals(name, childFromResult.name)
    }

    @Suppress("invisible_member")
    @Test
    fun exceptionInWriteWillRollback() = runBlocking {
        class CustomException : Exception()

        assertFailsWith<CustomException> {
            realm.write {
                val name = "Realm"
                this.copyToRealm(Child()).apply { this.name = name }
                throw CustomException()
            }
        }
        assertEquals(0, realm.objects<Child>().size)
    }

    @Test
    fun writeBlocking() {
        val managedChild = realm.writeBlocking { copyToRealm(Child().apply { name = "John" }) }
        assertTrue(managedChild.isManaged())
        assertEquals("John", managedChild.name)
    }

    @Suppress("invisible_member")
    @Test
    fun writeBlockingAfterWrite() = runBlocking {
        val name = "Realm"
        val child: Child = realm.write {
            this.copyToRealm(Child()).apply { this.name = name }
        }
        assertEquals(name, child.name)
        assertEquals(1, realm.objects<Child>().size)

        realm.writeBlocking {
            this.copyToRealm(Child()).apply { this.name = name }
        }
        Unit
    }

    @Suppress("invisible_member")
    @Test
    fun exceptionInWriteBlockingWillRollback() {
        class CustomException : Exception()
        assertFailsWith<CustomException> {
            realm.writeBlocking {
                val name = "Realm"
                this.copyToRealm(Child()).apply { this.name = name }
                throw CustomException()
            }
        }
        assertEquals(0, realm.objects<Child>().size)
    }

    @Test
    @Suppress("invisible_member")
    fun simultaneousWritesAreAllExecuted() = runBlocking {
        val jobs: List<Job> = IntRange(0, 9).map {
            launch {
                realm.write {
                    copyToRealm(Parent())
                }
            }
        }
        jobs.map { it.join() }

        // Ensure that all writes are actually committed
        realm.close()
        assertTrue(realm.isClosed())
        realm = Realm.open(configuration)
        assertEquals(10, realm.objects(Parent::class).size)
    }

    @Test
    @Suppress("invisible_member")
    fun writeBlockingWhileWritingIsSerialized() = runBlocking {
        val writeStarted = Mutex(true)
        val writeEnding = Mutex(true)
        val writeBlockingQueued = Mutex(true)
        async {
            realm.write {
                writeStarted.unlock()
                while (writeBlockingQueued.isLocked) {
                    PlatformUtils.sleep(1.milliseconds)
                }
                writeEnding.unlock()
            }
        }
        writeStarted.lock()
        runBlocking {
            val async = async {
                realm.writeBlocking {
                    assertFalse { writeEnding.isLocked }
                }
            }
            writeBlockingQueued.unlock()
            async.await()
        }
    }

    @Test
    @Suppress("invisible_member")
    fun close() = runBlocking {
        realm.write {
            copyToRealm(Parent())
        }
        realm.close()
        assertTrue(realm.isClosed())

        realm = Realm.open(configuration)
        assertEquals(1, realm.objects(Parent::class).size)
    }

    @Test
    @Suppress("invisible_member")
    fun closeCausesOngoingWriteToThrow() = runBlocking {
        val writeStarted = Mutex(true)
        val write = async {
            assertFailsWith<IllegalStateException> {
                realm.write {
                    writeStarted.unlock()
                    copyToRealm(Parent())
                    // realm.close is blocking until write block is done, so we cannot wait on
                    // specific external events, so just sleep a bit :/
                    PlatformUtils.sleep(Duration.Companion.milliseconds(100))
                }
            }
        }
        writeStarted.lock()
        realm.close()
        assert(write.await() is RuntimeException)
        realm = Realm.open(configuration)
        assertEquals(0, realm.objects<Parent>().size)
    }

    @Test
    @Suppress("invisible_member")
    fun writeAfterCloseThrows() = runBlocking {
        realm.close()
        assertTrue(realm.isClosed())
        assertFailsWith<IllegalStateException> {
            realm.write {
                copyToRealm(Child())
            }
        }
        Unit
    }

    @Test
    @Suppress("invisible_member")
    fun coroutineCancelCausesRollback() = runBlocking {
        val mutex = Mutex(true)
        val job = async {
            realm.write {
                copyToRealm(Parent())
                mutex.unlock()
                // Ensure that we keep on going until actually cancelled
                while (isActive) {
                    PlatformUtils.sleep(1.milliseconds)
                }
            }
        }
        mutex.lock()
        job.cancelAndJoin()

        // Ensure that write is not committed
        realm.close()
        assertTrue(realm.isClosed())
        realm = Realm.open(configuration)
        // This assertion doesn't hold on MacOS as all code executes on the same thread as the
        // dispatcher is a run loop on the local thread, thus, the main flow is not picked up when
        // the mutex is unlocked. Doing so would require the write block to be able to suspend in
        // some way (or the writer to be backed by another thread).
        assertEquals(0, realm.objects(Parent::class).size)
    }

    @Test
    @Suppress("invisible_member")
    fun writeAfterCoroutineCancel() = runBlocking {
        val mutex = Mutex(true)
        val job = async {
            realm.write {
                copyToRealm(Parent())
                mutex.unlock()
                // Ensure that we keep on going until actually cancelled
                while (isActive) {
                    PlatformUtils.sleep(1.milliseconds)
                }
            }
        }

        mutex.lock()
        job.cancelAndJoin()

        // Verify that we can do other writes after cancel
        realm.write {
            copyToRealm(Parent())
        }

        // Ensure that only one write is actually committed
        realm.close()
        assertTrue(realm.isClosed())
        realm = Realm.open(configuration)
        // This assertion doesn't hold on MacOS as all code executes on the same thread as the
        // dispatcher is a run loop on the local thread, thus, the main flow is not picked up when
        // the mutex is unlocked. Doing so would require the write block to be able to suspend in
        // some way (or the writer to be backed by another thread).
        assertEquals(1, realm.objects(Parent::class).size)
    }

    @Test
    @Suppress("invisible_member")
    fun writesOnFrozenRealm() {
        val dispatcher = newSingleThreadContext("background")
        runBlocking {
            realm.write {
                copyToRealm(Parent())
            }
        }
        runBlocking(dispatcher) {
            realm.write {
                copyToRealm(Parent())
            }
        }
        assertEquals(2, realm.objects<Parent>().size)
    }

    @Test
    fun closeClosesAllVersions() {
        runBlocking {
            realm.write { copyToRealm(Parent()) }
        }
        val parent: Parent = realm.objects<Parent>().first()
        runBlocking {
            realm.write { copyToRealm(Parent()) }
        }
        realm.close()
        assertFailsWith<IllegalStateException> {
            parent.version()
        }
    }

    @Test
    fun closingIntermediateVersionsWhenNoLongerReferenced() {
        assertEquals(0, intermediateReferences.value.size)
        var parent: Parent? = realm.writeBlocking { copyToRealm(Parent()) }
        assertEquals(1, intermediateReferences.value.size)
        realm.writeBlocking { }
        assertEquals(2, intermediateReferences.value.size)

        // Clear reference
        parent = null
        // Trigger GC
        triggerGC()
        // Close of intermediate version is currently only done when updating the realm after a write
        realm.writeBlocking { }
        assertEquals(1, intermediateReferences.value.size)
    }

    @Suppress("invisible_reference")
    private val intermediateReferences: AtomicRef<Set<Pair<NativePointer, WeakReference<io.realm.internal.RealmReference>>>>
        get() {
            @Suppress("invisible_member")
            return (realm as io.realm.internal.RealmImpl).intermediateReferences
        }
}
