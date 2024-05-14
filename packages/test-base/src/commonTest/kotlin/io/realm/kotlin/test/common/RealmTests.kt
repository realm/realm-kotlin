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
import io.realm.kotlin.VersionId
import io.realm.kotlin.entities.link.Child
import io.realm.kotlin.entities.link.Parent
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.isValid
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.version
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.internal.platform.isWindows
import io.realm.kotlin.internal.platform.pathOf
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.query.find
import io.realm.kotlin.test.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.platform.platformFileSystem
import io.realm.kotlin.test.util.TestChannel
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.test.util.use
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
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
import kotlin.time.Duration.Companion.milliseconds

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
        RealmLog.level = LogLevel.ALL
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .build()
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
            .directory(tmpDir)
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

    @Test
    fun write_returnDeletedObject() = runBlocking {
        val returnValue: Child = realm.write {
            val child = copyToRealm(Child()).apply { this.name = "Realm" }
            child.apply { delete(this) }
        }
        assertFalse(returnValue.isValid())
    }

    @Suppress("invisible_member")
    @Test
    fun exceptionInWriteWillRollback() = runBlocking<Unit> {
        class CustomException : Exception()

        assertFailsWith<CustomException> {
            realm.write {
                val name = "Realm"
                this.copyToRealm(Child()).apply { this.name = name }
                throw CustomException()
            }
        }
        assertEquals(0, realm.query<Child>().find().size)
        // Verify that we can do additional transactions after the previous transaction
        // was rolled back due to the exception
        realm.write {
            this.copyToRealm(Child())
        }
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
    fun writeBlocking_returnDeletedObject() {
        val returnValue: Child = realm.writeBlocking {
            val child = copyToRealm(Child()).apply { this.name = "Realm" }
            child.apply { delete(this) }
        }
        assertFalse(returnValue.isValid())
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
                    PlatformUtils.sleep(100.milliseconds)
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
    fun writeAfterCloseThrows() = runBlocking<Unit> {
        realm.close()
        assertTrue(realm.isClosed())
        assertFailsWith<IllegalStateException> {
            realm.write {
                copyToRealm(Child())
            }
        }
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
    fun close_idempotent() {
        realm.close()
        assertTrue(realm.isClosed())
        realm.close()
        assertTrue(realm.isClosed())
    }

    @Test
    @Suppress("LongMethod")
    fun deleteRealm() {
        val fileSystem = FileSystem.SYSTEM
        val testDir = PlatformUtils.createTempDir()
        val testDirPath = testDir.toPath()
        assertTrue(fileSystem.exists(testDirPath))

        val configuration = RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(testDir)
            .build()

        val bgThreadReadyChannel = TestChannel<Unit>()
        val readyToCloseChannel = TestChannel<Unit>()
        val closedChannel = TestChannel<Unit>()

        runBlocking {
            val testRealm = Realm.open(configuration)

            val deferred = async {
                // Create another Realm to ensure the log files are generated.
                val anotherRealm = Realm.open(configuration)
                bgThreadReadyChannel.send(Unit)

                readyToCloseChannel.receiveOrFail()

                anotherRealm.close()
                closedChannel.send(Unit)
            }

            // Waits for background thread opening the same Realm.
            bgThreadReadyChannel.receiveOrFail()

            // Check the realm got created correctly and signal that it can be closed.
            fileSystem.list(testDirPath)
                .also { testDirPathList ->
                    // We expect the following files: db file, .lock, .management, .note.
                    // On Linux and Mac, the .note is used to control notifications. This mechanism
                    // is not used on Windows, so the file is not present there.
                    val expectedFiles = if (isWindows()) {
                        3
                    } else {
                        4
                    }
                    assertEquals(expectedFiles, testDirPathList.size)
                    readyToCloseChannel.send(Unit)
                }

            testRealm.close()

            closedChannel.receiveOrFail()

            // Delete realm now that it's fully closed.
            Realm.deleteRealm(configuration)

            // Lock file should never be deleted.
            fileSystem.list(testDirPath)
                .also { testDirPathList ->
                    assertEquals(1, testDirPathList.size) // only .lock file remains

                    assertTrue(fileSystem.exists("${configuration.path}.lock".toPath()))
                }

            deferred.cancel()
            bgThreadReadyChannel.close()
            readyToCloseChannel.close()
            closedChannel.close()
        }
    }

    @Test
    fun deleteRealm_fileDoesNotExists() {
        val fileSystem = FileSystem.SYSTEM
        val testDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(testDir)
            .build()
        assertFalse(fileSystem.exists(configuration.path.toPath()))
        Realm.deleteRealm(configuration) // No-op if file doesn't exists
        assertFalse(fileSystem.exists(configuration.path.toPath()))
    }

    @Test
    fun deleteRealm_failures() {
        val tempDirA = PlatformUtils.createTempDir()

        val configA = RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(tempDirA)
            .name("anotherRealm.realm")
            .build()

        // Creates a new Realm file.
        val anotherRealm = Realm.open(configA)

        // Deleting it without having closed it should fail.
        assertFailsWithMessage<IllegalStateException>("Cannot delete files of an open Realm: '${pathOf(tempDirA, "anotherRealm.realm")}' is still in use") {
            Realm.deleteRealm(configA)
        }

        // But now that we close it deletion should work.
        anotherRealm.close()
        try {
            Realm.deleteRealm(configA)
        } catch (e: Exception) {
            fail("Should not reach this.")
        }
    }

    private fun createWriteCopyLocalConfig(name: String, encryptionKey: ByteArray? = null): RealmConfiguration {
        val builder = RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name(name)
        if (encryptionKey != null) {
            builder.encryptionKey(encryptionKey)
        }
        return builder.build()
    }

    private fun writeCopy(from: Configuration, to: Configuration) {
        Realm.open(from).use { realm ->
            realm.writeBlocking {
                repeat(1000) { i: Int ->
                    copyToRealm(
                        Parent().apply {
                            name = "Object-$i"
                        }
                    )
                }
            }
            realm.writeCopyTo(to)
        }
    }

    @Test
    fun writeCopyTo_localToLocal() {
        val configA: RealmConfiguration = createWriteCopyLocalConfig("fileA.realm")
        val configB: RealmConfiguration = createWriteCopyLocalConfig("fileB.realm")

        writeCopy(from = configA, to = configB)

        // Copy is compacted i.e. smaller than original.
        val fileASize: Long = platformFileSystem.metadata(configA.path.toPath()).size!!
        val fileBSize: Long = platformFileSystem.metadata(configB.path.toPath()).size!!
        assertTrue(fileASize >= fileBSize, "$fileASize >= $fileBSize")
        // Content is copied
        Realm.open(configB).use { realm ->
            assertEquals(1000, realm.query<Parent>().count().find())
        }
    }

    @Test
    fun writeCopyTo_localToLocalEncrypted() {
        val configA: RealmConfiguration = createWriteCopyLocalConfig("fileA.realm")
        val configBEncrypted: RealmConfiguration = createWriteCopyLocalConfig("fileB.realm", Random.Default.nextBytes(Realm.ENCRYPTION_KEY_LENGTH))

        writeCopy(from = configA, to = configBEncrypted)

        // Ensure that new Realm is encrypted
        val configBUnencrypted: RealmConfiguration = RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("fileB.realm")
            .build()

        assertFailsWith<IllegalStateException> {
            Realm.open(configBUnencrypted)
        }

        Realm.open(configBEncrypted).use { realm ->
            assertEquals(1000, realm.query<Parent>().count().find())
        }
    }

    @Test
    fun writeCopyTo_localEncryptedToLocal() {
        val key = Random.Default.nextBytes(Realm.ENCRYPTION_KEY_LENGTH)
        val configAEncrypted: RealmConfiguration = createWriteCopyLocalConfig("fileA.realm", key)
        val configB: RealmConfiguration = createWriteCopyLocalConfig("fileB.realm")

        writeCopy(from = configAEncrypted, to = configB)

        // Ensure that new Realm is not encrypted
        val configBEncrypted: RealmConfiguration = createWriteCopyLocalConfig("fileB.realm", key)
        assertFailsWith<IllegalStateException> {
            Realm.open(configBEncrypted)
        }

        Realm.open(configB).use { realm ->
            assertEquals(1000, realm.query<Parent>().count().find())
        }
    }

    @Test
    fun writeCopyTo_destinationAlreadyExist_throws() {
        val configA: RealmConfiguration = createWriteCopyLocalConfig("fileA.realm")
        val configB: RealmConfiguration = createWriteCopyLocalConfig("fileB.realm")
        Realm.open(configB).use {}
        Realm.open(configA).use { realm ->
            assertFailsWith<IllegalArgumentException> {
                realm.writeCopyTo(configB)
            }
        }
    }

    @Test
    fun compactRealm() {
        realm.close()
        if (isWindows()) {
            assertFailsWith<NotImplementedError> {
                Realm.compactRealm(configuration)
            }
        } else {
            assertTrue(Realm.compactRealm(configuration))
        }
    }

    @Test
    fun compactRealm_failsIfOpen() {
        if (isWindows()) {
            assertFailsWith<NotImplementedError> {
                Realm.compactRealm(configuration)
            }
        } else {
            assertFalse(Realm.compactRealm(configuration))
        }
    }

    @Ignore // Test to generate a realm file to use in initialRealmFile. Copy the generated file to
    // - test-base/src/androidMain/assets/subdir/asset.realm
    // - test-base/src/jvmTest/resources/subdir/asset.realm
    // - test-base/src/iosTest/resources/subdir/asset.realm
    // - test-base/src/macosTest/resources/subdir/asset.realm
    @Test
    fun createInitialRealmFile() {
        val config = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
            // Need a separate name to avoid clashes with this.realm initialized in setup()
            .name("asset.realm")
            .build()
        Realm.deleteRealm(config)
        Realm.open(config).use {
            it.writeBlocking {
                copyToRealm(Parent())
                copyToRealm(Parent())
                copyToRealm(Parent())
                copyToRealm(Parent())
            }
        }
    }

    @Test
    fun initialRealmFile() {
        val config = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            // Need a separate name to avoid clashes with this.realm initialized in setup()
            .name("prefilled.realm")
            .initialRealmFile("subdir/asset.realm")
            .build()

        assertFalse(fileExists(config.path))
        Realm.open(config).use {
            val schema = it.schema()
            // Verify that the initial realm already has some data in it
            assertEquals(4, it.query<Parent>().find().size)

            it.writeBlocking { delete(query<Parent>()) }
            assertEquals(0, it.query<Parent>().find().size)
        }

        // Verify that reopening the file see the updates and doesn't reinitialize the realm from
        // the initialRealmFile
        Realm.open(config).use {
            assertEquals(0, it.query<Parent>().find().size)
        }
    }

    @Test
    fun initialRealmFile_withChecksum() {
        val config = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("prefilled.realm")
            .initialRealmFile("subdir/asset.realm", "8984dda08008bbc6b56d2b8f6ba50dc378bb865a59a082eb42862ad31c21ad21")
            .build()

        assertFalse(fileExists(config.path))
        Realm.open(config).use {
            val schema = it.schema()
            // Verify that the initial realm already has some data in it
            assertEquals(4, it.query<Parent>().find().size)

            it.writeBlocking { delete(query<Parent>()) }
            assertEquals(0, it.query<Parent>().find().size)
        }

        // Verify that reopening the file see the updates and doesn't reinitialize the realm from
        // the initialRealmFile
        Realm.open(config).use {
            assertEquals(0, it.query<Parent>().find().size)
        }
    }

    @Test
    fun initialRealmFile_invalidChecksum() {
        val config = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("prefilled.realm")
            .initialRealmFile("subdir/asset.realm", "asdf")
            .build()

        assertFalse(fileExists(config.path))
        assertFailsWithMessage<RuntimeException>("Asset file checksum for 'subdir/asset.realm' does not match. Expected 'asdf' but was '8984dda08008bbc6b56d2b8f6ba50dc378bb865a59a082eb42862ad31c21ad21'") {
            Realm.open(config)
        }
    }

    @Test
    fun initialRealmFile_nonExistingFile() {
        val config = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("prefilled.realm")
            .initialRealmFile("nonexistingfile.realm")
            .build()

        assertFalse(fileExists(config.path))
        assertFailsWithMessage<IllegalArgumentException>("Asset file not found: 'nonexistingfile.realm'") {
            Realm.open(config)
        }
    }

    @Test
    fun initialRealmFile_existingFileDisregardWrongAssetFile() {
        val config = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("default.realm")
            .initialRealmFile("nonexistingfile.realm")
            .build()

        assertTrue(fileExists(config.path))
        Realm.open(config).use { }
    }

    @Test
    fun initialRealmFile_existingFileDisregardWrongChecksum() {
        val config = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("default.realm")
            .initialRealmFile("subdir/asset.realm", "invalid_checksum")
            .build()

        assertTrue(fileExists(config.path))
        Realm.open(config).use { }
    }

    @Test
    fun initialRealmFile_initialDataOnTopOfInitialRealmFile() {
        val config = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("initial_realm.realm")
            .initialRealmFile("subdir/asset.realm")
            .initialData {
                // Verify that initialData is executing on top of initialRealmFile
                val results = query<Parent>().find()
                assertEquals(4, results.size)
                delete(results)
            }
            .build()

        assertFalse(fileExists(config.path))
        Realm.open(config).use {
            assertEquals(0, it.query<Parent>().find().size)
        }
    }

    @Test
    fun initialRealmFile_throwsWithSyncInitialRealmFile() {
        val config = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("initial_realm.realm")
            .initialRealmFile("asset-pbs.realm")
            .build()

        assertFalse(fileExists(config.path))
        assertFailsWithMessage<IllegalStateException>("has history type 'SyncClient'") {
            Realm.open(config)
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
//    private val intermediateReferences: AtomicRef<Set<Pair<NativePointer, WeakReference<io.realm.kotlin.internal.RealmReference>>>>
//        get() {
//            @Suppress("invisible_member")
//            return (realm as io.realm.kotlin.internal.RealmImpl).intermediateReferences
//        }
}
