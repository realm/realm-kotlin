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
@file:Suppress("invisible_member", "invisible_reference") // Needed to call session.simulateError()

package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.CompactOnLaunchCallback
import io.realm.kotlin.InitialDataCallback
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.ParentPk
import io.realm.kotlin.entities.sync.flx.FlexChildObject
import io.realm.kotlin.entities.sync.flx.FlexEmbeddedObject
import io.realm.kotlin.entities.sync.flx.FlexParentObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.pathOf
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.internal.platform.singleThreadDispatcher
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.ClientResetRequiredException
import io.realm.kotlin.mongodb.exceptions.SyncException
import io.realm.kotlin.mongodb.sync.DiscardUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.ManuallyRecoverUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.RecoverOrDiscardUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.RecoverUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncMode
import io.realm.kotlin.mongodb.sync.SyncSession
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.TestHelper.getRandomKey
import io.realm.kotlin.test.util.TestHelper.randomEmail
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.mongodb.kbson.BsonObjectId
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val DEFAULT_NAME = "test.realm"

internal enum class ValueType {
    STRING, LONG, INT, NULL, OBJECT_ID, UUID
}

class SyncConfigTests {

    private lateinit var partitionValue: String
    private lateinit var app: App

    @BeforeTest
    fun setup() {
        partitionValue = TestHelper.randomPartitionValue()
        app = TestApp(this::class.simpleName)
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.asTestApp.close()
        }
        RealmLog.reset()
    }

    @Test
    fun errorHandler() {
        val errorHandler = object : SyncSession.ErrorHandler {
            override fun onError(session: SyncSession, error: SyncException) {
                // No-op
            }
        }
        val user = createTestUser()
        val config = SyncConfiguration.Builder(
            schema = PARTITION_BASED_SCHEMA,
            user = user,
            partitionValue = partitionValue
        ).also { builder ->
            builder.errorHandler(errorHandler)
        }.build()
        assertEquals(errorHandler, config.errorHandler)
    }

    @Test
    fun errorHandler_default() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(
            schema = PARTITION_BASED_SCHEMA,
            user = user,
            partitionValue = partitionValue
        ).build()
        assertNotNull(config.errorHandler)
    }

    // Smoke-test...most functionality is tested in CompactOnLaunchTests
    // See https://github.com/realm/realm-kotlin/issues/672
    @Test
    fun compactOnLaunch_default() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(
            schema = PARTITION_BASED_SCHEMA,
            user = user,
            partitionValue = partitionValue
        ).build()
        assertNull(config.compactOnLaunchCallback)
    }

    // Smoke-test...most functionality is tested in CompactOnLaunchTests
    // See https://github.com/realm/realm-kotlin/issues/672
    @Test
    fun compactOnLaunch() {
        val user = createTestUser()
        val callback = CompactOnLaunchCallback { _, _ -> false }
        val config = SyncConfiguration.Builder(
            schema = PARTITION_BASED_SCHEMA,
            user = user,
            partitionValue = partitionValue
        )
            .compactOnLaunch(callback)
            .build()
        assertEquals(callback, config.compactOnLaunchCallback)
    }

    // Smoke-test...most functionality is tested in InitialDataTests
    // See https://github.com/realm/realm-kotlin/pull/839
    @Test
    fun initialData() {
        val user = createTestUser()
        val callback = InitialDataCallback {
            copyToRealm(
                ParentPk().apply {
                    _id = Random.nextLong().toString()
                }
            )
        }
        val config = SyncConfiguration.Builder(
            schema = PARTITION_BASED_SCHEMA,
            user = user,
            partitionValue = partitionValue
        )
            .initialData(callback)
            .build()

        assertEquals(callback, config.initialDataCallback)
        Realm.open(config).use {
            assertEquals(1, it.query<ParentPk>().count().find())
        }
    }

