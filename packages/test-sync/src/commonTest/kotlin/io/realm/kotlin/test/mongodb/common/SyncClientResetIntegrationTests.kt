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
@file:Suppress("invisible_member", "invisible_reference") // Needed to call session.simulateError()

package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.entities.sync.SyncPerson
import io.realm.kotlin.entities.sync.flx.FlexChildObject
import io.realm.kotlin.entities.sync.flx.FlexEmbeddedObject
import io.realm.kotlin.entities.sync.flx.FlexParentObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.interop.sync.ProtocolClientErrorCode
import io.realm.kotlin.internal.interop.sync.SyncErrorCodeCategory
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.ClientResetRequiredException
import io.realm.kotlin.mongodb.internal.SyncSessionImpl
import io.realm.kotlin.mongodb.sync.DiscardUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.ManuallyRecoverUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.RecoverOrDiscardUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.RecoverUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncMode
import io.realm.kotlin.mongodb.sync.SyncSession
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TEST_APP_PARTITION
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.addEmailProvider
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.initializeFlexibleSync
import io.realm.kotlin.test.mongodb.util.TestAppInitializer.initializePartitionSync
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class SyncClientResetIntegrationTests {

    /**
     * Defines a Client Reset testing environment. This class is used for both PBS and FLX since
     * individual tests perform the same assertions, whether a PBS or a FLX app.
     *
     * Things to take into account:
     * - [insertElement] is used to populate the realm and also to write objects during Client Reset
     * to test the different states of the before and after realm instances.
     * - [recoverData] does all things recovery: query for the object we expect to be in the before
     * realm and copies the data to the after realm.
     * - [configBuilderGenerator] allows custom configuration of the [TestApp]
     * - [recoveryDisabled] indicates whether or not the app to be created should have recovery mode
     * enabled.
     */
    @Suppress("LongParameterList")
    class TestEnvironment<T : RealmObject> constructor(
        val clazz: KClass<T>,
        val appName: String,
        val syncMode: SyncMode,
        val recoveryDisabled: Boolean,
        val configBuilderGenerator: (user: User) -> SyncConfiguration.Builder,
        val insertElement: (realm: Realm) -> Unit,
        val recoverData: (before: TypedRealm, after: MutableRealm) -> Unit,
    ) {
        private fun newChannel(): Channel<ResultsChange<out RealmObject>> = Channel(1)
        private fun getObjects(realm: TypedRealm): RealmQuery<T> = realm.query(clazz)
        private fun countObjects(realm: TypedRealm): Long = getObjects(realm).count().find()
        private val logChannel: Channel<ClientResetLogEvents> = Channel(5)

        /**
         * Runs the test. Steps as follows: create an app, create a user, log in execute your test
         * logic.
         */
        fun performTest(
            block: TestEnvironment<T>.(
                syncMode: SyncMode,
                app: TestApp,
                user: User,
                builder: SyncConfiguration.Builder
            ) -> Unit
        ) {
            val app = TestApp(
                appName = appName,
                logLevel = LogLevel.INFO,
                customLogger = ClientResetLoggerInspector(logChannel),
                initialSetup = { app, service ->
                    addEmailProvider(app)
                    when (syncMode) {
                        SyncMode.PARTITION_BASED ->
                            initializePartitionSync(app, service, recoveryDisabled)
                        SyncMode.FLEXIBLE ->
                            initializeFlexibleSync(app, service, recoveryDisabled)
                    }
                }
            )
            try {
                val (email, password) = TestHelper.randomEmail() to "password1234"
                val user = runBlocking {
                    app.createUserAndLogIn(email, password)
                }

                block(syncMode, app, user, configBuilderGenerator(user))
            } finally {
                app.close()
            }
        }
    }

    companion object {

        private val defaultTimeout = 1.minutes

        /**
         * Factory for FLX testing environments.
         */
        private fun createFlxEnvironment(
            appName: String,
            openRealmTimeout: Duration,
            recoveryDisabled: Boolean = false
        ): TestEnvironment<out RealmObject> {
            val section = Random.nextInt()
            return TestEnvironment(
                clazz = FlexParentObject::class,
                appName = appName,
                syncMode = SyncMode.FLEXIBLE,
                recoveryDisabled = recoveryDisabled,
                configBuilderGenerator = { user ->
                    return@TestEnvironment SyncConfiguration.Builder(
                        user,
                        setOf(
                            FlexParentObject::class,
                            FlexChildObject::class,
                            FlexEmbeddedObject::class
                        )
                    ).initialSubscriptions { realm ->
                        realm.query<FlexParentObject>(
                            "section = $0 AND name = $1",
                            section,
                            "blue"
                        ).also { add(it) }
                    }.waitForInitialRemoteData(openRealmTimeout)
                },
                insertElement = { realm: Realm ->
                    realm.writeBlocking {
                        copyToRealm(
                            FlexParentObject().apply {
                                this.section = section
                                this.name = "blue"
                            }
                        )
                    }
                },
                recoverData = { before: TypedRealm, after: MutableRealm ->
                    // Perform manual copy
                    // see https://github.com/realm/realm-kotlin/issues/868
                    after.copyToRealm(
                        FlexParentObject().apply {
                            assertNotNull(
                                before.query<FlexParentObject>().first().find()
                            ).let {
                                // Perform manual copy
                                // see https://github.com/realm/realm-kotlin/issues/868
                                this._id = it._id
                                this.section = it.section
                                this.name = it.name
                                this.age = it.age
                            }
                        }
                    )
                }
            )
        }

        /**
         * Factory for PBS testing environments.
         */
        private fun createPbsEnvironment(
            appName: String,
            recoveryDisabled: Boolean = false
        ): TestEnvironment<out RealmObject> = TestEnvironment(
            clazz = SyncPerson::class,
            appName = appName,
            syncMode = SyncMode.PARTITION_BASED,
            recoveryDisabled = recoveryDisabled,
            configBuilderGenerator = { user ->
                return@TestEnvironment SyncConfiguration.Builder(
                    user,
                    TestHelper.randomPartitionValue(),
                    schema = setOf(SyncPerson::class)
                )
            },
            insertElement = { realm: Realm ->
                realm.writeBlocking {
                    copyToRealm(SyncPerson())
                }
            },
            recoverData = { before: TypedRealm, after: MutableRealm ->
                after.copyToRealm(
                    SyncPerson().apply {
                        assertNotNull(
                            before.query<SyncPerson>().first().find()
                        ).let {
                            // Perform manual copy
                            // see https://github.com/realm/realm-kotlin/issues/868
                            this._id = it._id
                            this.age = it.age
                            this.firstName = it.firstName
                            this.lastName = it.lastName
                        }
                    }
                )
            }
        )

        /**
         * Starts a test for PBS given an environment and a custom test logic.
         */
        fun performPbsTest(
            environment: TestEnvironment<out RealmObject>? = null,
            block: TestEnvironment<out RealmObject>.(
                syncMode: SyncMode,
                app: TestApp,
                user: User,
                builder: SyncConfiguration.Builder
            ) -> Unit
        ) {
            environment ?: createPbsEnvironment(TEST_APP_PARTITION)
                .also {
                    it.performTest(block)
                }
        }

        /**
         * Starts a test for FLX given an environment and a custom test logic.
         */
        fun performFlxTest(
            environment: TestEnvironment<out RealmObject>? = null,
            openRealmTimeout: Duration = defaultTimeout,
            block: TestEnvironment<out RealmObject>.(
                syncMode: SyncMode,
                app: TestApp,
                user: User,
                builder: SyncConfiguration.Builder
            ) -> Unit
        ) {
            environment ?: createFlxEnvironment(TEST_APP_FLEX, openRealmTimeout)
                .also {
                    it.performTest(block)
                }
        }
    }

    private enum class ClientResetEvents {
        ON_BEFORE_RESET,
        ON_AFTER_RESET,
        ON_AFTER_RECOVERY,
        ON_AFTER_DISCARD,
        ON_MANUAL_RESET_FALLBACK
    }

    private enum class ClientResetLogEvents {
        DISCARD_LOCAL_ON_BEFORE_RESET,
        DISCARD_LOCAL_ON_AFTER_RECOVERY,
        DISCARD_LOCAL_ON_AFTER_DISCARD,
        DISCARD_LOCAL_ON_ERROR,
        MANUAL_ON_ERROR
    }

    /**
     * This class allows us to inspect if the default client reset strategies actually log the
     * client reset events.
     */
    private class ClientResetLoggerInspector(
        val channel: Channel<ClientResetLogEvents>
    ) : RealmLogger {

        override val level: LogLevel
            get() = LogLevel.WARN
        override val tag: String
            get() = "SyncClientResetIntegrationTests"

        override fun log(
            level: LogLevel,
            throwable: Throwable?,
            message: String?,
            vararg args: Any?
        ) {
            message?.let {
                if (message.contains("Client reset: attempting to automatically recover unsynced changes in Realm:")) {
                    channel.trySend(ClientResetLogEvents.DISCARD_LOCAL_ON_BEFORE_RESET)
                } else if (message.contains("Client reset: successfully recovered all unsynced changes in Realm:")) {
                    channel.trySend(ClientResetLogEvents.DISCARD_LOCAL_ON_AFTER_RECOVERY)
                } else if (message.contains("Client reset: couldn't recover successfully, all unsynced changes were discarded in Realm:")) {
                    channel.trySend(ClientResetLogEvents.DISCARD_LOCAL_ON_AFTER_DISCARD)
                } else if (message.contains("Client reset: manual reset required for Realm in")) {
                    channel.trySend(ClientResetLogEvents.DISCARD_LOCAL_ON_ERROR)
                } else if (message.contains("Client reset required on Realm:")) {
                    channel.trySend(ClientResetLogEvents.MANUAL_ON_ERROR)
                } else {
                    // Ignore
                }
            }
        }
    }

    private lateinit var initialLogLevel: LogLevel
    private lateinit var partitionValue: String

    @BeforeTest
    fun setup() {
        initialLogLevel = RealmLog.level
        partitionValue = TestHelper.randomPartitionValue()
    }

    @AfterTest
    fun tearDown() {
        RealmLog.removeAll()
        RealmLog.addDefaultSystemLogger()
        RealmLog.level = initialLogLevel
    }

    // ---------------------------------------------------------------------------------------
    // DiscardUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun discardUnsyncedChanges_discards_pbs() {
        performPbsTest { syncMode, app, user, builder ->
            discardUnsyncedChanges_discards(syncMode, app, user, builder)
        }
    }

    @Test
    fun discardUnsyncedChanges_discards_flx() {
        performFlxTest { syncMode, app, user, builder ->
            discardUnsyncedChanges_discards(syncMode, app, user, builder)
        }
    }

    private fun TestEnvironment<*>.discardUnsyncedChanges_discards(
        syncMode: SyncMode,
        app: TestApp,
        user: User,
        builder: SyncConfiguration.Builder
    ) {
        // Validate that the discard local strategy onBeforeReset and onAfterReset callbacks
        // are invoked successfully when a client reset is triggered.
        val channel = Channel<ClientResetEvents>(2)
        val config = builder.syncClientResetStrategy(
            object : DiscardUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    // This realm contains something as we wrote an object while the session was paused
                    assertEquals(1, countObjects(realm))
                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                }

                override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                    // The before-Realm contains the object we wrote while the session was paused
                    assertEquals(1, countObjects(before))

                    // The after-Realm contains no objects
                    assertEquals(0, countObjects(after))

                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_AFTER_RESET)
                }

                override fun onError(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    fail("onError shouldn't be called: ${exception.message}")
                }

                override fun onManualResetFallback(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    fail("onManualResetFallback shouldn't be called: ${exception.message}")
                }
            }
        ).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                // This channel helps to validate that the Realm gets updated
                val objectChannel: Channel<ResultsChange<out RealmObject>> = newChannel()

                val job = async {
                    getObjects(realm)
                        .asFlow()
                        .collect {
                            objectChannel.trySend(it)
                        }
                }

                // No initial data
                assertEquals(0, objectChannel.receiveOrFail().list.size)

                app.triggerClientReset(syncMode, realm.syncSession, user.id) {
                    insertElement(realm)
                    assertEquals(1, objectChannel.receiveOrFail().list.size)
                }

                // Validate that the client reset was triggered successfully
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receiveOrFail())
                assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receiveOrFail())

                // TODO We must not need this. Force updating the instance pointer.
                realm.write { }

                // Validate Realm instance has been correctly updated
                assertEquals(0, objectChannel.receiveOrFail().list.size)

                job.cancel()
            }
        }
    }

    @Test
    fun discardUnsyncedChanges_discards_attemptRecover_pbs() {
        performPbsTest { syncMode, app, user, builder ->
            discardUnsyncedChanges_discards_attemptRecover(syncMode, app, user, builder)
        }
    }

    @Test
    fun discardUnsyncedChanges_discards_attemptRecover_flx() {
        performFlxTest { syncMode, app, user, builder ->
            discardUnsyncedChanges_discards_attemptRecover(syncMode, app, user, builder)
        }
    }

    private fun TestEnvironment<*>.discardUnsyncedChanges_discards_attemptRecover(
        syncMode: SyncMode,
        app: TestApp,
        user: User,
        builder: SyncConfiguration.Builder
    ) {
        // Attempts to recover data if a client reset is triggered.
        val channel = Channel<ClientResetEvents>(2)
        val config = builder.syncClientResetStrategy(
            object : DiscardUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                }

                override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                    // The before-Realm contains the object we wrote while the session was paused
                    assertEquals(1, countObjects(before))

                    recoverData(before, after)

                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_AFTER_RESET)
                }

                override fun onError(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    fail("onError shouldn't be called: ${exception.message}")
                }

                override fun onManualResetFallback(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    fail("onManualResetFallback shouldn't be called: ${exception.message}")
                }
            }
        ).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                // This channel helps to validate that the Realm gets updated
                val objectChannel: Channel<ResultsChange<out RealmObject>> = newChannel()

                val job = async {
                    getObjects(realm)
                        .asFlow()
                        .collect {
                            objectChannel.trySend(it)
                        }
                }

                // No initial data
                assertEquals(0, objectChannel.receiveOrFail().list.size)

                app.triggerClientReset(syncMode, realm.syncSession, user.id) {
                    // Write something while the session is paused to make sure the before realm contains something
                    insertElement(realm)
                    assertEquals(1, objectChannel.receiveOrFail().list.size)
                }

                // Validate that the client reset was triggered successfully
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receiveOrFail())
                assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receiveOrFail())

                // TODO We must not need this. Force updating the instance pointer.
                realm.write { }

                // Validate Realm instance has been correctly updated
                assertEquals(1, objectChannel.receiveOrFail().list.size)

                job.cancel()
            }
        }
    }

    @Test
    fun discardUnsyncedChanges_failure_pbs() {
        performPbsTest { _, _, _, builder ->
            discardUnsyncedChanges_failure_pbs(builder)
        }
    }

    @Test
    fun discardUnsyncedChanges_failure_flx() {
        performFlxTest { _, _, _, builder ->
            discardUnsyncedChanges_failure_pbs(builder)
        }
    }

    private fun discardUnsyncedChanges_failure_pbs(builder: SyncConfiguration.Builder) {
        // Validate that the discard local strategy onError callback is invoked successfully if
        // a client reset fails.
        // Channel size is 2 because both onError and onManualResetFallback are called
        val channel = Channel<ClientResetEvents>(2)
        val config = builder.syncClientResetStrategy(object : DiscardUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                fail("Should not call onBeforeReset")
            }

            override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                fail("Should not call onAfterReset")
            }

            override fun onError(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                // Just notify the callback has been invoked, do the assertions in onManualResetFallback
                channel.trySend(ClientResetEvents.ON_MANUAL_RESET_FALLBACK)
            }

            override fun onManualResetFallback(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                val originalFilePath = assertNotNull(exception.originalFilePath)
                val recoveryFilePath = assertNotNull(exception.recoveryFilePath)
                assertTrue(fileExists(originalFilePath))
                assertFalse(fileExists(recoveryFilePath))
                // Note, this error message is just the one created by ObjectStore for
                // testing the server will send a different message. This just ensures that
                // we don't accidentally modify or remove the message.
                assertEquals(
                    "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed.",
                    exception.message
                )

                channel.trySend(ClientResetEvents.ON_MANUAL_RESET_FALLBACK)
            }
        }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                with(realm.syncSession as SyncSessionImpl) {
                    simulateError(
                        ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                    )

                    // TODO Twice until the deprecated method is removed
                    assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receiveOrFail())
                    assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receiveOrFail())
                }
            }
        }
    }

    @Test
    fun discardUnsyncedChanges_executeClientReset_pbs() = runBlocking {
        performPbsTest { _, _, _, builder ->
            discardUnsyncedChanges_executeClientReset(builder)
        }
    }

    @Test
    fun discardUnsyncedChanges_executeClientReset_flx() = runBlocking {
        performFlxTest { _, _, _, builder ->
            discardUnsyncedChanges_executeClientReset(builder)
        }
    }

    private fun discardUnsyncedChanges_executeClientReset(
        builder: SyncConfiguration.Builder
    ) {
        // Channel size is 2 because both onError and onManualResetFallback are called
        val channel = Channel<ClientResetEvents>(2)
        val config = builder.syncClientResetStrategy(object : DiscardUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                fail("Should not call onBeforeReset")
            }

            override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                fail("Should not call onAfterReset")
            }

            override fun onError(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                // Just notify the callback has been invoked, do the assertions in onManualResetFallback
                channel.trySend(ClientResetEvents.ON_MANUAL_RESET_FALLBACK)
            }

            override fun onManualResetFallback(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                val originalFilePath = assertNotNull(exception.originalFilePath)
                val recoveryFilePath = assertNotNull(exception.recoveryFilePath)
                assertTrue(fileExists(originalFilePath))
                assertFalse(fileExists(recoveryFilePath))

                exception.executeClientReset()

                // Validate that files have been moved after explicit reset
                assertFalse(fileExists(originalFilePath))
                assertTrue(fileExists(recoveryFilePath))

                channel.trySend(ClientResetEvents.ON_MANUAL_RESET_FALLBACK)
            }
        }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                with(realm.syncSession as SyncSessionImpl) {
                    simulateError(
                        ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                    )

                    // TODO Twice until the deprecated method is removed
                    assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receiveOrFail())
                    assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receiveOrFail())
                }
            }
        }
    }

    @Test
    fun discardUnsyncedChanges_userExceptionCaptured_onBeforeReset_pbs() {
        performPbsTest { syncMode, app, user, builder ->
            discardUnsyncedChanges_userExceptionCaptured_onBeforeReset(syncMode, app, user, builder)
        }
    }

    @Test
    fun discardUnsyncedChanges_userExceptionCaptured_onBeforeReset_flx() {
        performFlxTest { syncMode, app, user, builder ->
            discardUnsyncedChanges_userExceptionCaptured_onBeforeReset(syncMode, app, user, builder)
        }
    }

    private fun discardUnsyncedChanges_userExceptionCaptured_onBeforeReset(
        syncMode: SyncMode,
        app: TestApp,
        user: User,
        builder: SyncConfiguration.Builder
    ) {
        // Validates that any user exception during the automatic client reset is properly captured.
        val channel = Channel<ClientResetEvents>(3)
        val config = builder.syncClientResetStrategy(object : DiscardUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                // Notify that this callback has been invoked
                channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                throw IllegalStateException("User exception")
            }

            override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                // Send event anyways so that the asserts outside would fail
                channel.trySend(ClientResetEvents.ON_AFTER_RESET)
            }

            override fun onError(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                // Notify that this callback has been invoked
                assertEquals(
                    "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed.",
                    exception.message
                )
                channel.trySend(ClientResetEvents.ON_MANUAL_RESET_FALLBACK)
            }

            override fun onManualResetFallback(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                // Notify that this callback has been invoked
                assertEquals(
                    "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed.",
                    exception.message
                )
                channel.trySend(ClientResetEvents.ON_MANUAL_RESET_FALLBACK)
            }
        }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                app.triggerClientReset(syncMode, realm.syncSession, user.id)

                // Validate that the client reset was triggered successfully
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receiveOrFail())
                assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receiveOrFail())
                assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receiveOrFail())
            }
        }
    }

    @Test
    fun discardUnsyncedChanges_userExceptionCaptured_onAfterReset_pbs() {
        performPbsTest { syncMode, app, user, builder ->
            discardUnsyncedChanges_userExceptionCaptured_onAfterReset_pbs(
                syncMode,
                app,
                user,
                builder
            )
        }
    }

    @Test
    fun discardUnsyncedChanges_userExceptionCaptured_onAfterReset_flx() {
        performFlxTest { syncMode, app, user, builder ->
            discardUnsyncedChanges_userExceptionCaptured_onAfterReset_pbs(
                syncMode,
                app,
                user,
                builder
            )
        }
    }

    private fun discardUnsyncedChanges_userExceptionCaptured_onAfterReset_pbs(
        syncMode: SyncMode,
        app: TestApp,
        user: User,
        builder: SyncConfiguration.Builder
    ) {
        // Validates that any user exception during the automatic client reset is properly captured.
        // Channel size is 4 because both onError and onManualResetFallback are called
        val channel = Channel<ClientResetEvents>(4)
        val config = builder.syncClientResetStrategy(object : DiscardUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                // Notify that this callback has been invoked
                channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
            }

            override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                // Notify that this callback has been invoked
                channel.trySend(ClientResetEvents.ON_AFTER_RESET)
                throw IllegalStateException("User exception")
            }

            override fun onError(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                // Notify that this callback has been invoked
                assertEquals(
                    "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed.",
                    exception.message
                )
                channel.trySend(ClientResetEvents.ON_MANUAL_RESET_FALLBACK)
            }

            override fun onManualResetFallback(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                // Notify that this callback has been invoked
                assertEquals(
                    "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed.",
                    exception.message
                )
                channel.trySend(ClientResetEvents.ON_MANUAL_RESET_FALLBACK)
            }
        }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                app.triggerClientReset(syncMode, realm.syncSession, user.id)

                // Validate that the client reset was triggered successfully
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receiveOrFail())
                assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receiveOrFail())

                // TODO Twice until the deprecated method is removed
                assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receiveOrFail())
                assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receiveOrFail())
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Default from config: RecoverOrDiscardUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun defaultRecoverOrDiscardUnsyncedChanges_logsReported_pbs() {
        performPbsTest { syncMode, app, user, builder ->
            defaultRecoverOrDiscardUnsyncedChanges_logsReported(syncMode, app, user, builder)
        }
    }

    @Test
    fun defaultRecoverOrDiscardUnsyncedChanges_logsReported_flx() {
        performFlxTest { syncMode, app, user, builder ->
            defaultRecoverOrDiscardUnsyncedChanges_logsReported(syncMode, app, user, builder)
        }
    }

    private fun TestEnvironment<*>.defaultRecoverOrDiscardUnsyncedChanges_logsReported(
        syncMode: SyncMode,
        app: TestApp,
        user: User,
        builder: SyncConfiguration.Builder
    ) {
        val config = builder.build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                app.triggerClientReset(syncMode, realm.syncSession, user.id)

                // Validate we receive logs on the regular path
                assertEquals(
                    ClientResetLogEvents.DISCARD_LOCAL_ON_BEFORE_RESET,
                    logChannel.receiveOrFail()
                )
                assertEquals(
                    ClientResetLogEvents.DISCARD_LOCAL_ON_AFTER_RECOVERY,
                    logChannel.receiveOrFail()
                )
                // assertEquals(ClientResetLogEvents.DISCARD_LOCAL_ON_AFTER_RESET, logChannel.receiveOrFail())

                (realm.syncSession as SyncSessionImpl).simulateError(
                    ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                    SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                )
                // Validate that we receive logs on the error callback
                val actual = logChannel.receiveOrFail()
                assertEquals(ClientResetLogEvents.DISCARD_LOCAL_ON_ERROR, actual)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // ManuallyRecoverUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun manuallyRecoverUnsyncedChanges_reported_pbs() = runBlocking {
        performPbsTest { _, _, _, builder ->
            manuallyRecoverUnsyncedChanges_reported(builder)
        }
    }

    @Test
    fun manuallyRecoverUnsyncedChanges_reported_flx() = runBlocking {
        performFlxTest { _, _, _, builder ->
            manuallyRecoverUnsyncedChanges_reported(builder)
        }
    }

    private fun manuallyRecoverUnsyncedChanges_reported(
        builder: SyncConfiguration.Builder
    ) {
        val channel = Channel<ClientResetRequiredException>(1)

        val config = builder.syncClientResetStrategy(
            object : ManuallyRecoverUnsyncedChangesStrategy {
                override fun onClientReset(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    channel.trySend(exception)
                }
            }
        ).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                with((realm.syncSession as SyncSessionImpl)) {
                    simulateError(
                        ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                    )

                    val exception = channel.receiveOrFail()
                    val originalFilePath = assertNotNull(exception.originalFilePath)
                    val recoveryFilePath = assertNotNull(exception.recoveryFilePath)
                    assertTrue(fileExists(originalFilePath))
                    assertFalse(fileExists(recoveryFilePath))
                    assertEquals(
                        "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed.",
                        exception.message
                    )
                }
            }
        }
    }

    @Test
    fun manuallyRecoverUnsyncedChanges_executeClientReset_pbs() = runBlocking {
        performPbsTest { _, _, _, builder ->
            manuallyRecoverUnsyncedChanges_executeClientReset(builder)
        }
    }

    @Test
    fun manuallyRecoverUnsyncedChanges_executeClientReset_flx() = runBlocking {
        performFlxTest { _, _, _, builder ->
            manuallyRecoverUnsyncedChanges_executeClientReset(builder)
        }
    }

    private fun manuallyRecoverUnsyncedChanges_executeClientReset(
        builder: SyncConfiguration.Builder
    ) {
        val channel = Channel<ClientResetRequiredException>(1)

        val config = builder.syncClientResetStrategy(
            object : ManuallyRecoverUnsyncedChangesStrategy {
                override fun onClientReset(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    channel.trySend(exception)
                }
            }
        ).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                with(realm.syncSession as SyncSessionImpl) {
                    simulateError(
                        ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                    )

                    val exception = channel.receiveOrFail()

                    val originalFilePath = assertNotNull(exception.originalFilePath)
                    val recoveryFilePath = assertNotNull(exception.recoveryFilePath)
                    assertTrue(fileExists(originalFilePath))
                    assertFalse(fileExists(recoveryFilePath))

                    exception.executeClientReset()
                    assertFalse(fileExists(originalFilePath))
                    assertTrue(fileExists(recoveryFilePath))
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // RecoverUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun recoverUnsyncedChanges_recover_pbs() = runBlocking {
        performPbsTest { syncMode, app, user, builder ->
            recoverUnsyncedChanges_recover(syncMode, app, user, builder)
        }
    }

    @Test
    fun recoverUnsyncedChanges_recover_flx() = runBlocking {
        performFlxTest { syncMode, app, user, builder ->
            recoverUnsyncedChanges_recover(syncMode, app, user, builder)
        }
    }

    private fun TestEnvironment<*>.recoverUnsyncedChanges_recover(
        syncMode: SyncMode,
        app: TestApp,
        user: User,
        builder: SyncConfiguration.Builder
    ) {
        val channel = Channel<ClientResetEvents>(2)
        val config = builder.syncClientResetStrategy(object : RecoverUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                assertEquals(1, countObjects(realm))
                channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
            }

            override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                assertEquals(1, countObjects(before))
                assertEquals(1, countObjects(after))
                channel.trySend(ClientResetEvents.ON_AFTER_RESET)
            }

            override fun onManualResetFallback(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                fail("This test case was not supposed to trigger RecoverUnsyncedChangesStrategy::onManualResetFallback(): ${exception.message}")
            }
        }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                app.triggerClientReset(syncMode, realm.syncSession, user.id) {
                    insertElement(realm)
                    assertEquals(1, countObjects(realm))
                }

                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receiveOrFail())
                assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receiveOrFail())
            }
        }
    }

    @Test
    fun recoverUnsyncedChanges_resetErrorHandled_pbs() {
        performPbsTest { _, _, _, builder ->
            recoverUnsyncedChanges_resetErrorHandled(builder)
        }
    }

    @Test
    fun recoverUnsyncedChanges_resetErrorHandled_flx() {
        performFlxTest { _, _, _, builder ->
            recoverUnsyncedChanges_resetErrorHandled(builder)
        }
    }

    private fun recoverUnsyncedChanges_resetErrorHandled(
        builder: SyncConfiguration.Builder
    ) {
        val channel = Channel<ClientResetRequiredException>(1)
        val config = builder.syncClientResetStrategy(object : RecoverUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                fail("This test case was not supposed to trigger RecoverUnsyncedChangesStrategy::onBeforeReset()")
            }

            override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                fail("This test case was not supposed to trigger RecoverUnsyncedChangesStrategy::onAfterReset()")
            }

            override fun onManualResetFallback(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                channel.trySend(exception)
            }
        }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                (realm.syncSession as SyncSessionImpl).simulateError(
                    ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                    SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                )
                val exception = channel.receiveOrFail()

                assertNotNull(exception.recoveryFilePath)
                assertNotNull(exception.originalFilePath)
                assertFalse(fileExists(exception.recoveryFilePath))
                assertTrue(fileExists(exception.originalFilePath))
                assertTrue(exception.message!!.contains("Automatic recovery from client reset failed"))
            }
        }
    }

    @Test
    fun recoverUnsyncedChanges_recoverFails_pbs() = runBlocking {
        performPbsTest { syncMode, app, user, builder ->
            recoverUnsyncedChanges_recoverFails(syncMode, app, user, builder)
        }
    }

    @Test
    fun recoverUnsyncedChanges_recoverFails_flx() = runBlocking {
        performFlxTest { syncMode, app, user, builder ->
            recoverUnsyncedChanges_recoverFails(syncMode, app, user, builder)
        }
    }

    private fun recoverUnsyncedChanges_recoverFails(
        syncMode: SyncMode,
        app: TestApp,
        user: User,
        builder: SyncConfiguration.Builder
    ) {
        val channel = Channel<ClientResetEvents>(2)
        val config = builder.syncClientResetStrategy(object : RecoverUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                throw IllegalStateException("User exception")
            }

            override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                // Send event anyways so that the asserts outside would fail
                channel.trySend(ClientResetEvents.ON_AFTER_RESET)
            }

            override fun onManualResetFallback(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                // Notify that this callback has been invoked
                assertEquals(
                    "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed.",
                    exception.message
                )
                channel.trySend(ClientResetEvents.ON_MANUAL_RESET_FALLBACK)
            }
        }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                app.triggerClientReset(syncMode, realm.syncSession, user.id)

                // Validate that the client reset was triggered successfully
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receiveOrFail())
                assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receiveOrFail())
            }
        }
    }

    @Test
    fun recoverUnsyncedChanges_executeClientReset_pbs() = runBlocking {
        performPbsTest { _, _, _, builder ->
            recoverUnsyncedChanges_executeClientReset(builder)
        }
    }

    @Test
    fun recoverUnsyncedChanges_executeClientReset_flx() = runBlocking {
        performFlxTest { _, _, _, builder ->
            recoverUnsyncedChanges_executeClientReset(builder)
        }
    }

    private fun recoverUnsyncedChanges_executeClientReset(builder: SyncConfiguration.Builder) {
        val channel = Channel<ClientResetEvents>(2)
        val config = builder.syncClientResetStrategy(object : RecoverUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                fail("Should not call onBeforeReset")
            }

            override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                fail("Should not call onAfterReset")
            }

            override fun onManualResetFallback(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                val originalFilePath = assertNotNull(exception.originalFilePath)
                val recoveryFilePath = assertNotNull(exception.recoveryFilePath)
                assertTrue(fileExists(originalFilePath))
                assertFalse(fileExists(recoveryFilePath))

                exception.executeClientReset()

                // Validate that files have been moved after explicit reset
                assertFalse(fileExists(originalFilePath))
                assertTrue(fileExists(recoveryFilePath))

                assertEquals(
                    "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed.",
                    exception.message
                )

                channel.trySend(ClientResetEvents.ON_MANUAL_RESET_FALLBACK)
            }
        }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                with(realm.syncSession as SyncSessionImpl) {
                    simulateError(
                        ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                    )

                    // TODO Twice until the deprecated method is removed
                    assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receiveOrFail())
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // RecoverOrDiscardUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun recoverOrDiscardUnsyncedChanges_recover_pbs() = runBlocking {
        performPbsTest { syncMode, app, user, builder ->
            recoverOrDiscardUnsyncedChanges_recover(syncMode, app, user, builder)
        }
    }

    @Test
    fun recoverOrDiscardUnsyncedChanges_recover_flx() = runBlocking {
        performFlxTest { syncMode, app, user, builder ->
            recoverOrDiscardUnsyncedChanges_recover(syncMode, app, user, builder)
        }
    }

    private fun TestEnvironment<*>.recoverOrDiscardUnsyncedChanges_recover(
        syncMode: SyncMode,
        app: TestApp,
        user: User,
        builder: SyncConfiguration.Builder
    ) {
        val channel = Channel<ClientResetEvents>(2)
        val config = builder.syncClientResetStrategy(
            object : RecoverOrDiscardUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                }

                override fun onAfterRecovery(before: TypedRealm, after: MutableRealm) {
                    channel.trySend(ClientResetEvents.ON_AFTER_RECOVERY)
                }

                override fun onAfterDiscard(before: TypedRealm, after: MutableRealm) {
                    fail("This test case was not supposed to trigger AutomaticRecoveryStrategy::onAfterDiscard()")
                }

                override fun onManualResetFallback(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    fail("This test case was not supposed to trigger AutomaticRecoveryStrategy::onError()")
                }
            }
        ).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                app.triggerClientReset(syncMode, realm.syncSession, user.id)
                insertElement(realm)
                assertEquals(1, countObjects(realm))

                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receiveOrFail())
                assertEquals(ClientResetEvents.ON_AFTER_RECOVERY, channel.receiveOrFail())
            }
        }
    }

    @Test
    fun recoverOrDiscardUnsyncedChanges_discards_pbs() = runBlocking {
        val suffix = Random.nextLong(1000, 9999)
        val environment = createPbsEnvironment("PBS-NO-RECOVERY_$suffix", recoveryDisabled = true)
        performPbsTest(environment) { syncMode, app, user, builder ->
            recoverOrDiscardUnsyncedChanges_discards(syncMode, app, user, builder)
        }
    }

    @Test
    fun recoverOrDiscardUnsyncedChanges_discards_flx() = runBlocking {
        val suffix = Random.nextLong(1000, 9999)
        val environment = createPbsEnvironment("PBS-NO-RECOVERY_$suffix", recoveryDisabled = true)
        performFlxTest(environment) { syncMode, app, user, builder ->
            recoverOrDiscardUnsyncedChanges_discards(syncMode, app, user, builder)
        }
    }

    private fun TestEnvironment<*>.recoverOrDiscardUnsyncedChanges_discards(
        syncMode: SyncMode,
        app: TestApp,
        user: User,
        builder: SyncConfiguration.Builder
    ) {
        val channel = Channel<ClientResetEvents>(2)
        val config = builder.syncClientResetStrategy(
            object : RecoverOrDiscardUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    assertEquals(1, countObjects(realm))
                    channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                }

                override fun onAfterRecovery(before: TypedRealm, after: MutableRealm) {
                    fail("This test case was not supposed to trigger RecoverOrDiscardUnsyncedChangesStrategy::onAfterRecovery()")
                }

                override fun onAfterDiscard(before: TypedRealm, after: MutableRealm) {
                    assertEquals(1, countObjects(before))
                    assertEquals(0, countObjects(after))
                    channel.trySend(ClientResetEvents.ON_AFTER_DISCARD)
                }

                override fun onManualResetFallback(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    fail("This test case was not supposed to trigger RecoverOrDiscardUnsyncedChangesStrategy::onError()")
                }
            }
        ).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                // The apps in this test run with recovery mode disabled so no need to fiddle with the configuration
                app.triggerClientReset(syncMode, realm.syncSession, user.id) {
                    insertElement(realm)
                    assertEquals(1, countObjects(realm))
                }

                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receiveOrFail())
                assertEquals(ClientResetEvents.ON_AFTER_DISCARD, channel.receiveOrFail())
            }
        }
    }

    @Test
    fun recoverOrDiscardUnsyncedChanges_executeClientReset_pbs() = runBlocking {
        performPbsTest { _, _, _, builder ->
            recoverOrDiscardUnsyncedChanges_executeClientReset(builder)
        }
    }

    @Test
    fun recoverOrDiscardUnsyncedChanges_executeClientReset_flx() = runBlocking {
        performFlxTest { _, _, _, builder ->
            recoverOrDiscardUnsyncedChanges_executeClientReset(builder)
        }
    }

    private fun recoverOrDiscardUnsyncedChanges_executeClientReset(
        builder: SyncConfiguration.Builder
    ) {
        val channel = Channel<ClientResetEvents>(2)
        val config = builder.syncClientResetStrategy(object : RecoverOrDiscardUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                fail("Should not call onBeforeReset")
            }

            override fun onAfterRecovery(before: TypedRealm, after: MutableRealm) {
                fail("Should not call onAfterReset")
            }

            override fun onAfterDiscard(before: TypedRealm, after: MutableRealm) {
                fail("Should not call onAfterDiscard")
            }

            override fun onManualResetFallback(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                val originalFilePath = assertNotNull(exception.originalFilePath)
                val recoveryFilePath = assertNotNull(exception.recoveryFilePath)
                assertTrue(fileExists(originalFilePath))
                assertFalse(fileExists(recoveryFilePath))

                exception.executeClientReset()

                // Validate that files have been moved after explicit reset
                assertFalse(fileExists(originalFilePath))
                assertTrue(fileExists(recoveryFilePath))

                assertEquals(
                    "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed.",
                    exception.message
                )

                channel.trySend(ClientResetEvents.ON_MANUAL_RESET_FALLBACK)
            }
        }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.syncSession.downloadAllServerChanges(defaultTimeout)

                with(realm.syncSession as SyncSessionImpl) {
                    simulateError(
                        ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                    )

                    // TODO Twice until the deprecated method is removed
                    assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receiveOrFail())
                }
            }
        }
    }
}
