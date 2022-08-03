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
import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.ParentPk
import io.realm.kotlin.entities.sync.SyncPerson
import io.realm.kotlin.entities.sync.flx.FlexChildObject
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
import io.realm.kotlin.mongodb.sync.SyncSession
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.test.mongodb.TEST_APP_1
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
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
import kotlin.time.Duration.Companion.minutes

@RunWith(Parameterized::class)
class SyncClientResetIntegrationTests(
    val parameterGenerator: () -> TestParameterGenerator<*>
) {

    class TestParameterGenerator<T : RealmObject>(
        private val clazz: KClass<T>,
        val appName: String,
        val configBuilderGenerator: (user: User) -> SyncConfiguration.Builder,
        val insertElement: (realm: Realm) -> Unit,
        val recoverData: (before: TypedRealm, after: MutableRealm) -> Unit,
    ) {
        fun channel(capacity: Int): Channel<ResultsChange<T>> = Channel(capacity)
        fun query(realm: TypedRealm): RealmQuery<T> = realm.query(clazz)
        fun countElements(realm: TypedRealm): Long = query(realm).count().find()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        @Suppress("LongMethod")
        fun data(): List<Array<() -> TestParameterGenerator<*>>> {
            return listOf(
                arrayOf(
                    {
                        val partition = TestHelper.randomPartitionValue()

                        TestParameterGenerator(
                            clazz = SyncPerson::class,
                            appName = TEST_APP_1,
                            configBuilderGenerator = { user ->
                                return@TestParameterGenerator SyncConfiguration.Builder(
                                    user,
                                    partition,
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
                    }
                ),
                arrayOf(
                    {
                        val section = Random.nextInt()

                        TestParameterGenerator(
                            clazz = FlexParentObject::class,
                            appName = TEST_APP_FLEX,
                            configBuilderGenerator = { user ->
                                return@TestParameterGenerator SyncConfiguration.Builder(
                                    user,
                                    setOf(FlexParentObject::class, FlexChildObject::class)
                                ).initialSubscriptions { realm ->
                                    realm.query<FlexParentObject>(
                                        "section = $0 AND name = $1",
                                        section,
                                        "blue"
                                    ).also { add(it) }
                                }.waitForInitialRemoteData(timeout = 1.minutes)
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
                    }
                ),
            )
        }
    }

    private enum class ClientResetEvents {
        ON_BEFORE_RESET,
        ON_AFTER_RESET,
        ON_AFTER_RECOVERY,
        ON_AFTER_DISCARD,
        ON_ERROR
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

    private lateinit var parameters: TestParameterGenerator<*>
    private lateinit var partitionValue: String
    private lateinit var user: User
    private lateinit var app: TestApp
    private lateinit var logChannel: Channel<ClientResetLogEvents>

    @BeforeTest
    fun setup() {
        partitionValue = TestHelper.randomPartitionValue()
        logChannel = Channel(5)
        parameters = parameterGenerator()
        app = TestApp(
            appName = parameters.appName,
            logLevel = LogLevel.INFO,
            customLogger = ClientResetLoggerInspector(logChannel)
        )
        val (email, password) = TestHelper.randomEmail() to "password1234"
        user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    // ---------------------------------------------------------------------------------------
    // DiscardUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun discardUnsyncedChangesStrategy_discards() {
        // Validate that the discard local strategy onBeforeReset and onAfterReset callbacks
        // are invoked successfully when a client reset is triggered.
        val channel = Channel<ClientResetEvents>(2)
        val config = parameters.configBuilderGenerator(user).syncClientResetStrategy(
            object : DiscardUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    // This realm contains something as we wrote an object while the session was paused
                    assertEquals(1, parameters.countElements(realm))
                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                }

                override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                    // The before-Realm contains the object we wrote while the session was paused
                    assertEquals(1, parameters.countElements(before))

                    // The after-Realm contains no objects
                    assertEquals(0, parameters.countElements(after))

                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_AFTER_RESET)
                }

                override fun onError(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_ERROR)
                }
            }
        ).build()

        Realm.open(config).use { realm ->
            runBlocking {
                // This channel helps to validate that the Realm gets updated
                val objectChannel: Channel<ResultsChange<out RealmObject>> =
                    parameters.channel(1) as Channel<ResultsChange<out RealmObject>>

                val job = async {
                    parameters.query(realm).asFlow()
                        .collect {
                            objectChannel.trySend(it)
                        }
                }

                // No initial data
                assertEquals(0, objectChannel.receive().list.size)

                app.triggerClientReset(user.identity, realm.syncSession) {
                    parameters.insertElement(realm)
                    assertEquals(1, objectChannel.receive().list.size)
                }

                // Validate that the client reset was triggered successfully
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())

                // TODO We must not need this. Force updating the instance pointer.
                realm.write { }

                // Validate Realm instance has been correctly updated
                assertEquals(0, objectChannel.receive().list.size)

                job.cancel()
            }
        }
    }

    @Test
    fun discardUnsyncedChangesStrategy_discards_attemptRecover() {
        // Attempts to recover data if a client reset is triggered.
        val channel = Channel<ClientResetEvents>(2)
        val config = parameters.configBuilderGenerator(user).syncClientResetStrategy(
            object : DiscardUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                }

                override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                    // The before-Realm contains the object we wrote while the session was paused
                    assertEquals(1, parameters.countElements(before))

                    parameters.recoverData(before, after)

                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_AFTER_RESET)
                }

                override fun onError(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    // Notify that this callback has been invoked
                    channel.trySend(ClientResetEvents.ON_ERROR)
                }
            }
        ).build()

        Realm.open(config).use { realm ->
            runBlocking {
                // This channel helps to validate that the Realm gets updated
                val objectChannel: Channel<ResultsChange<out RealmObject>> =
                    parameters.channel(1) as Channel<ResultsChange<out RealmObject>>

                val job = async {
                    parameters.query(realm).asFlow()
                        .collect {
                            objectChannel.trySend(it)
                        }
                }

                // No initial data
                assertEquals(0, objectChannel.receive().list.size)

                app.triggerClientReset(user.identity, realm.syncSession) {
                    // Write something while the session is paused to make sure the before realm contains something
                    parameters.insertElement(realm)
                    assertEquals(1, objectChannel.receive().list.size)
                }

                // Validate that the client reset was triggered successfuly
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())

                // TODO We must not need this. Force updating the instance pointer.
                realm.write { }

                // Validate Realm instance has been correctly updated
                assertEquals(1, objectChannel.receive().list.size)

                job.cancel()
            }
        }
    }

    @Test
    fun discardUnsyncedChangesStrategy_failure() {
        // Validate that the discard local strategy onError callback is invoked successfully if
        // a client reset fails.
        val channel = Channel<ClientResetEvents>(1)
        val config =
            parameters.configBuilderGenerator(user)
                .syncClientResetStrategy(object : DiscardUnsyncedChangesStrategy {
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
                        val originalFilePath = assertNotNull(exception.originalFilePath)
                        val recoveryFilePath = assertNotNull(exception.recoveryFilePath)
                        assertTrue(fileExists(originalFilePath))
                        assertFalse(fileExists(recoveryFilePath))
                        // Note, this error message is just the one created by ObjectStore for
                        // testing the server will send a different message. This just ensures that
                        // we don't accidentally modify or remove the message.
                        assertEquals(
                            "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed",
                            exception.message
                        )

                        // Notify that this callback has been invoked
                        channel.trySend(ClientResetEvents.ON_ERROR)
                    }
                }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                with(realm.syncSession as SyncSessionImpl) {
                    simulateError(
                        ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                    )

                    assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
                }
            }
        }
    }

    @Test
    fun discardUnsyncedChangesStrategy_executeClientReset() = runBlocking {
        val channel = Channel<ClientResetEvents>(1)
        val config =
            parameters.configBuilderGenerator(user)
                .syncClientResetStrategy(object : DiscardUnsyncedChangesStrategy {
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
                        val originalFilePath = assertNotNull(exception.originalFilePath)
                        val recoveryFilePath = assertNotNull(exception.recoveryFilePath)
                        assertTrue(fileExists(originalFilePath))
                        assertFalse(fileExists(recoveryFilePath))

                        exception.executeClientReset()

                        // Validate that files have been moved after explicit reset
                        assertFalse(fileExists(originalFilePath))
                        assertTrue(fileExists(recoveryFilePath))

                        channel.trySend(ClientResetEvents.ON_ERROR)
                    }
                }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                with(realm.syncSession as SyncSessionImpl) {
                    simulateError(
                        ProtocolClientErrorCode.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE,
                        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT
                    )

                    assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
                }
            }
        }
    }

    @Test
    fun discardUnsyncedChangesStrategy_userExceptionCaptured_onBeforeReset() {
        // Validates that any user exception during the automatic client reset is properly captured.
        val channel = Channel<ClientResetEvents>(3)
        val config = parameters.configBuilderGenerator(user).syncClientResetStrategy(
            object : DiscardUnsyncedChangesStrategy {
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
                        "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed",
                        exception.message
                    )
                    channel.trySend(ClientResetEvents.ON_ERROR)
                }
            }
        ).build()

        Realm.open(config).use { realm ->
            runBlocking {
                app.triggerClientReset(user.identity, realm.syncSession)

                // Validate that the client reset was triggered successfully
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
            }
        }
    }

    @Test
    fun discardUnsyncedChangesStrategy_userExceptionCaptured_onAfterReset() {
        // Validates that any user exception during the automatic client reset is properly captured.
        val channel = Channel<ClientResetEvents>(3)
        val config = parameters.configBuilderGenerator(user).syncClientResetStrategy(
            object : DiscardUnsyncedChangesStrategy {
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
                        "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed",
                        exception.message
                    )
                    channel.trySend(ClientResetEvents.ON_ERROR)
                }
            }
        ).build()

        Realm.open(config).use { realm ->
            runBlocking {
                app.triggerClientReset(user.identity, realm.syncSession)

                // Validate that the client reset was triggered successfully
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())
                assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Default from config: RecoverOrDiscardUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun defaultRecoverOrDiscardUnsyncedChangesStrategy_logsReported() {
        val config = parameters.configBuilderGenerator(user).build()

        Realm.open(config).use { realm ->
            runBlocking {
                app.triggerClientReset(user.identity, realm.syncSession)

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

    // ---------------------------------------------------------------------------------------
    // ManuallyRecoverUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun manuallyRecoverUnsyncedChangesStrategy_reported() = runBlocking {
        val channel = Channel<ClientResetRequiredException>(1)

        val config = parameters.configBuilderGenerator(user)
            .syncClientResetStrategy(object : ManuallyRecoverUnsyncedChangesStrategy {
                override fun onClientReset(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    channel.trySend(exception)
                }
            }).build()

        Realm.open(config).use { realm ->
            runBlocking {
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
                        "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed",
                        exception.message
                    )
                }
            }
        }
    }

    @Test
    fun manuallyRecoverUnsyncedChangesStrategy_executeClientReset() = runBlocking {
        val channel = Channel<ClientResetRequiredException>(1)

        val config = parameters.configBuilderGenerator(user)
            .syncClientResetStrategy(object : ManuallyRecoverUnsyncedChangesStrategy {
                override fun onClientReset(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    channel.trySend(exception)
                }
            }).build()

        Realm.open(config).use { realm ->
            runBlocking {
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

    // ---------------------------------------------------------------------------------------
    // RecoverUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun recoverUnsyncedChangesStrategy_recover() = runBlocking {
        val channel = Channel<ClientResetEvents>(2)
        val config =
            parameters.configBuilderGenerator(user)
                .syncClientResetStrategy(object : RecoverUnsyncedChangesStrategy {
                    override fun onBeforeReset(realm: TypedRealm) {
                        channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                    }

                    override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                        channel.trySend(ClientResetEvents.ON_AFTER_RESET)
                    }

                    override fun onError(
                        session: SyncSession,
                        exception: ClientResetRequiredException
                    ) {
                        fail("This test case was not supposed to trigger RecoverUnsyncedChangesStrategy::onError()")
                    }
                }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                app.triggerClientReset(user.identity, realm.syncSession) {
                    parameters.insertElement(realm)
                    assertEquals(1, parameters.countElements(realm))
                }
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                assertEquals(ClientResetEvents.ON_AFTER_RESET, channel.receive())
            }
        }
    }

    @Test
    fun recoverUnsyncedChangesStrategy_resetErrorHandled() = runBlocking {
        val channel = Channel<ClientResetRequiredException>(1)
        val config = SyncConfiguration.Builder(
            user,
            partitionValue,
            schema = setOf(ParentPk::class, ChildPk::class)
        ).syncClientResetStrategy(object : RecoverUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                fail("This test case was not supposed to trigger RecoverUnsyncedChangesStrategy::onBeforeReset()")
            }

            override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                fail("This test case was not supposed to trigger RecoverUnsyncedChangesStrategy::onAfterReset()")
            }

            override fun onError(session: SyncSession, exception: ClientResetRequiredException) {
                channel.trySend(exception)
            }
        }).build()

        Realm.open(config).use { realm ->
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

    @Test
    fun recoverUnsyncedChangesStrategy_recoverFails() = runBlocking {
        val channel = Channel<ClientResetEvents>(2)
        val config =
            parameters.configBuilderGenerator(user)
                .syncClientResetStrategy(object : RecoverUnsyncedChangesStrategy {
                    override fun onBeforeReset(realm: TypedRealm) {
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
                            "[Client][AutoClientResetFailure(132)] Automatic recovery from client reset failed",
                            exception.message
                        )
                        channel.trySend(ClientResetEvents.ON_ERROR)
                    }
                }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                app.triggerClientReset(user.identity, realm.syncSession)

                // Validate that the client reset was triggered successfully
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                assertEquals(ClientResetEvents.ON_ERROR, channel.receive())
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // RecoverOrDiscardUnsyncedChangesStrategy
    // ---------------------------------------------------------------------------------------

    @Test
    fun recoverOrDiscardUnsyncedChangesStrategy_recover() = runBlocking {
        val channel = Channel<ClientResetEvents>(2)
        val config = parameters.configBuilderGenerator(user)
            .syncClientResetStrategy(object : RecoverOrDiscardUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                }

                override fun onAfterRecovery(before: TypedRealm, after: MutableRealm) {
                    channel.trySend(ClientResetEvents.ON_AFTER_RECOVERY)
                }

                override fun onAfterDiscard(before: TypedRealm, after: MutableRealm) {
                    fail("This test case was not supposed to trigger AutomaticRecoveryStrategy::onAfterDiscard()")
                }

                override fun onError(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    fail("This test case was not supposed to trigger AutomaticRecoveryStrategy::onError()")
                }
            }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                app.triggerClientReset(user.identity, realm.syncSession) {
                    parameters.insertElement(realm)
                    assertEquals(1, parameters.countElements(realm))
                }
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                assertEquals(ClientResetEvents.ON_AFTER_RECOVERY, channel.receive())
            }
        }
    }

    @Test
    fun recoverOrDiscardUnsyncedChangesStrategy_discards() = runBlocking {
        val channel = Channel<ClientResetEvents>(2)
        val config = parameters.configBuilderGenerator(user)
            .syncClientResetStrategy(object : RecoverOrDiscardUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    assertEquals(1, parameters.countElements(realm))
                    channel.trySend(ClientResetEvents.ON_BEFORE_RESET)
                }

                override fun onAfterRecovery(before: TypedRealm, after: MutableRealm) {
                    fail("This test case was not supposed to trigger RecoverOrDiscardUnsyncedChangesStrategy::onAfterRecovery()")
                }

                override fun onAfterDiscard(before: TypedRealm, after: MutableRealm) {
                    assertEquals(1, parameters.countElements(before))
                    assertEquals(0, parameters.countElements(after))
                    channel.trySend(ClientResetEvents.ON_AFTER_DISCARD)
                }

                override fun onError(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    fail("This test case was not supposed to trigger RecoverOrDiscardUnsyncedChangesStrategy::onError()")
                }
            }).build()

        Realm.open(config).use { realm ->
            runBlocking {
                // Disable recovery mode on the server
                app.triggerClientReset(user.identity, realm.syncSession, true) {
                    parameters.insertElement(realm)
                    assertEquals(1, parameters.countElements(realm))
                }
                assertEquals(ClientResetEvents.ON_BEFORE_RESET, channel.receive())
                assertEquals(ClientResetEvents.ON_AFTER_DISCARD, channel.receive())
            }
        }
    }
}