//    @Test
//    fun errorHandler_fromAppConfiguration() {
//        val user: User = createTestUser(app)
//        val config: SyncConfiguration = SyncConfiguration.defaultConfig(user, DEFAULT_PARTITION)
//        assertEquals(app.configuration.defaultErrorHandler, config.errorHandler)
//    }
//
//    @Test
//    fun errorHandler_nullThrows() {
//        val user: User = createTestUser(app)
//        val builder = SyncConfiguration.Builder(user, DEFAULT_PARTITION)
//        assertFailsWith<IllegalArgumentException> { builder.errorHandler(TestHelper.getNull()) }
//    }
//
//    @Test
//    fun clientResetHandler() {
//        val builder: SyncConfiguration.Builder = SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//        val handler = object : SyncSession.ClientResetHandler {
//            override fun onClientReset(session: SyncSession, error: ClientResetRequiredError) {}
//        }
//        val config = builder.clientResetHandler(handler).build()
//        assertEquals(handler, config.clientResetHandler)
//    }
//
//    @Test
//    fun clientResetHandler_fromAppConfiguration() {
//        val user: User = createTestUser(app)
//        val config: SyncConfiguration = SyncConfiguration.defaultConfig(user, DEFAULT_PARTITION)
//        assertEquals(app.configuration.defaultClientResetHandler, config.clientResetHandler)
//    }
//
//    @Test
//    fun clientResetHandler_nullThrows() {
//        val user: User = createTestUser(app)
//        val builder = SyncConfiguration.Builder(user, DEFAULT_PARTITION)
//        assertFailsWith<IllegalArgumentException> { builder.clientResetHandler(TestHelper.getNull()) }
//    }

    @Test
    fun clientResetStrategy_manual() {
        val user = createTestUser()
        val strategy = object : ManuallyRecoverUnsyncedChangesStrategy {
            override fun onClientReset(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                fail("Should not be called")
            }
        }
        val config = SyncConfiguration.Builder(
            schema = PARTITION_BASED_SCHEMA,
            user = user,
            partitionValue = partitionValue
        ).syncClientResetStrategy(strategy)
            .build()
        assertEquals(strategy, config.syncClientResetStrategy)
    }

    @Test
    fun clientResetStrategy_automatic() {
        val user = createTestUser()
        val strategy = object : RecoverUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                fail("Should not be called")
            }

            override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                fail("Should not be called")
            }

            override fun onManualResetFallback(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                fail("Should not be called")
            }
        }
        val config = SyncConfiguration.Builder(
            schema = PARTITION_BASED_SCHEMA,
            user = user,
            partitionValue = partitionValue
        ).syncClientResetStrategy(strategy)
            .build()
        assertEquals(strategy, config.syncClientResetStrategy)
    }

    @Test
    fun clientResetStrategy_automaticOrDiscard() {
        val user = createTestUser()
        val strategy = object : RecoverOrDiscardUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                fail("Should not be called")
            }

            override fun onAfterRecovery(before: TypedRealm, after: MutableRealm) {
                fail("Should not be called")
            }

            override fun onAfterDiscard(before: TypedRealm, after: MutableRealm) {
                fail("Should not be called")
            }

            override fun onManualResetFallback(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                fail("Should not be called")
            }
        }
        val config = SyncConfiguration.Builder(
            schema = PARTITION_BASED_SCHEMA,
            user = user,
            partitionValue = partitionValue
        ).syncClientResetStrategy(strategy)
            .build()
        assertEquals(strategy, config.syncClientResetStrategy)
    }

    @Test
    fun equals_sameObject() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(
            schema = PARTITION_BASED_SCHEMA,
            user = user,
            partitionValue = partitionValue
        ).build()
        assertEquals(config, config)
    }

