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
import io.realm.isManaged
import io.realm.query
import io.realm.query.find
import io.realm.test.platform.PlatformUtils
import io.realm.version
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
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

    private lateinit var configuration: RealmConfiguration

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder().path("$tmpDir/default.realm")
            .schema(setOf(Parent::class, Child::class)).build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
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
            schema = setOf(Parent::class, Child::class)
        ).maxNumberOfActiveVersions(1)
            .path("$tmpDir/default.realm")
            .build()
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
        realm.query<Child>()
            .find { objects ->
                val childFromResult = objects[0]
                assertEquals(name, childFromResult.name)
            }
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
        assertEquals(0, realm.query<Child>().find().size)
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
        assertEquals(1, realm.query<Child>().find().size)

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
        assertEquals(0, realm.query<Child>().find().size)
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
        assertEquals(10, realm.query<Parent>().find().size)
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
        assertEquals(1, realm.query<Parent>().find().size)
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
        assertEquals(0, realm.query<Parent>().find().size)
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
        assertEquals(0, realm.query<Parent>().find().size)
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
        assertEquals(1, realm.query<Parent>().find().size)
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
        assertEquals(2, realm.query<Parent>().find().size)
    }

    @Test
    fun closeClosesAllVersions() {
        runBlocking {
            realm.write { copyToRealm(Parent()) }
        }
        realm.query<Parent>()
            .first()
            .find { parent ->
                assertNotNull(parent)

                runBlocking {
                    realm.write { copyToRealm(Parent()) }
                }
                realm.close()
                assertFailsWith<IllegalStateException> {
                    parent.version()
                }
            }
    }

    @Test
    fun deleteRealm() {
        val fileSystem = FileSystem.SYSTEM
        val testDir = PlatformUtils.createTempDir("test_dir")
        val testDirPath = testDir.toPath()
        println("deleteRealm 1 - before")
        assertTrue(fileSystem.exists(testDirPath))
        println("deleteRealm 1 - after")

        println("deleteRealm 2 - before")
        val configuration = RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .path("$testDir/default.realm")
            .build()
        println("deleteRealm 2 - after")

        val bgThreadReadyChannel = Channel<Unit>(1)
        val readyToCloseChannel = Channel<Unit>(1)
        val closedChannel = Channel<Unit>(1)

        runBlocking {
            println("deleteRealm 3 - before")
            val testRealm = Realm.open(configuration)
            println("deleteRealm 3 - after")

            val deferred = async {
                // Create another Realm to ensure the log files are generated.
                println("deleteRealm 4 - before")
                val anotherRealm = Realm.open(configuration)
                println("deleteRealm 4 - after")

                println("deleteRealm 5 - before")
                bgThreadReadyChannel.send(Unit)
                println("deleteRealm 5 - after")

                println("deleteRealm 6 - before")
                readyToCloseChannel.receive()
                println("deleteRealm 6 - after")

                println("deleteRealm 7 - before")
                anotherRealm.close()
                println("deleteRealm 7 - after")

                println("deleteRealm 8 - before")
                closedChannel.send(Unit)
                println("deleteRealm 8 - after")
            }

            // Waits for background thread opening the same Realm.
            println("deleteRealm 9 - before")
            bgThreadReadyChannel.receive()
            println("deleteRealm 9 - after")

            // Check the realm got created correctly and signal that it can be closed.
            fileSystem.list(testDirPath)
                .also { testDirPathList ->
                    println("deleteRealm 10 - before")
                    assertEquals(4, testDirPathList.size) // db file, .lock, .management, .note
                    println("deleteRealm 10 - after")

                    println("deleteRealm 11 - before")
                    readyToCloseChannel.send(Unit)
                    println("deleteRealm 11 - after")
                }

            println("deleteRealm 12 - before")
            testRealm.close()
            println("deleteRealm 12 - after")

            println("deleteRealm 13 - before")
            closedChannel.receive()
            println("deleteRealm 13 - after")

            // Delete realm now that it's fully closed.
            println("deleteRealm 14 - before")
            Realm.deleteRealm(configuration)
            println("deleteRealm 14 - after")

            // Lock file should never be deleted.
            fileSystem.list(testDirPath)
                .also { testDirPathList ->
                    println("deleteRealm 15 - before")
                    assertEquals(1, testDirPathList.size) // only .lock file remains
                    println("deleteRealm 15 - after")

                    println("deleteRealm 16 - before")
                    assertTrue(fileSystem.exists("${configuration.path}.lock".toPath()))
                    println("deleteRealm 16 - after")
                }

            println("deleteRealm 17 - before")
            deferred.cancel()
            bgThreadReadyChannel.close()
            readyToCloseChannel.close()
            closedChannel.close()
            println("deleteRealm 17 - after")
        }
    }

    @Test
    fun deleteRealm_failures() {
        println("deleteRealm_failures 1 - before")
        val tempDirA = PlatformUtils.createTempDir()
        println("deleteRealm_failures 1 - after")

        println("deleteRealm_failures 2 - before")
        val configA = RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .path("$tempDirA/anotherRealm.realm")
            .build()
        println("deleteRealm_failures 2 - after")

        // Creates a new Realm file.
        println("deleteRealm_failures 3 - before")
        val anotherRealm = Realm.open(configA)
        println("deleteRealm_failures 3 - after")

        // Deleting it without having closed it should fail.
        assertFailsWith<IllegalStateException> {
            println("deleteRealm_failures 4 - before")
            Realm.deleteRealm(configA)
            println("deleteRealm_failures 4 - after")
        }

        // But now that we close it deletion should work.
        println("deleteRealm_failures 5 - before")
        anotherRealm.close()
        println("deleteRealm_failures 5 - after")
        try {
            println("deleteRealm_failures 6 - before")
            Realm.deleteRealm(configA)
            println("deleteRealm_failures 6 - after")
        } catch (e: Exception) {
            fail("Should not reach this.")
        }
    }

    // TODO Cannot verify intermediate versions as they are now spread across user facing, notifier
    //  and writer realms. Tests were anyway ignored, so don't really know what to do with these.
