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

import io.realm.LogConfiguration
import io.realm.Realm
import io.realm.VersionId
import io.realm.entities.sync.ChildPk
import io.realm.entities.sync.ParentPk
import io.realm.internal.platform.freeze
import io.realm.internal.platform.runBlocking
import io.realm.mongodb.SyncConfiguration
import io.realm.mongodb.SyncException
import io.realm.mongodb.SyncSession
import io.realm.mongodb.SyncSession.ErrorHandler
import io.realm.mongodb.User
import io.realm.notifications.ResultsChange
import io.realm.query
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.asTestApp
import io.realm.test.mongodb.createUserAndLogIn
import io.realm.test.mongodb.shared.DEFAULT_NAME
import io.realm.test.mongodb.shared.DEFAULT_PARTITION_VALUE
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.TestHelper.randomEmail
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.takeWhile
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncedRealmTests {

    companion object {
        // Initial version of any new typed Realm (due to schema being written)
        private val INITIAL_VERSION = VersionId(2)
    }

    private lateinit var tmpDir: String
    private lateinit var realm: Realm
    private lateinit var syncConfiguration: SyncConfiguration
    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        app = TestApp()

        val (email, password) = randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }

        tmpDir = PlatformUtils.createTempDir()
        syncConfiguration = createSyncConfig(
            user = user,
            partitionValue = DEFAULT_PARTITION_VALUE,
            directory = tmpDir
        )
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.asTestApp.close()
        }
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun canOpen() {
        realm = Realm.open(syncConfiguration)
        assertNotNull(realm)
    }

    @Test
    fun canSync() {
        // A user has two realms in different files, 1 stores an object locally and 2 receives the
        // update from the server after the object is synchronized.
        val (email, password) = randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }

        val partitionValue = Random.nextULong().toString()

        val dir1 = PlatformUtils.createTempDir()
        val config1 = createSyncConfig(directory = dir1, user = user, partitionValue = partitionValue)
        val realm1 = Realm.open(config1)
        assertNotNull(realm1)

        val dir2 = PlatformUtils.createTempDir()
        val config2 = createSyncConfig(directory = dir2, user = user, partitionValue = partitionValue)
        val realm2 = Realm.open(config2)
        assertNotNull(realm2)

        val child = ChildPk().apply {
            _id = "CHILD_A"
            name = "A"
        }

        val channel = Channel<ResultsChange<ChildPk>>(1)

        // This query has caused trouble on CI
        assertEquals(0, realm1.query<ChildPk>().find().size, realm1.toString())

        runBlocking {
            val observer = async {
                realm2.query<ChildPk>()
                    .asFlow()
                    .collect { childResults: ResultsChange<ChildPk> ->
                        channel.send(childResults)
                    }
            }

            assertEquals(0, channel.receive().list.size)

            realm1.write {
                copyToRealm(child)
            }

            val childResults = channel.receive()
            val childPk = childResults.list[0]
            assertEquals("CHILD_A", childPk._id)
            observer.cancel()
            channel.close()
        }

        realm1.close()
        realm2.close()
        PlatformUtils.deleteTempDir(dir1)
        PlatformUtils.deleteTempDir(dir2)
    }

    @Test
    fun canOpenWithRemoteSchema() = runBlocking {
        val (email, password) = randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }

        val partitionValue = Random.nextLong().toString()
        // Setup two realms that synchronizes with the backend
        val dir1 = PlatformUtils.createTempDir()
        val config1 = createSyncConfig(directory = dir1, partitionValue = partitionValue, user = user)
        val realm1 = Realm.open(config1)
        assertNotNull(realm1)
        val dir2 = PlatformUtils.createTempDir()
        val config2 = createSyncConfig(directory = dir2, user = user)
        val realm2 = Realm.open(config2)
        assertNotNull(realm2)

        // Block until we see changed written to one realm in the other to ensure that schema is
        // aligned with backend
        val synced = async {
            realm2.query(ChildPk::class).asFlow().takeWhile { it.list.size != 0 }.collect { }
        }
        realm1.write { copyToRealm(ChildPk()) }
        synced.await()

        // Open a third realm to verify that it can open it when there is a schema on the backend
        // There is no guarantee that this wouldn't succeed if all internal realms (user facing,
        // writer and notifier) are opened before the schema is synced from the server, but
        // empirically it has shown not to be the case and cause trouble if opening the second or
        // third realm with the wrong sync-intended schema mode.
        val dir3 = PlatformUtils.createTempDir()
        val config3 = createSyncConfig(directory = dir3, user = user)
        val realm3 = Realm.open(config3)
        assertNotNull(realm3)

        realm1.close()
        realm2.close()
        realm3.close()
        PlatformUtils.deleteTempDir(dir1)
        PlatformUtils.deleteTempDir(dir2)
        PlatformUtils.deleteTempDir(dir3)
    }

    @Test
    fun testErrorHandler() {
        // Open a realm with a schema. Close it without doing anything else
        val channel = Channel<SyncException>(1).freeze()
        val (email, password) = randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
        val tmpDir = PlatformUtils.createTempDir()

        val config1 = SyncConfiguration.Builder(
            schema = setOf(ChildPk::class),
            user = user,
            partitionValue = DEFAULT_PARTITION_VALUE
        ).directory(tmpDir).name("test1.realm").build()
        val realm1 = Realm.open(config1)
        assertNotNull(realm1)

        // Open another realm with the same entity but change the type of a field in the schema to
        // trigger a sync error to be caught by the error handler
        runBlocking {
            val config2 = SyncConfiguration.Builder(
                schema = setOf(io.realm.entities.sync.bogus.ChildPk::class),
                user = user,
                partitionValue = DEFAULT_PARTITION_VALUE
            ).directory(tmpDir).name("test2.realm")
                .also { builder ->
                    builder.errorHandler(object : ErrorHandler {
                        override fun onError(session: SyncSession, error: SyncException) {
                            runBlocking {
                                channel.send(error)
                            }
                        }
                    })
                }.build()
            val realm2 = Realm.open(config2)
            assertNotNull(realm2)

            // Await for exception to happen
            val exception = channel.receive()

            channel.close()

            // Validate that the exception was captured and contains serialized fields
            assertIs<SyncException>(exception)
            exception.message.let { errorMessage ->
                assertNotNull(errorMessage)
                assertTrue(errorMessage.contains("error_code.category="))
                assertTrue(errorMessage.contains("error_code.value="))
                assertTrue(errorMessage.contains("error_code.message="))
                assertTrue(errorMessage.contains("is_fatal="))
                assertTrue(errorMessage.contains("is_unrecognized_by_client="))
            }

            // Housekeeping for test Realms
            realm1.close()
            realm2.close()
            PlatformUtils.deleteTempDir(tmpDir)
        }
    }