//    @Test
//    fun equals_sameConfigurations() {
//        val user = createTestUser()
//        val config1 = SyncConfiguration.Builder(
//            user = user,
//            partitionValue = DEFAULT_PARTITION_VALUE,
//            schema = setOf(Child::class)
//        ).build()
//        val config2 = SyncConfiguration.Builder(
//            user = user,
//            partitionValue = DEFAULT_PARTITION_VALUE,
//            schema = setOf(Child::class)
//        ).build()
//        assertEquals(config1.partitionValue, config2.partitionValue)
//    }
//
//    @Test
//    // FIXME Tests are not exhaustive
//    fun equals_not() {
//        val user1: User = createTestUser(app)
//        val user2: User = createTestUser(app)
//        val config1: SyncConfiguration = SyncConfiguration.Builder(user1, DEFAULT_PARTITION).build()
//        val config2: SyncConfiguration = SyncConfiguration.Builder(user2, DEFAULT_PARTITION).build()
//        assertFalse(config1 == config2)
//    }
//
//    @Test
//    fun hashCode_equal() {
//        val user: User = createTestUser(app)
//        val config: SyncConfiguration = SyncConfiguration.defaultConfig(user, DEFAULT_PARTITION)
//        assertEquals(config.hashCode(), config.hashCode())
//    }
//
//    @Test
//    fun hashCode_notEquals() {
//        val user1: User = createTestUser(app)
//        val user2: User = createTestUser(app)
//        val config1: SyncConfiguration = SyncConfiguration.defaultConfig(user1, DEFAULT_PARTITION)
//        val config2: SyncConfiguration = SyncConfiguration.defaultConfig(user2, DEFAULT_PARTITION)
//        assertNotEquals(config1.hashCode(), config2.hashCode())
//    }

    @Test
    fun equals_syncSpecificFields() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(
            schema = PARTITION_BASED_SCHEMA,
            user = user,
            partitionValue = partitionValue
        ).build()
        assertEquals(config.user, user)
        assertTrue(config.schema.containsAll(setOf(ParentPk::class, ChildPk::class)))
        assertEquals(SyncMode.PARTITION_BASED, config.syncMode)
    }

    @Test
    fun name_partitionBasedDefaultName() {
        val user: User = createTestUser()
        // Long
        verifyName(
            SyncConfiguration.Builder(user, 1234L, setOf()),
            "l_1234.realm"
        )

        // Int
        verifyName(
            SyncConfiguration.Builder(user, 123 as Int, setOf()),
            "i_123.realm"
        )

        // String
        verifyName(
            SyncConfiguration.Builder(user, "mypartition", setOf()),
            "s_mypartition.realm"
        )
    }

    private fun verifyName(builder: SyncConfiguration.Builder, expectedFileName: String) {
        val config = builder.build()
        val suffix = pathOf("", "mongodb-realm", config.user.app.configuration.appId, config.user.id, expectedFileName)
        assertTrue(config.path.contains(suffix), "${config.path} failed.")
        assertEquals(expectedFileName, config.name)
    }

    @Test
    fun allPartitionTypes() {
        val user: User = createTestUser()

        val partitionsAndRealmNames: Map<Any?, String> =
            enumValues<ValueType>().flatMap { valueType ->
                when (valueType) {
                    ValueType.STRING -> listOf(
                        "string" to "s_string",
                        null as String? to "null"
                    )

                    ValueType.INT -> listOf(
                        10.toInt() to "i_10",
                        null as Int? to "null",
                    )

                    ValueType.LONG -> listOf(
                        20.toLong() to "l_20",
                        null as Long? to "null"
                    )

                    ValueType.OBJECT_ID -> listOf(
                        BsonObjectId("62aafc72b9c357695ac489a7") to "o_62aafc72b9c357695ac489a7",
                        null as BsonObjectId? to "null",
                    )

                    ValueType.UUID -> listOf(
                        RealmUUID.from("80ac3926-29a4-4315-b373-2e2a33cf694f") to "u_80ac3926-29a4-4315-b373-2e2a33cf694f",
                        null as RealmUUID? to "null",
                    )

                    ValueType.NULL -> listOf(
                        null to "null"
                    )
                    else -> TODO("Test for partition type not defined")
                }
            }.toMap()

        // Validate SyncConfiguration.create
        partitionsAndRealmNames.forEach { (partition: Any?, name: String) ->
            val config = createWithGenericPartition(user, partition, setOf())
            assertTrue(config.path.endsWith("/$name.realm"), "${config.path} failed")
        }

        // Validate SyncConfiguration.Builder
        partitionsAndRealmNames.forEach { (partition: Any?, name: String) ->
            val config = createBuilderWithGenericPartition(user, partition, setOf())
            assertTrue(config.path.endsWith("/$name.realm"), "${config.path} failed")
        }
    }

    fun createWithGenericPartition(
        user: User,
        partitionValue: Any?,
        schema: Set<KClass<out RealmObject>>
    ) = when (partitionValue) {
        is String? -> SyncConfiguration.create(user, partitionValue, schema)
        is Int? -> SyncConfiguration.create(user, partitionValue, schema)
        is Long? -> SyncConfiguration.create(user, partitionValue, schema)
        is BsonObjectId? -> SyncConfiguration.create(user, partitionValue, schema)
        is RealmUUID? -> SyncConfiguration.create(user, partitionValue, schema)
        else -> TODO("Undefined partition type")
    }

    fun createBuilderWithGenericPartition(
        user: User,
        partitionValue: Any?,
        schema: Set<KClass<out RealmObject>>
    ) = when (partitionValue) {
        is String? -> SyncConfiguration.Builder(user, partitionValue, schema).build()
        is Int? -> SyncConfiguration.Builder(user, partitionValue, schema).build()
        is Long? -> SyncConfiguration.Builder(user, partitionValue, schema).build()
        is BsonObjectId? -> SyncConfiguration.Builder(user, partitionValue, schema).build()
        is RealmUUID? -> SyncConfiguration.Builder(user, partitionValue, schema).build()
        else -> TODO("Undefined partition type")
    }

    @Test
    fun name_withoutFileExtension() {
        nameAssertions("my-file-name")
    }

    @Test
    fun name_withDotRealmFileExtension() {
        nameAssertions("my-file-name.realm")
    }

    @Test
    fun name_otherFileExtension() {
        nameAssertions("my-file-name.database")
    }

    @Test
    fun name_similarToDefaultObjectStoreName() {
        nameAssertions("s_partition-9482732795133669400.realm")
    }

    @Test
    fun name_emptyValueThrows() {
        val user: User = createTestUser()
        val builder = SyncConfiguration.Builder(user, partitionValue, setOf())
        assertFailsWith<IllegalArgumentException> {
            builder.name("")
        }
    }

    @Test
    fun name_illegalValueThrows() {
        val user: User = createTestUser()
        val builder = SyncConfiguration.Builder(user, partitionValue, setOf())
        assertFailsWith<IllegalArgumentException> {
            builder.name(".realm")
        }
    }

    @Test
    fun encryption() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(
            schema = PARTITION_BASED_SCHEMA,
            user = user,
            partitionValue = partitionValue
        ).also { builder ->
            builder.encryptionKey(getRandomKey())
        }.build()
        assertNotNull(config.encryptionKey)
    }

    @Test
    fun encryption_wrongLength() {
        val user = createTestUser()
        val builder = SyncConfiguration.Builder(user, partitionValue, setOf())
        assertFailsWith<IllegalArgumentException> { builder.encryptionKey(byteArrayOf(1, 2, 3)) }
    }

    //    @Test