//    @Test
//    // TODO Non deterministic.
//    //  https://github.com/realm/realm-kotlin/issues/486
//    @Ignore
//    fun intermediateVersionsReleaseWhenProgressingRealm() {
//        assertEquals(0, intermediateReferences.value.size)
//        realm.writeBlocking { }
//        assertEquals(1, intermediateReferences.value.size)
//        realm.writeBlocking { }
//        assertEquals(2, intermediateReferences.value.size)
//        realm.writeBlocking { }
//        assertEquals(3, intermediateReferences.value.size)
//
//        // Trigger GC - On native we also need to trigger GC on the background thread that creates
//        // the references
//        runBlocking((realm.configuration as InternalConfiguration).writeDispatcher) {
//            triggerGC()
//        }
//        triggerGC()
//
//        // Close of intermediate version is currently only done when updating the realm after a write
//        realm.writeBlocking { }
//        assertEquals(1, intermediateReferences.value.size)
//    }
//
//    @Test
//    // TODO Investigate why clearing local object variable does not trigger collection of
//    //  reference on Native. Could just be that the GC somehow does not collect this when
//    //  cleared due some thresholds or outcome of GC not being predictable.
//    //  https://github.com/realm/realm-kotlin/issues/486
//    @Ignore
//    fun clearingRealmObjectReleasesRealmReference() {
//        assertEquals(0, intermediateReferences.value.size)
//        // The below code creates the object without returning it from write to show that the
//        // issue is not bound to the freezing inside write, but also happens on the same thread as
//        // the realm is constructed on.
//        realm.writeBlocking { copyToRealm(Parent()); Unit }
//        var parent: Parent? = realm.query<Parent>()
//            .first()
//            .find()
//        assertNotNull(parent)
//        assertEquals(1, intermediateReferences.value.size)
//        realm.writeBlocking { }
//        assertEquals(2, intermediateReferences.value.size)
//        realm.writeBlocking { }
//        assertEquals(3, intermediateReferences.value.size)
//
//        // Trigger GC - On native we also need to trigger GC on the background thread that creates
//        // the references
//        runBlocking((realm.configuration as InternalConfiguration).writeDispatcher) {
//            triggerGC()
//        }
//        triggerGC()
//
//        // Close of intermediate version is currently only done when updating the realm after a write
//        realm.writeBlocking { }
//        // We still have the single intermediate reference as a result of the write itself
//        // and the reference kept alive by the realm object
//        assertEquals(2, intermediateReferences.value.size)
//
//        // Clear reference
//        parent = null
//
//        runBlocking((realm.configuration as InternalConfiguration).writeDispatcher) {
//            triggerGC()
//        }
//        triggerGC()
//
//        realm.writeBlocking { }
//        // Clearing the realm object reference allowed clearing the corresponding reference
//        assertEquals(1, intermediateReferences.value.size)
//    }
//
//    @Suppress("invisible_reference")
//    private val intermediateReferences: AtomicRef<Set<Pair<NativePointer, WeakReference<io.realm.internal.RealmReference>>>>
//        get() {
//            @Suppress("invisible_member")
//            return (realm as io.realm.internal.RealmImpl).intermediateReferences
//        }
}
