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

package io.realm.kotlin.test.mongodb.shared

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
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Ignore
// FIXME These tests seemingly make other tests fail on macos - we need to investigate how we can
//  set up test apps on the server and make them run in a sterile environment that does not affect
//  other tests/platforms.
class SyncClientResetIntegrationTests {

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
                requestTimeout = 10.seconds,
                customLogger = ClientResetLoggerInspector(logChannel),
                initialSetup = { app, service ->
                    println("-----------------> addition of email provider started")
                    addEmailProvider(app)
                    println("-----------------> addition of email provider completed")
                    println("-----------------> app creation started")
                    when (syncMode) {
                        SyncMode.PARTITION_BASED ->
                            initializePartitionSync(app, service, recoveryDisabled)
                        SyncMode.FLEXIBLE ->
                            initializeFlexibleSync(app, service, recoveryDisabled)
                    }
                    println("-----------------> app creation done")
                }
            )
            try {
                println("-----------------> user creation started")
                val (email, password) = TestHelper.randomEmail() to "password1234"
                val user = runBlocking {
                    app.createUserAndLogIn(email, password)
                }
                println("-----------------> user creation done")

                block(syncMode, app, user, configBuilderGenerator(user))
            } finally {
//                app.close(true)
                app.close()
            }
        }
    }

    companion object {

        private fun defaultTestEnvironments(
            appSuffix: String,
            openRealmTimeout: Duration = 1.minutes
        ): List<TestEnvironment<out RealmObject>> = listOf(
            createPartitionBasedTemplate("CLIENT-RESET-PBS_$appSuffix"),
            createFlexibleSyncTemplate("CLIENT-RESET-FLX_$appSuffix", openRealmTimeout)
        )

        private fun createFlexibleSyncTemplate(
            appName: String = TEST_APP_FLEX,
            openRealmTimeout: Duration = 1.minutes,
            recoveryDisabled: Boolean = false
        ): TestEnvironment<out RealmObject> = TestEnvironment(
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
                        Random.nextInt(),
                        "blue"
                    ).also { add(it) }
                }.waitForInitialRemoteData(openRealmTimeout)
                    .name("$appName.realm")
            },
            insertElement = { realm: Realm ->
                realm.writeBlocking {
                    copyToRealm(FlexParentObject())
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

        private fun createPartitionBasedTemplate(
            appName: String = TEST_APP_PARTITION,
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
                ).name("$appName.realm")
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

        @Suppress("LongMethod")
        fun performTests(
            openRealmTimeout: Duration = 1.minutes,
            environments: List<TestEnvironment<out RealmObject>> = defaultTestEnvironments(
                appSuffix = Random.nextLong(1000, 9999).toString(),
                openRealmTimeout = openRealmTimeout
            ),
            block: TestEnvironment<out RealmObject>.(
                syncMode: SyncMode,
                app: TestApp,
                user: User,
                builder: SyncConfiguration.Builder
            ) -> Unit
        ) {
            environments.forEach {
                println("-----------------> ${it.appName} START")
                it.performTest(block)
                println("-----------------> ${it.appName} DONE")
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
     * This class allows us to inspect if the default client reset strategies actually log the client
     * reset events.
     */
    private class ClientResetLoggerInspector(
        val channel: Channel<ClientResetLogEvents>
    ) : RealmLogger {

        override val level: LogLevel
            get() = LogLevel.INFO
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

    private lateinit var partitionValue: String

    @BeforeTest
    fun setup() {
        partitionValue = TestHelper.randomPartitionValue()
    }

    @AfterTest
    fun tearDown() {
    }

    // ---------------------------------------------------------------------------------------
    // DiscardUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun discardUnsyncedChangesStrategy_discards() {
        performTests { syncMode, app, user, builder ->
            // Validate that the discard local strategy onBeforeReset and onAfterReset callbacks
            // are invoked successfully when a client reset is triggered.
            val channel = Channel<ClientResetEvents>(2)
            val config = builder.syncClientResetStrategy(
                object : DiscardUnsyncedChangesStrategy {
                    override fun onBeforeReset(realm: TypedRealm) {
                        println("-----------------> onBeforeReset")
                        // This realm contains something as we wrote an object while the session was paused
                        assertEquals(1, countObjects(realm))
                        // Notify that this callback has been invoked
                        channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                    }

                    override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                        println("-----------------> onAfterReset")
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
                        fail("onError shouldn't be called")
                    }

                    override fun onManualResetFallback(
                        session: SyncSession,
                        exception: ClientResetRequiredException
                    ) {
                        fail("onError shouldn't be called")
                    }
                }
            ).build()

            println("-----------------> 1")
            Realm.open(config).use { realm ->
                runBlocking {
                    println("-----------------> 2")
                    realm.syncSession.downloadAllServerChanges(1.minutes)

                    // This channel helps to validate that the Realm gets updated
                    val objectChannel: Channel<ResultsChange<out RealmObject>> = newChannel()

                    val job = async {
                        println("-----------------> async 1")
                        getObjects(realm).asFlow()
                            .collect {
                                println("-----------------> flow received results with ${it.list.size} elements")
                                objectChannel.trySend(it)
                            }
                        println("-----------------> async 2")
                    }

                    // No initial data
                    println("-----------------> 3")
                    assertEquals(0, objectChannel.receive().list.size)

                    println("-----------------> 4")
                    app.triggerClientReset(syncMode, realm.syncSession, user.id) {
                        println("-----------------> 4a")
                        insertElement(realm)
                        println("-----------------> 4b")
                        assertEquals(1, objectChannel.receive().list.size)
                    }

                    // Validate that the client reset was triggered successfully
                    println("-----------------> 5")
                    assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                    println("-----------------> 6")
                    assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())

                    // TODO We must not need this. Force updating the instance pointer.
                    println("-----------------> 7")
                    realm.write { }

                    // Validate Realm instance has been correctly updated
                    println("-----------------> 8")
                    assertEquals(0, objectChannel.receive().list.size)

                    println("-----------------> 9")
                    job.cancel()
                }
            }
        }
    }

    @Test
    fun discardUnsyncedChangesStrategy_discards_attemptRecover() {
        performTests { syncMode, app, user, builder ->
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
                        fail("onError shouldn't be called")
                    }

                    override fun onManualResetFallback(
                        session: SyncSession,
                        exception: ClientResetRequiredException
                    ) {
                        fail("onError shouldn't be called")
                    }
                }
            ).build()

            println("-----------------> 1")
            Realm.open(config).use { realm ->
                runBlocking {
                    println("-----------------> 2")
                    realm.syncSession.downloadAllServerChanges(1.minutes)

                    // This channel helps to validate that the Realm gets updated
                    val objectChannel: Channel<ResultsChange<out RealmObject>> = newChannel()

                    val job = async {
                        println("-----------------> async 1")
                        getObjects(realm).asFlow()
                            .collect {
                                println("-----------------> flow received results with ${it.list.size} elements")
                                objectChannel.trySend(it)
                            }
                        println("-----------------> async 2")
                    }

                    // No initial data
                    println("-----------------> 3")
                    assertEquals(0, objectChannel.receive().list.size)

                    println("-----------------> 4")
                    app.triggerClientReset(syncMode, realm.syncSession, user.id) {
                        println("-----------------> 4a")
                        // Write something while the session is paused to make sure the before realm contains something
                        insertElement(realm)
                        println("-----------------> 4b")
                        assertEquals(1, objectChannel.receive().list.size)
                        println("-----------------> 4c")
                    }

                    // Validate that the client reset was triggered successfully
                    println("-----------------> 5")
                    assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                    println("-----------------> 6")
                    assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())

                    // TODO We must not need this. Force updating the instance pointer.
                    println("-----------------> 7")
                    realm.write { }

                    // Validate Realm instance has been correctly updated
                    println("-----------------> 8")
                    assertEquals(1, objectChannel.receive().list.size)

                    println("-----------------> 9")
                    job.cancel()
                    println("-----------------> 10")
                }
            }
        }
    }

    @Test
    fun discardUnsyncedChangesStrategy_failure() {
        performTests { syncMode, app, user, builder ->
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
                    realm.syncSession.downloadAllServerChanges(1.minutes)

                    with(realm.syncSession as SyncSessionImpl) {
                        simulateError(
                            ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                            SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                        )

                        // TODO Twice until the deprecated method is removed
                        assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receive())
                        assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receive())
                    }
                }
            }
        }
    }

    @Test
    fun discardUnsyncedChangesStrategy_executeClientReset() = runBlocking {
        performTests { syncMode, app, user, builder ->
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
                    println("-----------------> onError")
                    // Just notify the callback has been invoked, do the assertions in onManualResetFallback
                    channel.trySend(ClientResetEvents.ON_MANUAL_RESET_FALLBACK)
                }

                override fun onManualResetFallback(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    println("-----------------> onManualResetFallback")
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

            println("-----------------> 1")
            Realm.open(config).use { realm ->
                runBlocking {
                    println("-----------------> 2")
                    realm.syncSession.downloadAllServerChanges(1.minutes)

                    with(realm.syncSession as SyncSessionImpl) {
                        println("-----------------> 3")
                        simulateError(
                            ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                            SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                        )

                        // TODO Twice until the deprecated method is removed
                        println("-----------------> 4")
                        assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receive())
                        println("-----------------> 5")
                        assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receive())
                        println("-----------------> 6")
                    }
                }
            }
        }
    }

    @Test
    fun discardUnsyncedChangesStrategy_userExceptionCaptured_onBeforeReset() {
        performTests { syncMode, app, user, builder ->
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
                    realm.syncSession.downloadAllServerChanges(1.minutes)

                    app.triggerClientReset(syncMode, realm.syncSession, user.id)

                    // Validate that the client reset was triggered successfully
                    assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                    assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receive())
                    assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receive())
                }
            }
        }
    }

    @Test
    fun discardUnsyncedChangesStrategy_userExceptionCaptured_onAfterReset() {
        performTests { syncMode, app, user, builder ->
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
                    realm.syncSession.downloadAllServerChanges(1.minutes)

                    app.triggerClientReset(syncMode, realm.syncSession, user.id)

                    // Validate that the client reset was triggered successfully
                    assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                    assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())

                    // TODO Twice until the deprecated method is removed
                    assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receive())
                    assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receive())
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Default from config: RecoverOrDiscardUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
//    @Ignore
//    // FIXME Opening the realm times out consistently on platforms other than android
    fun defaultRecoverOrDiscardUnsyncedChangesStrategy_logsReported() {
        performTests { syncMode, app, user, builder ->
            val config = builder.build()

            Realm.open(config).use { realm ->
                runBlocking {
                    realm.syncSession.downloadAllServerChanges(1.minutes)

                    app.triggerClientReset(syncMode, realm.syncSession, user.id)

                    // Validate we receive logs on the regular path
                    assertEquals(
                        ClientResetLogEvents.DISCARD_LOCAL_ON_BEFORE_RESET,
                        logChannel.receive()
                    )
                    assertEquals(
                        ClientResetLogEvents.DISCARD_LOCAL_ON_AFTER_RECOVERY,
                        logChannel.receive()
                    )
                    // assertEquals(ClientResetLogEvents.DISCARD_LOCAL_ON_AFTER_RESET, logChannel.receive())

                    (realm.syncSession as SyncSessionImpl).simulateError(
                        ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                    )
                    // Validate that we receive logs on the error callback
                    val actual = logChannel.receive()
                    assertEquals(ClientResetLogEvents.DISCARD_LOCAL_ON_ERROR, actual)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // ManuallyRecoverUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun manuallyRecoverUnsyncedChangesStrategy_reported() = runBlocking {
        performTests { _, _, _, builder ->
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
                    realm.syncSession.downloadAllServerChanges(1.minutes)

                    with((realm.syncSession as SyncSessionImpl)) {
                        simulateError(
                            ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                            SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                        )

                        val exception = channel.receive()
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
    }

    @Test
    fun manuallyRecoverUnsyncedChangesStrategy_executeClientReset() = runBlocking {
        performTests { _, _, _, builder ->
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
                    realm.syncSession.downloadAllServerChanges(1.minutes)

                    with(realm.syncSession as SyncSessionImpl) {
                        simulateError(
                            ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                            SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                        )

                        val exception = channel.receive()

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
    }

    // ---------------------------------------------------------------------------------------
    // RecoverUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun recoverUnsyncedChangesStrategy_recover() = runBlocking {
        performTests { syncMode, app, user, builder ->
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
                    fail("This test case was not supposed to trigger RecoverUnsyncedChangesStrategy::onManualResetFallback()")
                }
            }).build()

            Realm.open(config).use { realm ->
                runBlocking {
                    realm.syncSession.downloadAllServerChanges(1.minutes)

                    app.triggerClientReset(syncMode, realm.syncSession, user.id) {
                        insertElement(realm)
                        assertEquals(1, countObjects(realm))
                    }

                    assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                    assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())
                }
            }
        }
    }

    @Test
//    @Ignore
//    // FIXME Opening the realm times out consistently on platforms other than android
    fun recoverUnsyncedChangesStrategy_resetErrorHandled() {
        performTests { _, _, _, builder ->
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
                    realm.syncSession.downloadAllServerChanges(1.minutes)

                    (realm.syncSession as SyncSessionImpl).simulateError(
                        ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                    )
                    val exception = channel.receive()

                    assertNotNull(exception.recoveryFilePath)
                    assertNotNull(exception.originalFilePath)
                    assertFalse(fileExists(exception.recoveryFilePath))
                    assertTrue(fileExists(exception.originalFilePath))
                    assertTrue(exception.message!!.contains("Automatic recovery from client reset failed"))
                }
            }
        }
    }

    @Test
    fun recoverUnsyncedChangesStrategy_recoverFails() = runBlocking {
        performTests { syncMode, app, user, builder ->
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
                    realm.syncSession.downloadAllServerChanges(1.minutes)

                    app.triggerClientReset(syncMode, realm.syncSession, user.id)

                    // Validate that the client reset was triggered successfully
                    assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                    assertEquals(ClientResetEvents.ON_MANUAL_RESET_FALLBACK, channel.receive())
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // RecoverOrDiscardUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun recoverOrDiscardUnsyncedChangesStrategy_recover() = runBlocking {
        performTests { syncMode, app, user, builder ->
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
                    realm.syncSession.downloadAllServerChanges(1.minutes)

                    app.triggerClientReset(syncMode, realm.syncSession, user.id)
                    insertElement(realm)
                    assertEquals(1, countObjects(realm))

                    assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                    assertEquals(ClientResetEvents.ON_AFTER_RECOVERY, channel.receive())
                }
            }
        }
    }

    @Test
    fun recoverOrDiscardUnsyncedChangesStrategy_discards() = runBlocking {
        val suffix = Random.nextLong(1000, 9999)

        //  Create new apps with recovery mode disabled
        performTests(
            environments = listOf(
                createPartitionBasedTemplate("PBS-NO-RECOVERY_$suffix", recoveryDisabled = true),
                createFlexibleSyncTemplate("FLX-NO-RECOVERY_$suffix", recoveryDisabled = true)
            )
        ) { syncMode, app, user, builder ->
            val channel = Channel<ClientResetEvents>(2)
            val config = builder.syncClientResetStrategy(
                object : RecoverOrDiscardUnsyncedChangesStrategy {
                    override fun onBeforeReset(realm: TypedRealm) {
                        println("-----------------> onBeforeReset")
                        assertEquals(1, countObjects(realm))
                        channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                    }

                    override fun onAfterRecovery(before: TypedRealm, after: MutableRealm) {
                        println("-----------------> onAfterRecovery")
                        fail("This test case was not supposed to trigger RecoverOrDiscardUnsyncedChangesStrategy::onAfterRecovery()")
                    }

                    override fun onAfterDiscard(before: TypedRealm, after: MutableRealm) {
                        println("-----------------> onAfterDiscard")
                        assertEquals(1, countObjects(before))
                        assertEquals(0, countObjects(after))
                        channel.trySend(ClientResetEvents.ON_AFTER_DISCARD)
                    }

                    override fun onManualResetFallback(
                        session: SyncSession,
                        exception: ClientResetRequiredException
                    ) {
                        println("-----------------> onManualResetFallback")
                        fail("This test case was not supposed to trigger RecoverOrDiscardUnsyncedChangesStrategy::onError()")
                    }
                }
            ).build()

            println("-----------------> 1")
            Realm.open(config).use { realm ->
                runBlocking {
                    println("-----------------> 2")
                    realm.syncSession.downloadAllServerChanges(1.minutes)

                    // The apps in this test run with recovery mode disabled so no need to fiddle with the configuration
                    println("-----------------> 3")
                    app.triggerClientReset(syncMode, realm.syncSession, user.id) {
                        println("-----------------> 3a")
                        insertElement(realm)
                        println("-----------------> 3b")
                        assertEquals(1, countObjects(realm))
                    }

                    println("-----------------> 4")
                    assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                    println("-----------------> 5")
                    assertEquals(ClientResetEvents.ON_AFTER_DISCARD, channel.receive())
                }
            }
        }
    }
}