//    fun initialData() {
//        val user: User = createTestUser(app)
//        val config = configFactory.createSyncConfigurationBuilder(user)
//            .schema(SyncStringOnly::class.java)
//            .initialData(object : Realm.Transaction {
//                override fun execute(realm: Realm) {
//                    val stringOnly: SyncStringOnly = realm.createObject(ObjectId())
//                    stringOnly.chars = "TEST 42"
//                }
//            })
//            .build()
//        assertNotNull(config.initialDataTransaction)
//
//        // open the first time - initialData must be triggered
//        Realm.getInstance(config).use { realm ->
//            val results: RealmResults<SyncStringOnly> = realm.where<SyncStringOnly>().findAll()
//            assertEquals(1, results.size)
//            assertEquals("TEST 42", results.first()!!.chars)
//        }
//
//        // open the second time - initialData must not be triggered
//        Realm.getInstance(config).use { realm ->
//            assertEquals(1, realm.where<SyncStringOnly>().count())
//        }
//    }
//
//    @Test
//    fun defaultRxFactory() {
//        val user: User = createTestUser(app)
//        val config: SyncConfiguration = SyncConfiguration.defaultConfig(user, DEFAULT_PARTITION)
//        assertNotNull(config.rxFactory)
//    }
//
    @Test
    fun toString_nonEmpty() {
        val user: User = createTestUser()
        val config: SyncConfiguration = SyncConfiguration.create(user, partitionValue, setOf())
        val configStr = config.toString()
        assertTrue(configStr.isNotEmpty())
    }

    @Test
    fun useConfigOnOtherThread() = runBlocking {
        val user: User = createTestUser()
        // This should set both errorHandler and autoMigration callback
        val config: SyncConfiguration = SyncConfiguration.create(user, partitionValue, setOf())
        val dispatcher: CoroutineDispatcher = singleThreadDispatcher("config-test")
        withContext(dispatcher) {
            Realm.open(config).close()
        }
    }

    //
