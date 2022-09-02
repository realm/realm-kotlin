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

package io.realm.kotlin.test.shared

import io.realm.kotlin.LogConfiguration
import io.realm.kotlin.Realm
import io.realm.kotlin.VersionId
import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.ParentPk
import io.realm.kotlin.entities.sync.SyncObjectWithAllTypes
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.internal.platform.freeze
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.DownloadingRealmTimeOutException
import io.realm.kotlin.mongodb.exceptions.SyncException
import io.realm.kotlin.mongodb.exceptions.UnrecoverableSyncException
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncSession
import io.realm.kotlin.mongodb.sync.SyncSession.ErrorHandler
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.test.mongodb.TESTAPP_PARTITION
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.mongodb.shared.DEFAULT_NAME
import io.realm.kotlin.test.mongodb.util.SyncPermissions
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.TestHelper.randomEmail
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.nanoseconds

class SyncedRealmTests {

    companion object {
        // Initial version of any new typed Realm (due to schema being written)
        private val INITIAL_VERSION = VersionId(2)
    }

    private lateinit var partitionValue: String
    private lateinit var realm: Realm
    private lateinit var syncConfiguration: SyncConfiguration
    private lateinit var app: App

    @BeforeTest
    fun setup() {
        partitionValue = TestHelper.randomPartitionValue()
        app = TestApp(TESTAPP_PARTITION)

        val (email, password) = randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }

        syncConfiguration = createSyncConfig(
            user = user,
            partitionValue = partitionValue,
        )
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        if (this::app.isInitialized) {
            app.asTestApp.close()
        }
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

        val config1 = createSyncConfig(
            user = user, partitionValue = partitionValue, name = "db1",
            errorHandler = object : SyncSession.ErrorHandler {
                override fun onError(session: SyncSession, error: SyncException) {
                    fail("Realm 1: $error")
                }
            }
        )
        val realm1 = Realm.open(config1)
        assertNotNull(realm1)

        val config2 = createSyncConfig(
            user = user, partitionValue = partitionValue, name = "db2",
            errorHandler = object : SyncSession.ErrorHandler {
                override fun onError(session: SyncSession, error: SyncException) {
                    fail("Realm 2: $error")
                }
            }
        )
        val realm2 = Realm.open(config2)
        assertNotNull(realm2)

        val child = ChildPk().apply {
            _id = "CHILD_A"
            name = "A"
        }

        val channel = Channel<ResultsChange<ChildPk>>(1)

