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

package io.realm.test.mongodb.shared

import io.realm.Realm
import io.realm.entities.sync.ChildPk
import io.realm.entities.sync.ParentPk
import io.realm.internal.platform.createDefaultSystemLogger
import io.realm.internal.platform.runBlocking
import io.realm.log.LogLevel
import io.realm.mongodb.Credentials
import io.realm.mongodb.SyncConfiguration
import io.realm.mongodb.SyncException
import io.realm.mongodb.SyncSession
import io.realm.mongodb.User
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.asTestApp
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.TestHelper.getRandomKey
import io.realm.test.util.TestHelper.randomEmail
import okio.FileSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

const val DEFAULT_PARTITION_VALUE = "default"
const val DEFAULT_NAME = "test.realm"

@ExperimentalCoroutinesApi
class SyncConfigTests {

    private lateinit var tmpDir: String
    private lateinit var app: TestApp
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        app = TestApp(fileSystem = FileSystem.SYSTEM)
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.asTestApp.close()
        }
    }

    @Test
    fun logConfiguration() {
        val user = createTestUser()
        val logger = createDefaultSystemLogger("TEST", LogLevel.DEBUG)
        val customLoggers = listOf(logger)
        val config = SyncConfiguration.Builder(
            schema = setOf(ParentPk::class, ChildPk::class),
            user = user,
            partitionValue = DEFAULT_PARTITION_VALUE
        ).also { builder ->
            builder.log(LogLevel.DEBUG, customLoggers)
        }.build()
        assertEquals(LogLevel.DEBUG, config.log.level)
        assertEquals(logger, config.log.loggers[1]) // Additional logger placed after default logger
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
            schema = setOf(ParentPk::class, ChildPk::class),
            user = user,
            partitionValue = DEFAULT_PARTITION_VALUE
        ).also { builder ->
            builder.errorHandler(errorHandler)
        }.build()
        assertEquals(errorHandler, config.errorHandler)
    }

    @Test
    fun errorHandler_default() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(
            schema = setOf(ParentPk::class, ChildPk::class),
            user = user,
            partitionValue = DEFAULT_PARTITION_VALUE
        ).build()
        assertNotNull(config.errorHandler)
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
    fun equals_sameObject() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(
            schema = setOf(ParentPk::class, ChildPk::class),
            user = user,
            partitionValue = DEFAULT_PARTITION_VALUE
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
            schema = setOf(ParentPk::class, ChildPk::class),
            user = user,
            partitionValue = DEFAULT_PARTITION_VALUE
        ).build()
        assertEquals(config.partitionValue.asString(), DEFAULT_PARTITION_VALUE)
    }

//    @Test
//    fun name() {
//        val user: User = createTestUser(app)
//        val filename = "my-file-name.realm"
//        val config: SyncConfiguration = SyncConfiguration.Builder(user, DEFAULT_PARTITION)
//            .name(filename)
//            .build()
//        val suffix = "/mongodb-realm/${user.app.configuration.appId}/${user.id}/$filename"
//        assertTrue(config.path.endsWith(suffix))
//    }
//
//    @Test
//    fun name_illegalValuesThrows() {
//        val user: User = createTestUser(app)
//        val builder = SyncConfiguration.Builder(user, DEFAULT_PARTITION)
//
//        assertFailsWith<IllegalArgumentException> { builder.name(TestHelper.getNull()) }
//        assertFailsWith<IllegalArgumentException> { builder.name(".realm") }
//    }

    @Test
    fun encryption() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(
            schema = setOf(ParentPk::class, ChildPk::class),
            user = user,
            partitionValue = DEFAULT_PARTITION_VALUE
        ).also { builder ->
            builder.encryptionKey(getRandomKey())
        }.build()
        assertNotNull(config.encryptionKey)
    }

    @Test
    fun encryption_wrongLength() {
        val user = createTestUser()
        val builder = SyncConfiguration.Builder(user, DEFAULT_PARTITION_VALUE)
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
//    @Test
//    fun toString_nonEmpty() {
//        val user: User = createTestUser(app)
//        val config: SyncConfiguration = SyncConfiguration.defaultConfig(user, DEFAULT_PARTITION)
//        val configStr = config.toString()
//        assertTrue(configStr.isNotEmpty())
//    }
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
//    @Test
//    fun defaultConfiguration_throwsIfNotLoggedIn() {
//        // TODO Maybe we could avoid registering a real user
//        val user = app.registerUserAndLogin(TestHelper.getRandomEmail(), "123456")
//        user.logOut()
//        assertFailsWith<IllegalArgumentException> { SyncConfiguration.defaultConfig(user, DEFAULT_PARTITION) }
//    }
//
//    @Test
//    @Ignore("Not implemented yet")
//    fun shouldWaitForInitialRemoteData() {
//    }
//
//    @Test
//    @Ignore("Not implemented yet")
//    fun getInitialRemoteDataTimeout() {
//    }
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

    @Test
    fun getPartitionValue() {
        val user = createTestUser()
        val config = SyncConfiguration.Builder(
            schema = setOf(ParentPk::class, ChildPk::class),
            user = user,
            partitionValue = DEFAULT_PARTITION_VALUE
        ).build()
        assertEquals(DEFAULT_PARTITION_VALUE, config.partitionValue.asString())
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
//    @Test
//    fun nullPartitionValue() {
//        val user: User = createTestUser(app)
//
//        val configs = listOf<SyncConfiguration>(
//            SyncConfiguration.defaultConfig(user, null as String?),
//            SyncConfiguration.defaultConfig(user, null as Int?),
//            SyncConfiguration.defaultConfig(user, null as Long?),
//            SyncConfiguration.defaultConfig(user, null as ObjectId?),
//            SyncConfiguration.Builder(user, null as String?).build(),
//            SyncConfiguration.Builder(user, null as Int?).build(),
//            SyncConfiguration.Builder(user, null as Long?).build(),
//            SyncConfiguration.Builder(user, null as ObjectId?).build()
//        )
//
//        configs.forEach { config ->
//            assertTrue(config.path.endsWith("/null.realm"))
//        }
//    }
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

    private fun createTestUser(): User {
        val email = randomEmail()
        val password = "asdfasdf"
        app.asTestApp.createUser(email, password)
        return runBlocking {
            app.login(Credentials.emailPassword(email, password))
        }
    }
}