//    // Check that it is possible for multiple users to reference the same Realm URL while each user still use their
//    // own copy on the filesystem. This is e.g. what happens if a Realm is shared using a PermissionOffer.
//    @Test
//    fun multipleUsersReferenceSameRealm() {
//        val user1 = createTestUser()
//        val user2 = createTestUser()
//
//        val config1 = createSyncConfig(user1, DEFAULT_PARTITION_VALUE)
//        val config2 = createSyncConfig(user2, DEFAULT_PARTITION_VALUE)
//
//        // Verify that two different configurations can be used for the same URL
//        val realm1 = Realm.open(config1)
//        val realm2 = Realm.open(config2)
//        assertNotEquals(realm1, realm2)
//
//        realm1.close()
//        realm2.close()
//
//        // Verify that we actually save two different files
//        assertNotEquals(config1.path, config2.path)
//    }
//
    @Test
    fun with_throwsIfNotLoggedIn() = runBlocking {
        val user: User = createTestUser()
        user.logOut()
        assertFailsWith<IllegalArgumentException> { SyncConfiguration.create(user, "string", setOf()) }
        assertFailsWith<IllegalArgumentException> { SyncConfiguration.create(user, 123 as Int, setOf()) }
        assertFailsWith<IllegalArgumentException> { SyncConfiguration.create(user, 123L, setOf()) }
        Unit
    }

    @Test
    fun shouldWaitForInitialRemoteData() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(user, TestHelper.randomPartitionValue(), setOf())
            .waitForInitialRemoteData()
            .build()
        assertNotNull(config.initialRemoteData)
        assertEquals(Duration.INFINITE, config.initialRemoteData!!.timeout)
    }

    @Test
    fun getInitialRemoteDataTimeout() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(user, TestHelper.randomPartitionValue(), PARTITION_BASED_SCHEMA)
            .waitForInitialRemoteData(timeout = 10.seconds)
            .build()
        assertNotNull(config.initialRemoteData)
        assertEquals(10.seconds, config.initialRemoteData!!.timeout)
    }

    @Suppress("invisible_member", "invisible_reference")
    @Test
    fun supportedSchemaTypesWhenCreatingSyncConfiguration_partitionBased() {
        val user = createTestUser()
        val supportedSchemaTypes = setOf(
            FlexParentObject::class,
            FlexChildObject::class,
            FlexEmbeddedObject::class,
        )

        val validateConfig = { config: io.realm.kotlin.Configuration ->
            assertEquals(3, config.schema.size)
        }

        // Partition-based Sync
        enumValues<ValueType>().forEach {
            when (it) {
                ValueType.STRING -> {
                    validateConfig(SyncConfiguration.create(user, "", supportedSchemaTypes))
                    validateConfig(SyncConfiguration.Builder(user, "", supportedSchemaTypes).build())
                }
                ValueType.INT -> {
                    validateConfig(SyncConfiguration.create(user, 1 as Int, supportedSchemaTypes))
                    validateConfig(SyncConfiguration.Builder(user, 1 as Int, supportedSchemaTypes).build())
                }
                ValueType.LONG -> {
                    validateConfig(SyncConfiguration.create(user, 1L, supportedSchemaTypes))
                    validateConfig(SyncConfiguration.Builder(user, 1L, supportedSchemaTypes).build())
                }
                ValueType.OBJECT_ID -> {
                    validateConfig(SyncConfiguration.create(user, BsonObjectId(), supportedSchemaTypes))
                    validateConfig(SyncConfiguration.Builder(user, BsonObjectId(), supportedSchemaTypes).build())
                }
                ValueType.UUID -> {
                    validateConfig(SyncConfiguration.create(user, RealmUUID.random(), supportedSchemaTypes))
                    validateConfig(SyncConfiguration.Builder(user, RealmUUID.random(), supportedSchemaTypes).build())
                }
                ValueType.NULL -> {
                    validateConfig(SyncConfiguration.create(user, null as String?, supportedSchemaTypes))
                    validateConfig(SyncConfiguration.Builder(user, null as String?, supportedSchemaTypes).build())
                }
                else -> TODO("Test for partition type not defined")
            }
        }
    }

    @Suppress("invisible_member", "invisible_reference")
    @Test
    fun supportedSchemaTypesWhenCreatingSyncConfiguration_flexibleSync() {
        val user = createTestUser()
        val supportedSchemaTypes = setOf(
            FlexParentObject::class,
            FlexChildObject::class,
            FlexEmbeddedObject::class,
        )

        val validateConfig = { config: io.realm.kotlin.Configuration ->
            assertEquals(3, config.schema.size)
        }

        // Flexible Sync
        validateConfig(SyncConfiguration.create(user, supportedSchemaTypes))
        validateConfig(SyncConfiguration.Builder(user, supportedSchemaTypes).build())
    }

    @Test
    fun unsupportedSchemaTypesThrowException_partitionBasedSync() {
        val user = createTestUser()
        val unsupportedSchemaType = setOf(DynamicRealmObject::class)
        assertFailsWithMessage(IllegalArgumentException::class, "Only subclasses of RealmObject and EmbeddedRealmObject are allowed in the schema. Found: io.realm.kotlin.dynamic.DynamicRealmObject. If io.realm.kotlin.dynamic.DynamicRealmObject is a valid subclass: This class has not been modified by the Realm Compiler Plugin. Has the Realm Gradle Plugin been applied to the project with this model class?") {
            SyncConfiguration.create(user, "", unsupportedSchemaType)
        }
        assertFailsWithMessage(IllegalArgumentException::class, "Only subclasses of RealmObject and EmbeddedRealmObject are allowed in the schema. Found: io.realm.kotlin.dynamic.DynamicRealmObject. If io.realm.kotlin.dynamic.DynamicRealmObject is a valid subclass: This class has not been modified by the Realm Compiler Plugin. Has the Realm Gradle Plugin been applied to the project with this model class?") {
            SyncConfiguration.Builder(user, "", unsupportedSchemaType)
        }
    }

    @Test
    fun unsupportedSchemaTypesThrowException_flexibleSync() {
        val user = createTestUser()
        val unsupportedSchemaType = setOf(DynamicRealmObject::class)
        assertFailsWithMessage(IllegalArgumentException::class, "Only subclasses of RealmObject and EmbeddedRealmObject are allowed in the schema. Found: io.realm.kotlin.dynamic.DynamicRealmObject. If io.realm.kotlin.dynamic.DynamicRealmObject is a valid subclass: This class has not been modified by the Realm Compiler Plugin. Has the Realm Gradle Plugin been applied to the project with this model class?") {
            SyncConfiguration.create(user, unsupportedSchemaType)
        }
        assertFailsWithMessage(IllegalArgumentException::class, "Only subclasses of RealmObject and EmbeddedRealmObject are allowed in the schema. Found: io.realm.kotlin.dynamic.DynamicRealmObject. If io.realm.kotlin.dynamic.DynamicRealmObject is a valid subclass: This class has not been modified by the Realm Compiler Plugin. Has the Realm Gradle Plugin been applied to the project with this model class?") {
            SyncConfiguration.Builder(user, unsupportedSchemaType)
        }
    }

//
//    @Test
//    @Ignore("Not implemented yet")
//    fun getSessionStopPolicy() {
//    }
//
//    @Test
//    @Ignore("Not implemented yet")
//    fun getUrlPrefix() {
//    }

    @Ignore // Wait for https://github.com/realm/realm-kotlin/issues/648
    @Test
    fun getPartitionValue() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(
            schema = FLEXIBLE_SYNC_SCHEMA,
            user = user,
            partitionValue = partitionValue
        ).build()
        // Disabled until we have a proper BSON API
        // assertEquals(DEFAULT_PARTITION_VALUE, config.partitionValue.asString())
    }

