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

package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.LogConfiguration
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.VersionId
import io.realm.kotlin.entities.sync.BinaryObject
import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.ParentPk
import io.realm.kotlin.entities.sync.SyncObjectWithAllTypes
import io.realm.kotlin.entities.sync.flx.FlexChildObject
import io.realm.kotlin.entities.sync.flx.FlexEmbeddedObject
import io.realm.kotlin.entities.sync.flx.FlexParentObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.internal.platform.freeze
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.DownloadingRealmTimeOutException
import io.realm.kotlin.mongodb.exceptions.SyncException
import io.realm.kotlin.mongodb.exceptions.UnrecoverableSyncException
import io.realm.kotlin.mongodb.subscriptions
import io.realm.kotlin.mongodb.sync.InitialSubscriptionsCallback
import io.realm.kotlin.mongodb.sync.SubscriptionSetState
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncSession
import io.realm.kotlin.mongodb.sync.SyncSession.ErrorHandler
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.notifications.InitialRealm
import io.realm.kotlin.notifications.RealmChange
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedRealm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.schema.RealmClass
import io.realm.kotlin.schema.RealmSchema
import io.realm.kotlin.schema.ValuePropertyType
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.TestHelper.randomEmail
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("LargeClass")
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
        app = TestApp()

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

    // Test for https://github.com/realm/realm-kotlin/issues/1070
    @Test
    fun realmAsFlow_acrossSyncedChanges() = runBlocking {
        val (email1, password1) = randomEmail() to "password1234"
        val (email2, password2) = randomEmail() to "password1234"
        val user1 = app.createUserAndLogIn(email1, password1)
        val user2 = app.createUserAndLogIn(email2, password2)

        val config1 = createSyncConfig(
            user = user1,
            name = "db1.realm",
            partitionValue = partitionValue,
            schema = setOf(SyncObjectWithAllTypes::class)
        )
        val realm1 = Realm.open(config1)
        val c = Channel<RealmChange<Realm>>(1)
        val observer = async {
            realm1.asFlow().collect {
                c.send(it)
            }
        }
        val event: RealmChange<Realm> = c.receive()
        assertTrue(event is InitialRealm)

        // Write remote change
        createSyncConfig(
            user = user2,
            name = "db2.realm",
            partitionValue = partitionValue,
            schema = setOf(SyncObjectWithAllTypes::class)
        ).let { config ->
            Realm.open(config).use { realm ->
                realm.write {
                    val id = "id-${Random.nextLong()}"
                    copyToRealm(SyncObjectWithAllTypes().apply { _id = id })
                }
                realm.syncSession.uploadAllLocalChanges()
            }
        }

        // Wait for Realm.asFlow() to be updated based on remote change.
        try {
            withTimeout(timeout = 10.seconds) {
                while (true) {
                    val updateEvent: RealmChange<Realm> = c.receive()
                    assertTrue(updateEvent is UpdatedRealm)
                    if (updateEvent.realm.query<SyncObjectWithAllTypes>().find().size == 1)
                        break
                }
            }
        } finally {
            realm1.close()
            observer.cancel()
            c.cancel()
        }
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
        val channel = Channel<Throwable>(1).freeze()
        // Remove permissions to generate a sync error containing ONLY the original path
        // This way we assert we don't read wrong data from the user_info field
        val (email, password) = "test_nowrite_noread_${randomEmail()}" to "password1234"
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

        runBlocking {
            val deferred = async {
                Realm.open(config)
                // Make sure that the test eventually fail. Coroutines can cancel a delay
                // so this doesn't always block the test for 10 seconds.
                delay(10 * 1000)
                channel.trySend(AssertionError("Realm was successfully opened"))
            }

            val error = channel.receive()
            assertTrue(error is UnrecoverableSyncException, "Was $error")
            val message = error.message
            assertNotNull(message)
            assertTrue(
                message.lowercase().contains("permission denied"),
                "The error should be 'PermissionDenied' but it was: $message"
            )
            deferred.cancel()
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
        val schema = setOf(BinaryObject::class)

        // High enough to introduce latency when download Realm initial data
        // but not too high so that we reach Atlas' transmission limit
        val objectCount = 5

        // 1. Copy a valid Realm to the server
        val user1 = app.asTestApp.createUserAndLogin()
        val config1: SyncConfiguration = SyncConfiguration.create(user1, partitionValue, schema)
        Realm.open(config1).use { realm ->
            realm.write {
                for (index in 0 until objectCount) {
                    copyToRealm(
                        BinaryObject().apply {
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
            val count = realm.query<BinaryObject>()
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
                val list: RealmResults<SyncObjectWithAllTypes> =
                    realm.query<SyncObjectWithAllTypes>("_id = $0", id)
                        .asFlow()
                        .first {
                            it.list.size >= 1
                        }.list
                assertEquals(1, list.size)
                assertTrue(SyncObjectWithAllTypes.compareAgainstSampleData(list.first()))
            }
        }
    }

    // After https://github.com/realm/realm-core/pull/5784 was merged, ObjectStore will now
    // return the full on-disk schema from ObjectStore, but for typed Realms the user visible schema
    // should still only return classes and properties that was defined by the user.
    @Test
    fun onlyLocalSchemaIsVisible() = runBlocking {
        val (email1, password1) = randomEmail() to "password1234"
        val (email2, password2) = randomEmail() to "password1234"
        val user1 = app.createUserAndLogIn(email1, password1)
        val user2 = app.createUserAndLogIn(email2, password2)

        createSyncConfig(
            user = user1,
            partitionValue = partitionValue,
            schema = setOf(SyncObjectWithAllTypes::class, ChildPk::class)
        ).let { config ->
            Realm.open(config).use { realm ->
                realm.syncSession.uploadAllLocalChanges()
                val schema: RealmSchema = realm.schema()
                val childPkSchema: RealmClass? = schema["ChildPk"]
                assertNotNull(childPkSchema)
                assertNotNull(childPkSchema["name"])
                assertNotNull(childPkSchema["age"])
                assertNotNull(childPkSchema["link"])
                assertNotNull(childPkSchema["linkedFrom"])
            }
        }
        createSyncConfig(
            user = user2,
            partitionValue = partitionValue,
            schema = setOf(io.realm.kotlin.entities.sync.subset.ChildPk::class)
        ).let { config ->
            Realm.open(config).use { realm ->
                // Make sure that server schema changes are integrated
                realm.syncSession.downloadAllServerChanges(60.seconds)
                val schema: RealmSchema = realm.schema()
                assertNull(schema["SyncObjectWithAllTypes"])
                val childPkSchema: RealmClass? = schema["ChildPk"]
                assertNotNull(childPkSchema)
                assertNotNull(childPkSchema["name"])
                assertFalse((childPkSchema["_id"]!!.type as ValuePropertyType).isIndexed)
                assertNull(childPkSchema["age"])
                assertNull(childPkSchema["link"])
                assertNull(childPkSchema["linkedFrom"])
            }
        }
    }

    @Suppress("LongMethod")
    @Test
    fun mutableRealmInt_convergesAcrossClients() = runBlocking {
        // Updates and initial data upload are carried out using this config
        val config0 = createSyncConfig(
            user = app.createUserAndLogIn(randomEmail(), "password1234"),
            partitionValue = partitionValue,
            name = "db1",
            schema = setOf(SyncObjectWithAllTypes::class)
        )

        // Config for update 1
        val config1 = createSyncConfig(
            user = app.createUserAndLogIn(randomEmail(), "password1234"),
            partitionValue = partitionValue,
            name = "db2",
            schema = setOf(SyncObjectWithAllTypes::class)
        )

        // Config for update 2
        val config2 = createSyncConfig(
            user = app.createUserAndLogIn(randomEmail(), "password1234"),
            partitionValue = partitionValue,
            name = "db3",
            schema = setOf(SyncObjectWithAllTypes::class)
        )

        val counterValue = Channel<Long>(1)

        // Asynchronously receive updates
        val updates = async {
            Realm.open(config0).use { realm ->
                realm.query<SyncObjectWithAllTypes>()
                    .first()
                    .asFlow()
                    .collect {
                        if (it.obj != null) {
                            val counter = it.obj!!.mutableRealmIntField
                            counterValue.trySend(counter.get())
                        }
                    }
            }
        }

        // Upload initial data - blocking to ensure the two clients find an object to update
        val masterObject = SyncObjectWithAllTypes().apply { _id = "id-${Random.nextLong()}" }
        Realm.open(config0).use { realm ->
            realm.writeBlocking { copyToRealm(masterObject) }
            realm.syncSession.uploadAllLocalChanges()
        }
        assertEquals(42, counterValue.receive())

        // Increment counter asynchronously after download initial data (1)
        val increment1 = async {
            Realm.open(config1).use { realm ->
                realm.syncSession.downloadAllServerChanges(30.seconds)
                realm.write {
                    realm.query<SyncObjectWithAllTypes>()
                        .first()
                        .find()
                        .let { assertNotNull(findLatest(assertNotNull(it))) }
                        .mutableRealmIntField
                        .increment(1)
                }
            }
        }
        assertEquals(43, counterValue.receive())

        // Increment counter asynchronously after download initial data (2)
        val increment2 = async {
            Realm.open(config2).use { realm ->
                realm.syncSession.downloadAllServerChanges(30.seconds)
                realm.write {
                    realm.query<SyncObjectWithAllTypes>()
                        .first()
                        .find()
                        .let { assertNotNull(findLatest(assertNotNull(it))) }
                        .mutableRealmIntField
                        .increment(1)
                }
            }
        }
        assertEquals(44, counterValue.receive())

        increment1.cancel()
        increment2.cancel()
        updates.cancel()
    }

    private fun createWriteCopyLocalConfig(
        name: String,
        directory: String = PlatformUtils.createTempDir(),
        encryptionKey: ByteArray? = null,
        schemaVersion: Long = 0
    ): RealmConfiguration {
        val builder = RealmConfiguration.Builder(
            schema = setOf(
                SyncObjectWithAllTypes::class,
                FlexParentObject::class,
                FlexChildObject::class,
                FlexEmbeddedObject::class
            )
        ).directory(directory)
            .schemaVersion(schemaVersion)
            .name(name)
        if (encryptionKey != null) {
            builder.encryptionKey(encryptionKey)
        }
        return builder.build()
    }

    @Test
    fun writeCopyTo_localToPartitionBasedSync() = runBlocking {
        val (email1, password1) = randomEmail() to "password1234"
        val (email2, password2) = randomEmail() to "password1234"
        val user1 = app.createUserAndLogIn(email1, password1)
        val user2 = app.createUserAndLogIn(email2, password2)
        val localConfig = createWriteCopyLocalConfig("local.realm")
        val partitionValue = TestHelper.randomPartitionValue()
        val syncConfig1 = createSyncConfig(
            user = user1,
            name = "sync1.realm",
            partitionValue = partitionValue,
            schema = setOf(SyncObjectWithAllTypes::class)
        )
        val syncConfig2 = createSyncConfig(
            user = user2,
            name = "sync2.realm",
            partitionValue = partitionValue,
            schema = setOf(SyncObjectWithAllTypes::class)
        )
        Realm.open(localConfig).use { localRealm ->
            localRealm.writeBlocking {
                copyToRealm(
                    SyncObjectWithAllTypes().apply {
                        stringField = "local object"
                    }
                )
            }
            // Copy to partition-based Realm
            localRealm.writeCopyTo(syncConfig1)
        }
        // Open Sync Realm and ensure that data can be used and uploaded
        Realm.open(syncConfig1).use { syncRealm1: Realm ->
            assertEquals(1, syncRealm1.query<SyncObjectWithAllTypes>().count().find())
            assertEquals(
                "local object",
                syncRealm1.query<SyncObjectWithAllTypes>().first().find()!!.stringField
            )
            syncRealm1.writeBlocking {
                query<SyncObjectWithAllTypes>().first().find()!!.apply {
                    stringField = "updated local object"
                }
            }
            syncRealm1.syncSession.uploadAllLocalChanges(30.seconds)
        }
        // Check that uploaded data can be used
        Realm.open(syncConfig2).use { syncRealm2: Realm ->
            val obj = syncRealm2.query<SyncObjectWithAllTypes>().asFlow()
                .first { it.list.size == 1 }
                .list
                .first()
            assertEquals("updated local object", obj.stringField)
        }
    }

    @Test
    fun writeCopyTo_localToFlexibleSync_throws() = runBlocking {
        val flexApp = TestApp(
            appName = io.realm.kotlin.test.mongodb.TEST_APP_FLEX,
            builder = {
                it.syncRootDirectory(PlatformUtils.createTempDir("flx-sync-"))
            }
        )
        val (email1, password1) = randomEmail() to "password1234"
        val user1 = flexApp.createUserAndLogIn(email1, password1)
        val localConfig = createWriteCopyLocalConfig("local.realm")
        val flexSyncConfig = createFlexibleSyncConfig(
            user = user1,
            schema = setOf(
                FlexParentObject::class,
                FlexChildObject::class,
                FlexEmbeddedObject::class
            )
        )
        Realm.open(localConfig).use { localRealm ->
            localRealm.writeBlocking {
                copyToRealm(
                    SyncObjectWithAllTypes().apply {
                        stringField = "local object"
                    }
                )
            }
            assertFailsWith<IllegalArgumentException> {
                localRealm.writeCopyTo(flexSyncConfig)
            }
        }
        flexApp.close()
    }

    @Test
    fun writeCopyTo_partitionBasedToLocal() = runBlocking {
        val (email, password) = randomEmail() to "password1234"
        val user = app.createUserAndLogIn(email, password)
        val dir = PlatformUtils.createTempDir()
        val localConfig = createWriteCopyLocalConfig("local.realm", directory = dir)
        val migratedLocalConfig =
            createWriteCopyLocalConfig("local.realm", directory = dir, schemaVersion = 1)
        val partitionValue = TestHelper.randomPartitionValue()
        val syncConfig = createSyncConfig(
            user = user,
            name = "sync1.realm",
            partitionValue = partitionValue,
            schema = setOf(SyncObjectWithAllTypes::class)
        )
        Realm.open(syncConfig).use { syncRealm ->
            // Write local data
            syncRealm.writeBlocking {
                copyToRealm(
                    SyncObjectWithAllTypes().apply {
                        stringField = "local object"
                    }
                )
            }

            // Ensure that we have have synchronized the server schema, including the
            // partition field.
            syncRealm.syncSession.uploadAllLocalChanges(30.seconds)
            syncRealm.syncSession.downloadAllServerChanges(30.seconds)

            // Copy to partition-based Realm
            syncRealm.writeCopyTo(localConfig)
        }

        // Opening the local Realm with the same schema will throw a schema mismatch, because
        // the server schema contains classes and fields not in the local schema.
        assertFailsWith<IllegalStateException> {
            Realm.open(localConfig)
        }

        // Opening with a migration should work fine
        Realm.open(migratedLocalConfig).use { localRealm: Realm ->
            assertEquals(1, localRealm.query<SyncObjectWithAllTypes>().count().find())
            assertEquals(
                "local object",
                localRealm.query<SyncObjectWithAllTypes>().first().find()!!.stringField
            )
        }
    }

    @Test
    fun writeCopyTo_flexibleSyncToLocal() = runBlocking {
        val flexApp = TestApp(
            appName = io.realm.kotlin.test.mongodb.TEST_APP_FLEX,
            builder = {
                it.syncRootDirectory(PlatformUtils.createTempDir("flx-sync-"))
            }
        )
        val (email1, password1) = randomEmail() to "password1234"
        val user = flexApp.createUserAndLogIn(email1, password1)
        val localConfig = createWriteCopyLocalConfig("local.realm")
        val syncConfig = createSyncConfig(
            user = user,
            name = "sync.realm",
            partitionValue = partitionValue,
            schema = setOf(
                FlexParentObject::class,
                FlexChildObject::class,
                FlexEmbeddedObject::class
            )
        )
        Realm.open(syncConfig).use { flexSyncRealm: Realm ->
            flexSyncRealm.writeBlocking {
                copyToRealm(
                    FlexParentObject().apply {
                        name = "local object"
                    }
                )
            }
            // Copy to local Realm
            flexSyncRealm.writeCopyTo(localConfig)
        }
        // Open Local Realm and check that data can read.
        Realm.open(localConfig).use { localRealm: Realm ->
            assertEquals(1, localRealm.query<FlexParentObject>().count().find())
            assertEquals("local object", localRealm.query<FlexParentObject>().first().find()!!.name)
        }
        flexApp.close()
    }

    @Test
    fun writeCopyTo_partitionBasedToDifferentPartitionKey() = runBlocking {
        val (email1, password1) = randomEmail() to "password1234"
        val (email2, password2) = randomEmail() to "password1234"
        val user1 = app.createUserAndLogIn(email1, password1)
        val user2 = app.createUserAndLogIn(email2, password2)
        val syncConfig1 = createSyncConfig(
            user = user1,
            name = "sync1.realm",
            partitionValue = TestHelper.randomPartitionValue(),
            schema = setOf(SyncObjectWithAllTypes::class)
        )
        val syncConfig2 = createSyncConfig(
            user = user2,
            name = "sync2.realm",
            partitionValue = TestHelper.randomPartitionValue(),
            schema = setOf(SyncObjectWithAllTypes::class)
        )
        Realm.open(syncConfig1).use { syncRealm1 ->
            syncRealm1.writeBlocking {
                copyToRealm(
                    SyncObjectWithAllTypes().apply {
                        stringField = "local object"
                    }
                )
            }
            // Copy to partition-based Realm
            syncRealm1.syncSession.uploadAllLocalChanges(30.seconds)
            // Work-around for https://github.com/realm/realm-core/issues/4865
            // Calling syncRealm1.syncSession.downloadAllServerChanges doesn't seem to
            // fix it in all cases
            delay(1000)
            syncRealm1.writeCopyTo(syncConfig2)
        }
        // Open Sync Realm and ensure that data can be used and uploaded
        Realm.open(syncConfig2).use { syncRealm2: Realm ->
            val result = syncRealm2.query<SyncObjectWithAllTypes>().find()
            assertEquals(1, result.size)
            assertEquals("local object", result.first().stringField)
            syncRealm2.syncSession.uploadAllLocalChanges(30.seconds)
        }
    }

    @Test
    fun writeCopyTo_partitionBasedToSamePartitionKey() = runBlocking {
        val (email1, password1) = randomEmail() to "password1234"
        val (email2, password2) = randomEmail() to "password1234"
        val user1 = app.createUserAndLogIn(email1, password1)
        val user2 = app.createUserAndLogIn(email2, password2)
        val partitionValue = TestHelper.randomPartitionValue()
        val syncConfig1 = createSyncConfig(
            user = user1,
            name = "sync1.realm",
            partitionValue = partitionValue,
            schema = setOf(SyncObjectWithAllTypes::class)
        )
        val syncConfig2 = createSyncConfig(
            user = user2,
            name = "sync2.realm",
            partitionValue = partitionValue,
            schema = setOf(SyncObjectWithAllTypes::class)
        )
        Realm.open(syncConfig1).use { syncRealm1 ->
            // Write local data
            syncRealm1.writeBlocking {
                copyToRealm(
                    SyncObjectWithAllTypes().apply {
                        stringField = "local object"
                    }
                )
            }
            // Copy to partition-based Realm
            syncRealm1.syncSession.uploadAllLocalChanges(30.seconds)
            // Work-around for https://github.com/realm/realm-core/issues/4865
            // Calling syncRealm1.syncSession.downloadAllServerChanges doesn't seem to
            // fix it in all cases
            delay(1000)
            syncRealm1.writeCopyTo(syncConfig2)
        }
        // Open Sync Realm and ensure that data can be used and uploaded
        Realm.open(syncConfig2).use { syncRealm2: Realm ->
            val result = syncRealm2.query<SyncObjectWithAllTypes>().find()
            assertEquals(1, result.size)
            assertEquals("local object", result.first().stringField)
            syncRealm2.syncSession.uploadAllLocalChanges(30.seconds)
        }
    }

    @Test
    fun writeCopyTo_flexibleSyncToFlexibleSync() = runBlocking {
        val flexApp = TestApp(
            logLevel = io.realm.kotlin.log.LogLevel.ALL,
            appName = io.realm.kotlin.test.mongodb.TEST_APP_FLEX,
            builder = {
                it.syncRootDirectory(PlatformUtils.createTempDir("flx-sync-"))
            }
        )
        val section = Random.nextInt()
        val (email1, password1) = randomEmail() to "password1234"
        val (email2, password2) = randomEmail() to "password1234"
        val user1 = flexApp.createUserAndLogIn(email1, password1)
        val user2 = flexApp.createUserAndLogIn(email2, password2)
        val syncConfig1 = createFlexibleSyncConfig(
            user = user1,
            name = "sync1.realm",
            errorHandler = { _, error ->
                fail(error.toString())
            },
            schema = setOf(
                FlexParentObject::class,
                FlexChildObject::class,
                FlexEmbeddedObject::class
            ),
            initialSubscriptions = { realm: Realm ->
                realm.query<FlexParentObject>("section = $0", section).subscribe(name = "parentSubscription")
            }
        )
        val syncConfig2 = createFlexibleSyncConfig(
            user = user2,
            name = "sync2.realm",
            errorHandler = { _, error ->
                fail(error.toString())
            },
            schema = setOf(
                FlexParentObject::class,
                FlexChildObject::class,
                FlexEmbeddedObject::class
            )
        )

        Realm.open(syncConfig1).use { flexRealm1: Realm ->
            // It is not possible to use `writeCopyTo` if data is written to the Realm before
            // the SubscriptionSet is `COMPLETE`. Work around the issue for now.
            flexRealm1.subscriptions.waitForSynchronization(30.seconds)
            flexRealm1.write {
                copyToRealm(
                    FlexParentObject(section).apply {
                        name = "User1Object"
                    }
                )
            }
            flexRealm1.syncSession.uploadAllLocalChanges(30.seconds)
            assertEquals(SubscriptionSetState.COMPLETE, flexRealm1.subscriptions.state)
            // Copy to another flex RealmRealm
            flexRealm1.writeCopyTo(syncConfig2)
            assertTrue(fileExists(syncConfig2.path))

            // Open the copied Realm and verify we can read and write data
            Realm.open(syncConfig2).use { flexRealm2: Realm ->
                // Subscriptions are copied
                assertEquals(1, flexRealm2.subscriptions.size)
                assertEquals("parentSubscription", flexRealm2.subscriptions.first().name)
                assertEquals(SubscriptionSetState.COMPLETE, flexRealm2.subscriptions.state)

                // As is data
                assertEquals(1, flexRealm2.query<FlexParentObject>().count().find())
                assertEquals("User1Object", flexRealm2.query<FlexParentObject>().first().find()!!.name)

                flexRealm2.subscriptions.waitForSynchronization(30.seconds)
                flexRealm2.write {
                    copyToRealm(
                        FlexParentObject(section).apply {
                            name = "User2Object"
                        }
                    )
                }
                flexRealm2.syncSession.uploadAllLocalChanges(30.seconds)
                assertEquals(2, flexRealm2.query<FlexParentObject>().count().find())
            }
        }
        flexApp.close()
    }

    @Test
    fun writeCopyTo_dataNotUploaded_throws() = runBlocking {
        val (email1, password1) = randomEmail() to "password1234"
        val user1 = app.createUserAndLogIn(email1, password1)
        val syncConfigA = createSyncConfig(
            user = user1,
            name = "a.realm",
            partitionValue = TestHelper.randomPartitionValue(),
            schema = setOf(SyncObjectWithAllTypes::class)
        )
        val syncConfigB = createSyncConfig(
            user = user1,
            name = "b.realm",
            partitionValue = TestHelper.randomPartitionValue(),
            schema = setOf(SyncObjectWithAllTypes::class)
        )
        Realm.open(syncConfigA).use { realm ->
            realm.syncSession.pause()
            realm.writeBlocking {
                copyToRealm(SyncObjectWithAllTypes())
            }
            assertFailsWith<IllegalStateException> {
                realm.writeCopyTo(syncConfigB)
            }
        }
    }

    // Test for https://github.com/realm/realm-kotlin/issues/1068
    // Note, this test is not 100% sure to surface the bug, but manual testing has shown that it
    // works well enough. Also, even if it doesn't surface the bug, it will not the fail the test.
    @Test
    fun accessSessionAfterRemoteChange() = runBlocking {
        val flexApp = TestApp(
            appName = io.realm.kotlin.test.mongodb.TEST_APP_FLEX,
            builder = {
                it.syncRootDirectory(PlatformUtils.createTempDir("flx-sync-"))
            }
        )
        val section = Random.nextInt()
        val (email1, password1) = randomEmail() to "password1234"
        val (email2, password2) = randomEmail() to "password1234"
        val user1 = flexApp.createUserAndLogIn(email1, password1)
        val user2 = flexApp.createUserAndLogIn(email2, password2)
        val syncConfig1 = createFlexibleSyncConfig(
            user = user1,
            name = "sync1.realm",
            initialSubscriptions = { realm: Realm ->
                realm.query<FlexParentObject>("section = $0", section).subscribe()
            }
        )
        val syncConfig2 = createFlexibleSyncConfig(
            user = user2,
            name = "sync2.realm",
            initialSubscriptions = { realm: Realm ->
                realm.query<FlexParentObject>("section = $0", section).subscribe()
            }
        )
        val realm1 = Realm.open(syncConfig1)

        Realm.open(syncConfig2).use { realm2 ->
            realm2.write {
                copyToRealm(FlexParentObject(section))
            }
            realm2.syncSession.uploadAllLocalChanges()
        }

        // Reading the object means we received it from the other Realm
        withTimeout(30.seconds) {
            val obj: FlexParentObject = realm1.query<FlexParentObject>("section = $0", section).asFlow()
                .map { it.list }
                .filter { it.isNotEmpty() }
                .first().first()
            assertEquals(section, obj.section)

            // 1. Local write to work around https://github.com/realm/realm-kotlin/issues/1070
            realm1.write { }

            // 2. Trigger GC. This will GC the RealmReference JVM object, making the native reference
            //    eligible for closing.
            PlatformUtils.triggerGC()

            // 3. On the next update of Realm, we run through the weak list of all previous
            //    RealmReferences and close all native pointers with their JVM object GC'ed.
            //    This should now include the object created in step 1.
            realm1.write { }
        }

        // 4. With the original native dbPointer now being closed, accessing the syncSession for
        //    the first time should still work.
        try {
            realm1.syncSession.pause()
            assertEquals(SyncSession.State.PAUSED, realm1.syncSession.state)
        } finally {
            realm1.close()
            flexApp.close()
        }
    }

    /**
     * Logged collecting all logs it has seen.
     */
    private class CustomLogCollector(
        override val tag: String,
        override val level: LogLevel
    ) : RealmLogger {

        private val mutex = Mutex()
        private val _logs = mutableListOf<String>()
        /**
         * Returns a snapshot of the current state of the logs.
         */
        val logs: List<String>
            get() = runBlocking {
                mutex.withLock {
                    _logs.toList()
                }
            }

        override fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?) {
            val logMessage: String = message!!
            runBlocking {
                mutex.withLock {
                    _logs.add(logMessage)
                }
            }
        }
    }

    @Test
    fun customLoggersReceiveSyncLogs() = runBlocking {
        val customLogger = CustomLogCollector("CUSTOM", LogLevel.DEBUG)
        val section = Random.nextInt()
        val flexApp = TestApp(
            appName = io.realm.kotlin.test.mongodb.TEST_APP_FLEX,
            builder = {
                it.syncRootDirectory(PlatformUtils.createTempDir("flx-sync-"))
                it.log(level = LogLevel.DEBUG, listOf(customLogger))
                it.appName("MyCustomApp")
                it.appVersion("1.0.0")
            }
        )
        val (email, password) = randomEmail() to "password1234"
        val user = flexApp.createUserAndLogIn(email, password)
        val syncConfig = createFlexibleSyncConfig(
            user = user,
            name = "flex.realm",
            initialSubscriptions = { realm: Realm ->
                realm.query<FlexParentObject>("section = $0", section).subscribe()
            }
        )
        Realm.open(syncConfig).use { flexSyncRealm: Realm ->
            flexSyncRealm.writeBlocking {
                copyToRealm(
                    FlexParentObject().apply {
                        name = "local object"
                    }
                )
            }
            flexSyncRealm.syncSession.uploadAllLocalChanges()
        }
        assertTrue(customLogger.logs.isNotEmpty())
        assertTrue(customLogger.logs.any { it.contains("Connection[1]: Negotiated protocol version:") })
        assertTrue(customLogger.logs.any { it.contains("MyCustomApp/1.0.0") })
        flexApp.close()
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
        schema: Set<KClass<out BaseRealmObject>> = setOf(ParentPk::class, ChildPk::class),
    ): SyncConfiguration = SyncConfiguration.Builder(
        schema = schema,
        user = user,
        partitionValue = partitionValue
    ).name(name).also { builder ->
        if (encryptionKey != null) builder.encryptionKey(encryptionKey)
        if (errorHandler != null) builder.errorHandler(errorHandler)
        if (log != null) builder.log(log.level, log.loggers)
    }.build()

    @Suppress("LongParameterList")
    private fun createFlexibleSyncConfig(
        user: User,
        name: String = DEFAULT_NAME,
        encryptionKey: ByteArray? = null,
        log: LogConfiguration? = null,
        errorHandler: ErrorHandler? = null,
        schema: Set<KClass<out BaseRealmObject>> = setOf(
            FlexParentObject::class,
            FlexChildObject::class,
            FlexEmbeddedObject::class
        ),
        initialSubscriptions: InitialSubscriptionsCallback? = null
    ): SyncConfiguration = SyncConfiguration.Builder(
        user = user,
        schema = schema
    ).name(name).also { builder ->
        if (encryptionKey != null) builder.encryptionKey(encryptionKey)
        if (errorHandler != null) builder.errorHandler(errorHandler)
        if (log != null) builder.log(log.level, log.loggers)
        if (initialSubscriptions != null) builder.initialSubscriptions(false, initialSubscriptions)
    }.build()
}