        // There was a race condition where construction of a query against the user facing frozen
        // version could throw, due to the underlying version being deleted when the live realm was
        // advanced on remote changes.
        // Haven't been able to make a reproducible recipe for triggering this, so just keeping the
        // query around to monitor that we don't reintroduce the issue:
        // https://github.com/realm/realm-kotlin/issues/683
        // For the record, we seemed to hit the race more often when syncing existing data, which
        // can be achieved by just reusing the same partition value and running this test multiple
        // times.
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
    }

    @Test
    fun canOpenWithRemoteSchema() {
        val (email, password) = randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }

        val partitionValue = Random.nextLong().toString()
        // Setup two realms that synchronizes with the backend
        val config1 = createSyncConfig(user = user, partitionValue = partitionValue, name = "db1")
        val realm1 = Realm.open(config1)
        assertNotNull(realm1)
        val config2 = createSyncConfig(user = user, partitionValue = partitionValue, name = "db2")
        val realm2 = Realm.open(config2)
        assertNotNull(realm2)

        // Block until we see changed written to one realm in the other to ensure that schema is
        // aligned with backend
        runBlocking {
            val synced = async {
                realm2.query(ChildPk::class).asFlow().takeWhile { it.list.size != 0 }.collect { }
            }
            realm1.write { copyToRealm(ChildPk()) }
            synced.await()
        }

        // Open a third realm to verify that it can open it when there is a schema on the backend
        // There is no guarantee that this wouldn't succeed if all internal realms (user facing,
        // writer and notifier) are opened before the schema is synced from the server, but
        // empirically it has shown not to be the case and cause trouble if opening the second or
        // third realm with the wrong sync-intended schema mode.
        val config3 = createSyncConfig(user = user, partitionValue = partitionValue, name = "db3")
        val realm3 = Realm.open(config3)
        assertNotNull(realm3)

        realm1.close()
        realm2.close()
        realm3.close()
    }

    @Test
    fun errorHandlerReceivesPermissionDeniedSyncError() {
        val channel = Channel<SyncException>(1).freeze()
        val (email, password) = randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }

        val config = SyncConfiguration.Builder(
            schema = setOf(ParentPk::class, ChildPk::class),
            user = user,
            partitionValue = partitionValue
        ).errorHandler { _, error ->
            channel.trySend(error)
        }.build()

        // Remove permissions to generate a sync error containing ONLY the original path
        // This way we assert we don't read wrong data from the user_info field
        runBlocking {
            app.asTestApp.changeSyncPermissions(SyncPermissions(read = false, write = false)) {
                runBlocking {
                    val deferred = async { Realm.open(config) }

                    val error = channel.receive()
                    assertTrue(error is UnrecoverableSyncException)
                    val message = error.message
                    assertNotNull(message)
                    assertTrue(
                        message.toLowerCase().contains("permission denied"),
                        "The error should be 'PermissionDenied' but it was: $message"
                    )
                    deferred.cancel()
                }
            }
        }
    }

    @Test
    fun testErrorHandler() {
        // Open a realm with a schema. Close it without doing anything else
        val channel = Channel<SyncException>(1).freeze()
        val (email, password) = randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
        val config1 = SyncConfiguration.Builder(
            schema = setOf(ChildPk::class),
            user = user,
            partitionValue = partitionValue
        ).name("test1.realm").build()
        val realm1 = Realm.open(config1)
        assertNotNull(realm1)

        // Open another realm with the same entity but change the type of a field in the schema to
        // trigger a sync error to be caught by the error handler
        runBlocking {
            realm1.syncSession.uploadAllLocalChanges()
            val config2 = SyncConfiguration.Builder(
                schema = setOf(io.realm.kotlin.entities.sync.bogus.ChildPk::class),
                user = user,
                partitionValue = partitionValue
            ).name("test2.realm")
                .errorHandler(object : ErrorHandler {
                    override fun onError(session: SyncSession, error: SyncException) {
                        channel.trySend(error)
                    }
                }).build()
            val realm2 = Realm.open(config2)
            assertNotNull(realm2)

            // Await for exception to happen
            val exception = channel.receive()

            channel.close()

            // Validate that the exception was captured and contains serialized fields
            assertIs<SyncException>(exception)
            exception.message.let { errorMessage ->
                assertNotNull(errorMessage)
                // Some race on JVM in particular mean that different errors can be reported.
                if (errorMessage.contains("[Client]")) {
                    assertTrue(errorMessage.contains("[BadChangeset(112)]"), errorMessage)
                    assertTrue(errorMessage.contains("Bad changeset (DOWNLOAD)"), errorMessage)
                } else if (errorMessage.contains("[Session]")) {
                    assertTrue(errorMessage.contains("InvalidSchemaChange(225)"), errorMessage)
                    assertTrue(
                        errorMessage.contains("Invalid schema change (UPLOAD)"),
                        errorMessage
                    )
                } else {
                    fail("Unexpected error message: $errorMessage")
                }
            }

            // Housekeeping for test Realms
            realm1.close()
            realm2.close()
        }
    }

    // It is unclear what we mean by "MainThread" in KMP, until we do, this functionality is
    // disabled. See https://github.com/realm/realm-kotlin/issues/847
    @Test
    @Ignore
    fun waitForInitialRemoteData_mainThreadThrows() = runBlocking(Dispatchers.Main) {
        val user = app.asTestApp.createUserAndLogin()
        val config = SyncConfiguration.Builder(user, TestHelper.randomPartitionValue(), setOf())
            .waitForInitialRemoteData()
            .build()
        assertFailsWith<IllegalStateException> {
            Realm.open(config)
        }
        Unit
    }

    @Test
    fun waitForInitialRemoteData() = runBlocking {
        val partitionValue = TestHelper.randomPartitionValue()
        val schema = setOf(ParentPk::class, ChildPk::class)

        // 1. Copy a valid Realm to the server
        val user1 = app.asTestApp.createUserAndLogin()
        val config1: SyncConfiguration = SyncConfiguration.create(user1, partitionValue, schema)
        Realm.open(config1).use { realm ->
            realm.write {
                for (index in 0..9) {
                    copyToRealm(
                        ParentPk().apply {
                            _id = "$partitionValue-$index"
                        }
                    )
                }
            }
            realm.syncSession.uploadAllLocalChanges()
        }

        // 2. Sometimes it can take a little while for the data to be available to other users,
        // so make sure data has reached server.
        val user2 = app.asTestApp.createUserAndLogin()
        val config2: SyncConfiguration = SyncConfiguration.create(user2, partitionValue, schema)
        assertNotEquals(config1.path, config2.path)
        Realm.open(config2).use { realm ->
            val count = realm.query<ParentPk>()
                .asFlow()
                .map { it.list.size }
                .first { it == 10 }
            assertEquals(10, count)
        }

        // 3. Finally verify `waitForInitialData` is working
        val user3 = app.asTestApp.createUserAndLogin()
        val config3: SyncConfiguration = SyncConfiguration.Builder(user3, partitionValue, schema)
            .waitForInitialRemoteData()
            .build()
        assertNotEquals(config1.path, config3.path)
        Realm.open(config3).use { realm ->
            assertEquals(10, realm.query<ParentPk>().count().find())
        }
    }

    @Test
    fun waitForInitialData_timeOut() = runBlocking {
        val partitionValue = TestHelper.randomPartitionValue()
        val schema = setOf(ParentPk::class, ChildPk::class)
        val objectCount = 1000 // High enough to introduce latency when download Realm initial data

        // 1. Copy a valid Realm to the server
        val user1 = app.asTestApp.createUserAndLogin()
        val config1: SyncConfiguration = SyncConfiguration.create(user1, partitionValue, schema)
        Realm.open(config1).use { realm ->
            realm.write {
                for (index in 0 until objectCount) {
                    copyToRealm(
                        ParentPk().apply {
                            _id = "$partitionValue-$index"
                        }
                    )
                }
            }
            realm.syncSession.uploadAllLocalChanges()
        }

        // 2. Sometimes it can take a little while for the data to be available to other users,
        // so make sure data has reached server.
        val user2 = app.asTestApp.createUserAndLogin()
        val config2: SyncConfiguration = SyncConfiguration.create(user2, partitionValue, schema)
        assertNotEquals(config1.path, config2.path)
        Realm.open(config2).use { realm ->
            val count = realm.query<ParentPk>()
                .asFlow()
                .filter { it.list.size == objectCount }
                .map { it.list.size }
                .first()
            assertEquals(objectCount, count)
        }

        // 3. Finally verify `waitForInitialData` is working
        val user3 = app.asTestApp.createUserAndLogin()
        val config3: SyncConfiguration = SyncConfiguration.Builder(user3, partitionValue, schema)
            .waitForInitialRemoteData(1.nanoseconds)
            .build()
        assertNotEquals(config1.path, config3.path)
        assertFailsWith<DownloadingRealmTimeOutException> {
            Realm.open(config3).use {
                fail("Realm should not open in time")
            }
        }
        Unit
    }

    // This tests will start and cancel getting a Realm 10 times. The Realm should be resilient
    // towards that. We cannot do much better since we cannot control the order of events internally
    // which would be needed to correctly test all error paths.
    @Test
    @Ignore // See https://github.com/realm/realm-kotlin/issues/851
    fun waitForInitialData_resilientInCaseOfRetries() = runBlocking {
        val user = app.asTestApp.createUserAndLogin()
        val schema = setOf(ParentPk::class, ChildPk::class)
        val partitionValue = TestHelper.randomPartitionValue()
        val config: SyncConfiguration = SyncConfiguration.Builder(user, partitionValue, schema)
            .waitForInitialRemoteData(1.nanoseconds)
            .build()

        for (i in 0..9) {
            assertFalse(fileExists(config.path), "Index: $i, Path: ${config.path}")
            Realm.open(config).use {
                fail("Index $i")
            }
        }
    }

    // Currently no good way to delete synced Realms that has been opened.
    // See https://github.com/realm/realm-core/issues/5542
    @Test
    @Suppress("LongMethod")
    @Ignore
    fun deleteRealm() {
        val fileSystem = FileSystem.SYSTEM
        val user = app.asTestApp.createUserAndLogin()
        val configuration: SyncConfiguration =
            SyncConfiguration.create(user, partitionValue, setOf())
        val syncDir: Path =
            "${app.configuration.syncRootDirectory}/mongodb-realm/${app.configuration.appId}/${user.identity}".toPath()

        val bgThreadReadyChannel = Channel<Unit>(1)
        val readyToCloseChannel = Channel<Unit>(1)
        val closedChannel = Channel<Unit>(1)

        kotlinx.coroutines.runBlocking {
            val testRealm = Realm.open(configuration)

            val deferred = async {
                // Create another Realm to ensure the log files are generated.
                val anotherRealm = Realm.open(configuration)
                bgThreadReadyChannel.send(Unit)
                readyToCloseChannel.receive()
                anotherRealm.close()
                closedChannel.send(Unit)
            }

            // Waits for background thread opening the same Realm.
            bgThreadReadyChannel.receive()

            // Check the realm got created correctly and signal that it can be closed.
            fileSystem.list(syncDir)
                .also { testDirPathList ->
                    assertEquals(4, testDirPathList.size) // db file, .lock, .management, .note
                    readyToCloseChannel.send(Unit)
                }
            testRealm.close()
            closedChannel.receive()

            // Delete realm now that it's fully closed.
            Realm.deleteRealm(configuration)

            // Lock file should never be deleted.
            fileSystem.list(syncDir)
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
    fun schemaRoundTrip() = runBlocking {
        val (email1, password1) = randomEmail() to "password1234"
        val (email2, password2) = randomEmail() to "password1234"
        val user1 = app.createUserAndLogIn(email1, password1)
        val user2 = app.createUserAndLogIn(email2, password2)

        // Create object with all types
        val id = "id-${Random.nextLong()}"
        val masterObject = SyncObjectWithAllTypes.createWithSampleData(id)

        createSyncConfig(
            user = user1,
            partitionValue = partitionValue,
            schema = setOf(SyncObjectWithAllTypes::class)
        ).let { config ->
            Realm.open(config).use { realm ->
                realm.write {
                    copyToRealm(masterObject)
                }
                realm.syncSession.uploadAllLocalChanges()
            }
        }
        createSyncConfig(
            user = user2,
            partitionValue = partitionValue,
            schema = setOf(SyncObjectWithAllTypes::class)
        ).let { config ->
            Realm.open(config).use { realm ->
                val obj: SyncObjectWithAllTypes =
                    realm.query<SyncObjectWithAllTypes>("_id = $0", id)
                        .asFlow()
                        .first {
                            it.list.size == 1
                        }.list.first()
                assertTrue(SyncObjectWithAllTypes.compareAgainstSampleData(obj))
            }
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
//    private val intermediateReferences: AtomicRef<Set<Pair<NativePointer, WeakReference<io.realm.kotlin.internal.RealmReference>>>>
//        get() {
//            @Suppress("invisible_member")
//            return (realm as io.realm.kotlin.internal.RealmImpl).intermediateReferences
//        }

    @Suppress("LongParameterList")
    private fun createSyncConfig(
        user: User,
        partitionValue: String,
        name: String = DEFAULT_NAME,
        encryptionKey: ByteArray? = null,
        log: LogConfiguration? = null,
        errorHandler: ErrorHandler? = null,
        schema: Set<KClass<out RealmObject>> = setOf(ParentPk::class, ChildPk::class),
    ): SyncConfiguration = SyncConfiguration.Builder(
        schema = schema,
        user = user,
        partitionValue = partitionValue
    ).name(name).also { builder ->
        if (encryptionKey != null) builder.encryptionKey(encryptionKey)
        if (errorHandler != null) builder.errorHandler(errorHandler)
        if (log != null) builder.log(log.level, log.loggers)
    }.build()
}