//    @Test
//    fun clientResyncMode() {
//        val user: User = createTestUser(app)
//
//        // Default mode for full Realms
//        var config: SyncConfiguration = SyncConfiguration.defaultConfig(user, DEFAULT_PARTITION)
//        assertEquals(ClientResyncMode.MANUAL, config.clientResyncMode)
//
//        // Manually set the mode
//        config = SyncConfiguration.Builder(user, DEFAULT_PARTITION)
//            .clientResyncMode(ClientResyncMode.DISCARD_LOCAL_REALM)
//            .build()
//        assertEquals(ClientResyncMode.DISCARD_LOCAL_REALM, config.clientResyncMode)
//    }
//
//    @Test
//    fun clientResyncMode_throwsOnNull() {
//        val user: User = createTestUser(app)
//        val config: SyncConfiguration.Builder = SyncConfiguration.Builder(user, DEFAULT_PARTITION)
//        try {
//            config.clientResyncMode(TestHelper.getNull())
//            fail()
//        } catch (ignore: IllegalArgumentException) {
//        }
//    }
//
//    // If the same user create two configurations with different partition values they must
//    // resolve to different paths on disk.
//    @Test
//    fun differentPartitionValuesAreDifferentRealms() {
//        val user = createTestUser()
//        val config1 = createSyncConfig(user, "realm1")
//        val config2 = createSyncConfig(user, "realm2")
//        assertNotEquals(config1.path, config2.path)
//
//        assertTrue(config1.path.endsWith("${app.configuration.appId}/${user.id}/s_realm1.realm"))
//        assertTrue(config2.path.endsWith("${app.configuration.appId}/${user.id}/s_realm2.realm"))
//
//        // Check for https://github.com/realm/realm-java/issues/6882
//        val realm1 = Realm.open(config1)
//        try {
//            val realm2 = Realm.open(config2)
//            realm2.close()
//        } finally {
//            realm1.close()
//        }
//    }
//
//
//    @Test
//    fun loggedOutUsersThrows() {
//        val user: User = app.registerUserAndLogin(TestHelper.getRandomEmail(), "123456")
//        user.logOut()
//        assertFailsWith<java.lang.IllegalArgumentException> {
//            SyncConfiguration.defaultConfig(user, ObjectId())
//        }
//        assertFailsWith<java.lang.IllegalArgumentException> {
//            SyncConfiguration.defaultConfig(app.currentUser(), ObjectId())
//        }
//    }
//
//    @Test
//    fun allowQueriesOnUiThread_defaultsToTrue() {
//        val builder: SyncConfiguration.Builder = SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//        val configuration = builder.build()
//        assertTrue(configuration.isAllowQueriesOnUiThread)
//    }
//
//    @Test
//    fun allowQueriesOnUiThread_explicitFalse() {
//        val builder: SyncConfiguration.Builder = SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//        val configuration = builder.allowQueriesOnUiThread(false)
//            .build()
//        assertFalse(configuration.isAllowQueriesOnUiThread)
//    }
//
//    @Test
//    fun allowQueriesOnUiThread_explicitTrue() {
//        val builder: SyncConfiguration.Builder = SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//        val configuration = builder.allowQueriesOnUiThread(true)
//            .build()
//        assertTrue(configuration.isAllowQueriesOnUiThread)
//    }
//
//    @Test
//    fun allowWritesOnUiThread_defaultsToFalse() {
//        val builder: SyncConfiguration.Builder = SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//        val configuration = builder.build()
//        assertFalse(configuration.isAllowWritesOnUiThread)
//    }
//
//    @Test
//    fun allowWritesOnUiThread_explicitFalse() {
//        val builder: SyncConfiguration.Builder = SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//        val configuration = builder.allowWritesOnUiThread(false)
//            .build()
//        assertFalse(configuration.isAllowWritesOnUiThread)
//    }
//
//    @Test
//    fun allowWritesOnUiThread_explicitTrue() {
//        val builder: SyncConfiguration.Builder = SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//        val configuration = builder.allowWritesOnUiThread(true)
//            .build()
//        assertTrue(configuration.isAllowWritesOnUiThread)
//    }
//
//    @Test
//    fun rxFactory_defaultNonNull() {
//        val configuration = SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//            .build()
//        assertNotNull(configuration.rxFactory)
//    }
//
//    @Test
//    fun rxFactory_nullThrows() {
//        assertFailsWith<IllegalArgumentException> {
//            SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//                .rxFactory(TestHelper.getNull())
//        }.let {
//            assertTrue(it.message!!.contains("null"))
//        }
//    }
//
//    @Test
//    fun rxFactory() {
//        val factory = object: RxObservableFactory {
//            override fun from(realm: Realm): Flowable<Realm> {
//                return Flowable.just(null)
//            }
//
//            override fun from(realm: DynamicRealm): Flowable<DynamicRealm> {
//                return Flowable.just(null)
//            }
//
//            override fun <E : Any?> from(realm: Realm, results: RealmResults<E>): Flowable<RealmResults<E>> {
//                return Flowable.just(null)
//            }
//
//            override fun <E : Any?> from(realm: DynamicRealm, results: RealmResults<E>): Flowable<RealmResults<E>> {
//                return Flowable.just(null)
//            }
//
//            override fun <E : Any?> from(realm: Realm, list: RealmList<E>): Flowable<RealmList<E>> {
//                return Flowable.just(null)
//            }
//
//            override fun <E : Any?> from(realm: DynamicRealm, list: RealmList<E>): Flowable<RealmList<E>> {
//                return Flowable.just(null)
//            }
//
//            override fun <E : RealmModel?> from(realm: Realm, `object`: E): Flowable<E> {
//                return Flowable.just(null)
//            }
//
//            override fun from(realm: DynamicRealm, `object`: DynamicRealmObject): Flowable<DynamicRealmObject> {
//                return Flowable.just(null)
//            }
//
//            override fun <E : Any?> from(realm: Realm, query: RealmQuery<E>): Single<RealmQuery<E>> {
//                return Single.just(null)
//            }
//
//            override fun <E : Any?> from(realm: DynamicRealm, query: RealmQuery<E>): Single<RealmQuery<E>> {
//                return Single.just(null)
//            }
//
//            override fun <E : Any?> changesetsFrom(realm: Realm, results: RealmResults<E>): Observable<CollectionChange<RealmResults<E>>> {
//                return Observable.just(null)
//            }
//
//            override fun <E : Any?> changesetsFrom(realm: DynamicRealm, results: RealmResults<E>): Observable<CollectionChange<RealmResults<E>>> {
//                return Observable.just(null)
//            }
//
//            override fun <E : Any?> changesetsFrom(realm: Realm, list: RealmList<E>): Observable<CollectionChange<RealmList<E>>> {
//                return Observable.just(null)
//            }
//
//            override fun <E : Any?> changesetsFrom(realm: DynamicRealm, list: RealmList<E>): Observable<CollectionChange<RealmList<E>>> {
//                return Observable.just(null)
//            }
//
//            override fun <E : RealmModel?> changesetsFrom(realm: Realm, `object`: E): Observable<ObjectChange<E>> {
//                return Observable.just(null)
//            }
//
//            override fun changesetsFrom(realm: DynamicRealm, `object`: DynamicRealmObject): Observable<ObjectChange<DynamicRealmObject>> {
//                return Observable.just(null)
//            }
//
//        }
//
//        val configuration1 = SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//            .rxFactory(factory)
//            .build()
//        assertEquals(factory, configuration1.rxFactory)
//
//        val configuration2 = SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//            .build()
//        assertNotEquals(factory, configuration2.rxFactory)
//    }
//
//    @Test
//    fun flowFactory_defaultNonNull() {
//        val configuration = SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//            .build()
//        assertNotNull(configuration.flowFactory)
//    }
//
//    @Test
//    fun flowFactory_nullThrows() {
//        assertFailsWith<IllegalArgumentException> {
//            SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//                .flowFactory(TestHelper.getNull())
//        }.let {
//            assertTrue(it.message!!.contains("null"))
//        }
//    }
//
//    @Test
//    fun flowFactory() {
//        val factory = object : FlowFactory {
//            override fun from(realm: Realm): Flow<Realm> {
//                return flowOf()
//            }
//
//            override fun from(dynamicRealm: DynamicRealm): Flow<DynamicRealm> {
//                return flowOf()
//            }
//
//            override fun <T : Any?> from(realm: Realm, results: RealmResults<T>): Flow<RealmResults<T>> {
//                return flowOf()
//            }
//
//            override fun <T : Any?> from(dynamicRealm: DynamicRealm, results: RealmResults<T>): Flow<RealmResults<T>> {
//                return flowOf()
//            }
//
//            override fun <T : Any?> from(realm: Realm, realmList: RealmList<T>): Flow<RealmList<T>> {
//                return flowOf()
//            }
//
//            override fun <T : Any?> from(dynamicRealm: DynamicRealm, realmList: RealmList<T>): Flow<RealmList<T>> {
//                return flowOf()
//            }
//
//            override fun <T : RealmModel?> from(realm: Realm, realmObject: T): Flow<T> {
//                return flowOf()
//            }
//
//            override fun from(dynamicRealm: DynamicRealm, dynamicRealmObject: DynamicRealmObject): Flow<DynamicRealmObject> {
//                return flowOf()
//            }
//
//            override fun <T : Any?> changesetFrom(realm: Realm, results: RealmResults<T>): Flow<CollectionChange<RealmResults<T>>> {
//                return flowOf()
//            }
//
//            override fun <T : Any?> changesetFrom(dynamicRealm: DynamicRealm, results: RealmResults<T>): Flow<CollectionChange<RealmResults<T>>> {
//                return flowOf()
//            }
//
//            override fun <T : Any?> changesetFrom(realm: Realm, list: RealmList<T>): Flow<CollectionChange<RealmList<T>>> {
//                return flowOf()
//            }
//
//            override fun <T : Any?> changesetFrom(dynamicRealm: DynamicRealm, list: RealmList<T>): Flow<CollectionChange<RealmList<T>>> {
//                return flowOf()
//            }
//
//            override fun <T : RealmModel?> changesetFrom(realm: Realm, realmObject: T): Flow<ObjectChange<T>> {
//                return flowOf()
//            }
//
//            override fun changesetFrom(dynamicRealm: DynamicRealm, dynamicRealmObject: DynamicRealmObject): Flow<ObjectChange<DynamicRealmObject>> {
//                return flowOf()
//            }
//        }
//
//        val configuration1 = SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//            .flowFactory(factory)
//            .build()
//        assertEquals(factory, configuration1.flowFactory)
//
//        val configuration2 = SyncConfiguration.Builder(createTestUser(app), DEFAULT_PARTITION)
//            .build()
//        assertNotEquals(factory, configuration2.flowFactory)
//    }

    @Test
    fun syncClientResetStrategy_partitionBased() {
        val resetHandler = object : DiscardUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                fail("Should not be called")
            }

            override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                fail("Should not be called")
            }

            override fun onManualResetFallback(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                fail("Should not be called")
            }
        }
        val user = createTestUser()
        val config = SyncConfiguration.Builder(user, partitionValue, setOf())
            .syncClientResetStrategy(resetHandler)
            .build()
        assertEquals(resetHandler, config.syncClientResetStrategy)
    }

    @Test
    fun syncClientResetStrategy_partitionBased_defaultNotNull() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(user, partitionValue, setOf())
            .build()
        assertNotNull(config.syncClientResetStrategy)
        assertTrue(config.syncClientResetStrategy is RecoverOrDiscardUnsyncedChangesStrategy)
    }

    @Test
    fun syncClientResetStrategy_flexibleBased() {
        val resetHandler = object : ManuallyRecoverUnsyncedChangesStrategy {
            override fun onClientReset(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                fail("Should not be called")
            }
        }
        val user = createTestUser()
        val config = SyncConfiguration.Builder(user, setOf())
            .syncClientResetStrategy(resetHandler)
            .build()
        assertEquals(resetHandler, config.syncClientResetStrategy)
    }

    @Test
    fun syncClientResetStrategy_defaultNotNull() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(user, setOf())
            .build()
        assertNotNull(config.syncClientResetStrategy)
        assertTrue(config.syncClientResetStrategy is RecoverOrDiscardUnsyncedChangesStrategy)
    }

    @Test
    fun syncClientResetStrategy_automatic() {
        val strategy = object : RecoverUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                fail("Callback should not be reachable")
            }

            override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                fail("Callback should not be reachable")
            }

            override fun onManualResetFallback(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                fail("Callback should not be reachable")
            }
        }
        val user = createTestUser()
        val config = SyncConfiguration.Builder(user, partitionValue, setOf())
            .syncClientResetStrategy(strategy)
            .build()
        assertEquals(strategy, config.syncClientResetStrategy)
    }

    @Test
    fun syncClientResetStrategy_automaticOrDiscard() {
        val strategy = object : RecoverOrDiscardUnsyncedChangesStrategy {
            override fun onBeforeReset(realm: TypedRealm) {
                fail("Callback should not be reachable")
            }

            override fun onAfterRecovery(before: TypedRealm, after: MutableRealm) {
                fail("Callback should not be reachable")
            }

            override fun onAfterDiscard(before: TypedRealm, after: MutableRealm) {
                fail("Callback should not be reachable")
            }

            override fun onManualResetFallback(
                session: SyncSession,
                exception: ClientResetRequiredException
            ) {
                fail("Callback should not be reachable")
            }
        }
        val user = createTestUser()
        val config = SyncConfiguration.Builder(user, partitionValue, setOf())
            .syncClientResetStrategy(strategy)
            .build()
        assertEquals(strategy, config.syncClientResetStrategy)
    }

    @Test
    fun recoverUnsyncedChangesStrategyMode() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(user, partitionValue, setOf())
            .syncClientResetStrategy(object : RecoverUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    fail("Should not be called")
                }

                override fun onAfterReset(before: TypedRealm, after: MutableRealm) {
                    fail("Should not be called")
                }

                override fun onManualResetFallback(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    fail("Should not be called")
                }
            })
            .build()
        assertTrue(config.syncClientResetStrategy is RecoverUnsyncedChangesStrategy)
    }

    @Test
    fun recoverOrDiscardUnsyncedChangesStrategyMode() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(user, partitionValue, setOf())
            .syncClientResetStrategy(object : RecoverOrDiscardUnsyncedChangesStrategy {
                override fun onBeforeReset(realm: TypedRealm) {
                    fail("Should not be called")
                }

                override fun onAfterRecovery(before: TypedRealm, after: MutableRealm) {
                    fail("Should not be called")
                }

                override fun onAfterDiscard(before: TypedRealm, after: MutableRealm) {
                    fail("Should not be called")
                }

                override fun onManualResetFallback(
                    session: SyncSession,
                    exception: ClientResetRequiredException
                ) {
                    fail("Should not be called")
                }
            })
            .build()
        assertTrue(config.syncClientResetStrategy is RecoverOrDiscardUnsyncedChangesStrategy)
    }

    private fun createTestUser(): User = runBlocking {
        val (email, password) = randomEmail() to "password1234"
        app.createUserAndLogIn(email, password)
    }

    private fun nameAssertions(fileName: String) {
        val user: User = createTestUser()
        val config: SyncConfiguration = SyncConfiguration.Builder(user, partitionValue, setOf())
            .name(fileName)
            .build()

        val expectedFilename = if (fileName.endsWith(".realm")) fileName else "$fileName.realm"

        val suffix = pathOf("", "mongodb-realm", user.app.configuration.appId, user.id, expectedFilename)
        assertTrue(config.path.endsWith(suffix), "${config.path} failed.")
        assertEquals(expectedFilename, config.name, "${config.name} failed.")
    }
}