//    @Test
//    fun initialVersion() {
//        assertEquals(INITIAL_VERSION, realm.version())
//    }
//
//    @Test
//    fun versionIncreaseOnWrite() {
//        assertEquals(INITIAL_VERSION, realm.version())
//        realm.writeBlocking { /* Do Nothing */ }
//        assertEquals(VersionId(3), realm.version())
//    }
//
//    @Test
//    fun versionDoesNotChangeWhenCancellingWrite() {
//        assertEquals(INITIAL_VERSION, realm.version())
//        realm.writeBlocking { cancelWrite() }
//        assertEquals(INITIAL_VERSION, realm.version())
//    }
//
//    @Test
//    fun versionThrowsIfRealmIsClosed() {
//        realm.close()
//        assertFailsWith<IllegalStateException> { realm.version() }
//    }
//
//    @Test
//    fun versionInsideWriteIsLatest() {
//        assertEquals(INITIAL_VERSION, realm.version())
//        realm.writeBlocking {
//            assertEquals(INITIAL_VERSION, version())
//            cancelWrite()
//        }
//        assertEquals(INITIAL_VERSION, realm.version())
//    }
//
//    @Test
//    fun numberOfActiveVersions() {
//        assertEquals(2, realm.getNumberOfActiveVersions())
//        realm.writeBlocking {
//            assertEquals(2, getNumberOfActiveVersions())
//        }
//        assertEquals(2, realm.getNumberOfActiveVersions())
//    }
//
//    @Test
//    @Ignore // FIXME This fails on MacOS only. Are versions cleaned up more aggressively there?
//    fun throwsIfMaxNumberOfActiveVersionsAreExceeded() {
//        realm.close()
//        val config = RealmConfiguration.Builder(
//            path = "$tmpDir/exceed-versions.realm",
//            schema = setOf(Parent::class, Child::class)
//        ).maxNumberOfActiveVersions(1).build()
//        realm = Realm.open(config)
//        // Pin the version, so when starting a new transaction on the first Realm,
//        // we don't release older versions.
//        val otherRealm = Realm.open(config)
//
//        try {
//            assertFailsWith<IllegalStateException> { realm.writeBlocking { } }
//        } finally {
//            otherRealm.close()
//        }
//    }
//
//    @Suppress("invisible_member")
//    @Test
//    fun write() = runBlocking {
//        val name = "Realm"
//        val child: Child = realm.write {
//            this.copyToRealm(Child()).apply { this.name = name }
//        }
//        assertEquals(name, child.name)
//        val objects = realm.objects<Child>()
//        val childFromResult = objects[0]
//        assertEquals(name, childFromResult.name)
//    }
//
//    @Suppress("invisible_member")
//    @Test
//    fun exceptionInWriteWillRollback() = runBlocking {
//        class CustomException : Exception()
//
//        assertFailsWith<CustomException> {
//            realm.write {
//                val name = "Realm"
//                this.copyToRealm(Child()).apply { this.name = name }
//                throw CustomException()
//            }
//        }
//        assertEquals(0, realm.objects<Child>().size)
//    }
//
//    @Test
//    fun writeBlocking() {
//        val managedChild = realm.writeBlocking { copyToRealm(Child().apply { name = "John" }) }
//        assertTrue(managedChild.isManaged())
//        assertEquals("John", managedChild.name)
//    }
//
//    @Suppress("invisible_member")
//    @Test
//    fun writeBlockingAfterWrite() = runBlocking {
//        val name = "Realm"
//        val child: Child = realm.write {
//            this.copyToRealm(Child()).apply { this.name = name }
//        }
//        assertEquals(name, child.name)
//        assertEquals(1, realm.objects<Child>().size)
//
//        realm.writeBlocking {
//            this.copyToRealm(Child()).apply { this.name = name }
//        }
//        Unit
//    }
//
//    @Suppress("invisible_member")
//    @Test
//    fun exceptionInWriteBlockingWillRollback() {
//        class CustomException : Exception()
//        assertFailsWith<CustomException> {
//            realm.writeBlocking {
//                val name = "Realm"
//                this.copyToRealm(Child()).apply { this.name = name }
//                throw CustomException()
//            }
//        }
//        assertEquals(0, realm.objects<Child>().size)
//    }
//
//    @Test
//    @Suppress("invisible_member")
//    fun simultaneousWritesAreAllExecuted() = runBlocking {
//        val jobs: List<Job> = IntRange(0, 9).map {
//            launch {
//                realm.write {
//                    copyToRealm(Parent())
//                }
//            }
//        }
//        jobs.map { it.join() }
//
//        // Ensure that all writes are actually committed
//        realm.close()
//        assertTrue(realm.isClosed())
//        realm = Realm.open(configuration)
//        assertEquals(10, realm.objects(Parent::class).size)
//    }
//
//    @Test
//    @Suppress("invisible_member")
//    fun writeBlockingWhileWritingIsSerialized() = runBlocking {
//        val writeStarted = Mutex(true)
//        val writeEnding = Mutex(true)
//        val writeBlockingQueued = Mutex(true)
//        async {
//            realm.write {
//                writeStarted.unlock()
//                while (writeBlockingQueued.isLocked) {
//                    PlatformUtils.sleep(1.milliseconds)
//                }
//                writeEnding.unlock()
//            }
//        }
//        writeStarted.lock()
//        runBlocking {
//            val async = async {
//                realm.writeBlocking {
//                    assertFalse { writeEnding.isLocked }
//                }
//            }
//            writeBlockingQueued.unlock()
//            async.await()
//        }
//    }
//
//    @Test
//    @Suppress("invisible_member")
//    fun close() = runBlocking {
//        realm.write {
//            copyToRealm(Parent())
//        }
//        realm.close()
//        assertTrue(realm.isClosed())
//
//        realm = Realm.open(configuration)
//        assertEquals(1, realm.objects(Parent::class).size)
//    }
//
//    @Test
//    @Suppress("invisible_member")
//    fun closeCausesOngoingWriteToThrow() = runBlocking {
//        val writeStarted = Mutex(true)
//        val write = async {
//            assertFailsWith<IllegalStateException> {
//                realm.write {
//                    writeStarted.unlock()
//                    copyToRealm(Parent())
//                    // realm.close is blocking until write block is done, so we cannot wait on
//                    // specific external events, so just sleep a bit :/
//                    PlatformUtils.sleep(Duration.Companion.milliseconds(100))
//                }
//            }
//        }
//        writeStarted.lock()
//        realm.close()
//        assert(write.await() is RuntimeException)
//        realm = Realm.open(configuration)
//        assertEquals(0, realm.objects<Parent>().size)
//    }
//
//    @Test
//    @Suppress("invisible_member")
//    fun writeAfterCloseThrows() = runBlocking {
//        realm.close()
//        assertTrue(realm.isClosed())
//        assertFailsWith<IllegalStateException> {
//            realm.write {
//                copyToRealm(Child())
//            }
//        }
//        Unit
//    }
//
//    @Test
//    @Suppress("invisible_member")
//    fun coroutineCancelCausesRollback() = runBlocking {
//        val mutex = Mutex(true)
//        val job = async {
//            realm.write {
//                copyToRealm(Parent())
//                mutex.unlock()
//                // Ensure that we keep on going until actually cancelled
//                while (isActive) {
//                    PlatformUtils.sleep(1.milliseconds)
//                }
//            }
//        }
//        mutex.lock()
//        job.cancelAndJoin()
//
//        // Ensure that write is not committed
//        realm.close()
//        assertTrue(realm.isClosed())
//        realm = Realm.open(configuration)
//        // This assertion doesn't hold on MacOS as all code executes on the same thread as the
//        // dispatcher is a run loop on the local thread, thus, the main flow is not picked up when
//        // the mutex is unlocked. Doing so would require the write block to be able to suspend in
//        // some way (or the writer to be backed by another thread).
//        assertEquals(0, realm.objects(Parent::class).size)
//    }
//
//    @Test
//    @Suppress("invisible_member")
//    fun writeAfterCoroutineCancel() = runBlocking {
//        val mutex = Mutex(true)
//        val job = async {
//            realm.write {
//                copyToRealm(Parent())
//                mutex.unlock()
//                // Ensure that we keep on going until actually cancelled
//                while (isActive) {
//                    PlatformUtils.sleep(1.milliseconds)
//                }
//            }
//        }
//
//        mutex.lock()
//        job.cancelAndJoin()
//
//        // Verify that we can do other writes after cancel
//        realm.write {
//            copyToRealm(Parent())
//        }
//
//        // Ensure that only one write is actually committed
//        realm.close()
//        assertTrue(realm.isClosed())
//        realm = Realm.open(configuration)
//        // This assertion doesn't hold on MacOS as all code executes on the same thread as the
//        // dispatcher is a run loop on the local thread, thus, the main flow is not picked up when
//        // the mutex is unlocked. Doing so would require the write block to be able to suspend in
//        // some way (or the writer to be backed by another thread).
//        assertEquals(1, realm.objects(Parent::class).size)
//    }
//
//    @Test
//    @Suppress("invisible_member")
//    fun writesOnFrozenRealm() {
//        val dispatcher = newSingleThreadContext("background")
//        runBlocking {
//            realm.write {
//                copyToRealm(Parent())
//            }
//        }
//        runBlocking(dispatcher) {
//            realm.write {
//                copyToRealm(Parent())
//            }
//        }
//        assertEquals(2, realm.objects<Parent>().size)
//    }
//
//    @Test
//    fun closeClosesAllVersions() {
//        runBlocking {
//            realm.write { copyToRealm(Parent()) }
//        }
//        val parent: Parent = realm.objects<Parent>().first()
//        runBlocking {
//            realm.write { copyToRealm(Parent()) }
//        }
//        realm.close()
//        assertFailsWith<IllegalStateException> {
//            parent.version()
//        }
//    }
//
//    @Test
//    fun closingIntermediateVersionsWhenNoLongerReferenced() {
//        assertEquals(0, intermediateReferences.value.size)
//        var parent: Parent? = realm.writeBlocking { copyToRealm(Parent()) }
//        assertEquals(1, intermediateReferences.value.size)
//        realm.writeBlocking { }
//        assertEquals(2, intermediateReferences.value.size)
//
//        // Clear reference
//        parent = null
//        // Trigger GC
//        triggerGC()
//        // Close of intermediate version is currently only done when updating the realm after a write
//        realm.writeBlocking { }
//        assertEquals(1, intermediateReferences.value.size)
//    }
//
//    @Suppress("invisible_reference")
//    private val intermediateReferences: AtomicRef<Set<Pair<NativePointer, WeakReference<io.realm.internal.RealmReference>>>>
//        get() {
//            @Suppress("invisible_member")
//            return (realm as io.realm.internal.RealmImpl).intermediateReferences
//        }

    @Suppress("LongParameterList")
    private fun createSyncConfig(
        user: User,
        partitionValue: String = DEFAULT_PARTITION_VALUE,
        directory: String? = null,
        name: String = DEFAULT_NAME,
        encryptionKey: ByteArray? = null,
        log: LogConfiguration? = null,
        errorHandler: ErrorHandler? = null
    ): SyncConfiguration = SyncConfiguration.Builder(
        schema = setOf(ParentPk::class, ChildPk::class),
        user = user,
        partitionValue = partitionValue
    ).directory(directory)
        .name(name)
        .let { builder ->
            if (encryptionKey != null) builder.encryptionKey(encryptionKey)
            if (errorHandler != null) builder.errorHandler(errorHandler)
            if (log != null) builder.log(log.level, log.loggers)
            builder
        }.build()
}
