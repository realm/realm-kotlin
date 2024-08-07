/*
 * Copyright 2024 Realm Inc.
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
@file:Suppress("invisible_member", "invisible_reference")

package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.BadFlexibleSyncQueryException
import io.realm.kotlin.mongodb.exceptions.DownloadingRealmTimeOutException
import io.realm.kotlin.mongodb.internal.SyncConfigurationImpl
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.mongodb.syncServerAppName
import io.realm.kotlin.test.mongodb.util.BaseAppInitializer
import io.realm.kotlin.test.mongodb.util.addEmailProvider
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PersistedName
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.coroutines.delay
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@PersistedName("Dog")
class DogV0 : RealmObject {
    @PrimaryKey
    var _id: BsonObjectId = BsonObjectId()

    var owner: String = ""

    var name: String = ""

    var breed: String = ""
}

@PersistedName("Dog")
class DogV1 : RealmObject {
    @PrimaryKey
    var _id: BsonObjectId = BsonObjectId()

    var owner: String = ""

    var name: String = ""
}

@PersistedName("Dog")
class DogV2 : RealmObject {
    @PrimaryKey
    var _id: BsonObjectId = BsonObjectId()

    var owner: String = ""

    var name: String? = ""
}

@PersistedName("Dog")
class DogV3 : RealmObject {
    @PrimaryKey
    var _id: BsonObjectId = BsonObjectId()

    var owner: String = ""

    var name: String? = ""

    var breed: ObjectId = ObjectId()
}

@PersistedName("Cat")
class CatV0 : RealmObject {
    @PrimaryKey
    var _id: BsonObjectId = BsonObjectId()

    var owner: String = ""

    var name: String? = ""
}

class SyncSchemaMigrationTests {

    private lateinit var user: User
    private lateinit var app: App

    fun createSyncConfig(
        dogSchema: KClass<out RealmObject>,
        catSchema: KClass<out RealmObject>? = null,
        version: Long,
    ) = SyncConfiguration
        .Builder(
            schema = setOfNotNull(dogSchema, catSchema),
            user = user,
        )
        .waitForInitialRemoteData(timeout = 30.seconds)
        .initialSubscriptions(false) { realm ->
            add(realm.query(dogSchema, "owner = $0", user.id))
            catSchema?.let {
                add(realm.query(catSchema, "owner = $0", user.id))
            }
        }
        .schemaVersion(version, timeout = 30.seconds)
        .build()

    private val SyncConfiguration.isSyncMigrationPending: Boolean
        //        get() = true
        get() {
            (this as SyncConfigurationImpl)

            val configPtr = this.createNativeConfiguration()
            return this.isSyncMigrationPending(configPtr)
        }

    @BeforeTest
    fun setup() {
        app = TestApp(
            this::class.simpleName,
            object : BaseAppInitializer(syncServerAppName("schver"), { app ->
                addEmailProvider(app)

                val database = app.clientAppId

                val namesToIds = app.setSchema(
                    schema = setOf(DogV0::class, CatV0::class)
                )

                app.mongodbService.setSyncConfig(
                    """
                    {
                        "flexible_sync": {
                            "state": "enabled",
                            "database_name": "$database",
                            "is_recovery_mode_disabled": false,
                            "queryable_fields_names": [
                                "owner"
                            ]
                        }
                    }
                    """.trimIndent()
                )

                while (!app.initialSyncComplete()) {
                    delay(500)
                }

                app.updateSchema(
                    ids = namesToIds,
                    schema = setOf(DogV1::class)
                )

                app.updateSchema(
                    ids = namesToIds,
                    schema = setOf(DogV2::class)
                )

                // Additive change does not bump version
                app.updateSchema(
                    ids = namesToIds,
                    schema = setOf(DogV3::class)
                )

                // Deleting an schema would bump the version
                app.deleteSchema(namesToIds["Cat"]!!)

                app.waitForSchemaVersion(3)
            }) {}
        )

        val (email, password) = TestHelper.randomEmail() to "password1234"
        user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.asTestApp.close()
        }
    }

    @Test
    fun validateDefaultTimeout() {
        val config = SyncConfiguration
            .Builder(
                schema = setOf(),
                user = user,
            )
            .schemaVersion(0)
            .build()

        assertNull(config.initialRemoteData)
        assertEquals(Duration.INFINITE, config.schemaMigrationRemoteData!!.timeout)
    }

    @Test
    fun validateTimeouts() {
        SyncConfiguration
            .Builder(
                schema = setOf(),
                user = user,
            )
            .waitForInitialRemoteData(timeout = 60.seconds)
            .schemaVersion(0, timeout = 30.seconds)
            .build()
            .let { config ->
                assertEquals(60.seconds, config.initialRemoteData!!.timeout)
                assertEquals(30.seconds, config.schemaMigrationRemoteData!!.timeout)
            }
    }

    // bump version but same schema
    @Test
    fun bumpVersionNotSchema() {
        createSyncConfig(
            dogSchema = DogV0::class,
            version = 0,
        ).let { config ->
            assertFalse(config.isSyncMigrationPending)

            Realm.open(config).use { realm ->
                assertNotNull(realm)
            }
        }

        // Destructive change on server schema
        assertFailsWith<BadFlexibleSyncQueryException> {
            createSyncConfig(
                dogSchema = DogV0::class,
                version = 1,
            ).let { config ->
                assertTrue(config.isSyncMigrationPending)

                Realm.open(config).use { realm ->
                    assertNotNull(realm)
                }
            }
        }
    }

    // change schema but not version
    @Test
    fun changeSchemaButNotVersion() {
        createSyncConfig(
            dogSchema = DogV0::class,
            version = 0,
        ).let { config ->
            assertFalse(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
            }
        }

        // It is a destructive change on the client schema so it works normally.
        createSyncConfig(
            dogSchema = DogV1::class,
            version = 0,
        ).let { config ->
            assertFalse(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
            }
        }

        // Invalid schema change
        assertFailsWith<IllegalStateException>("The following changes cannot be made in additive-only schema mode") {
            createSyncConfig(
                dogSchema = DogV2::class,
                version = 0,
            ).let { config ->
                // Destructive schema change would trigger a migration, even if schema versions
                // match.
                assertFalse(config.isSyncMigrationPending)

                Realm.open(config).use { realm ->
                    assertNotNull(realm)
                }
            }
        }
    }

    // fails with future schema version
    @Test
    fun failsWithNonExistingSchemaVersionFirstOpen() {
        assertFailsWithMessage<IllegalStateException>("Client provided invalid schema version") {
            createSyncConfig(
                dogSchema = DogV2::class,
                version = 5,
            ).let { config ->
                assertFalse(config.isSyncMigrationPending)
                Realm.open(config).use { realm ->
                    assertNotNull(realm)
                }
            }
        }
    }

    @Test
    fun failsWithNonExistingSchemaVersion() {
        createSyncConfig(
            dogSchema = DogV0::class,
            version = 0,
        ).let { config ->
            assertFalse(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
            }
        }

        assertFailsWithMessage<IllegalStateException>("Client provided invalid schema version") {
            createSyncConfig(
                dogSchema = DogV2::class,
                version = 5,
            ).let { config ->
                assertTrue(config.isSyncMigrationPending)
                Realm.open(config).use { realm ->
                    assertNotNull(realm)
                }
            }
        }
    }

    // migrate consecutive
    @Test
    fun migrateConsecutiveVersions() {
        createSyncConfig(
            dogSchema = DogV0::class,
            version = 0,
        ).let { config ->
            assertFalse(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
            }
        }

        createSyncConfig(
            dogSchema = DogV1::class,
            version = 1,
        ).let { config ->
            assertTrue(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
            }
        }

        // DogV2 and DogV3 share same schema version
        // because they only differ with additive changes.
        createSyncConfig(
            dogSchema = DogV2::class,
            version = 2,
        ).let { config ->
            assertTrue(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
            }
        }

        createSyncConfig(
            dogSchema = DogV3::class,
            version = 2,
        ).let { config ->
            assertFalse(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
            }
        }
    }

    // migrate skipping
    @Test
    fun migrateSkippingVersions() {
        createSyncConfig(
            dogSchema = DogV0::class,
            version = 0,
        ).let { config ->
            assertFalse(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
            }
        }

        createSyncConfig(
            dogSchema = DogV2::class,
            version = 2,
        ).let { config ->
            assertTrue(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
            }
        }
    }

    // migrate skipping
    @Test
    fun downgradeSchema() {
        createSyncConfig(
            dogSchema = DogV2::class,
            version = 2,
        ).let { config ->
            assertFalse(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
            }
        }

        createSyncConfig(
            dogSchema = DogV0::class,
            version = 0,
        ).let { config ->
            assertTrue(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
            }
        }
    }

    @Test
    fun dataVisibility_consecutive() {
        // DogV0 is incompatible with DogV3 changes

        // There is an issue in the baas server that prevents
        // from bootstrapping when a property type changes.
        //
        // see https://jira.mongodb.org/browse/BAAS-31935

//        createSyncConfig(
//            schema = DogV0::class,
//            version = 0,
//        ).let { config ->
//            assertFalse(config.isSyncMigrationPending)
//            Realm.open(config).use { realm ->
//                assertNotNull(realm)
//                assertEquals(0, realm.query<DogV0>().count().find())
//
//                realm.writeBlocking {
//                    copyToRealm(
//                        DogV0().apply {
//                            name = "v0"
//                        }
//                    )
//                }
//
//                runBlocking {
//                    realm.syncSession.uploadAllLocalChanges()
//                }
//            }
//        }

        createSyncConfig(
            dogSchema = DogV1::class,
            catSchema = CatV0::class,
            version = 1,
        ).let { config ->
            assertFalse(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
                assertEquals(0, realm.query<DogV1>("name = 'v0'").count().find())
                assertEquals(0, realm.query<CatV0>("name = 'v0'").count().find())

                realm.writeBlocking {
                    copyToRealm(
                        DogV1().apply {
                            name = "v1"
                            owner = user.id
                        }
                    )

                    copyToRealm(
                        CatV0().apply {
                            name = "v0"
                            owner = user.id
                        }
                    )
                }

                runBlocking {
                    realm.syncSession.uploadAllLocalChanges()
                }
            }
        }

        createSyncConfig(
            dogSchema = DogV2::class,
            catSchema = CatV0::class,
            version = 2,
        ).let { config ->
            assertTrue(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
                assertEquals(0, realm.query<DogV2>("name = 'v0'").count().find())
                assertEquals(1, realm.query<DogV2>("name = 'v1'").count().find())
                assertEquals(0, realm.query<CatV0>("name = 'v0'").count().find())

                realm.writeBlocking {
                    copyToRealm(
                        DogV2().apply {
                            name = "v2"
                            owner = user.id
                        }
                    )

                    copyToRealm(
                        CatV0().apply {
                            name = "v0"
                            owner = user.id
                        }
                    )
                }

                runBlocking {
                    realm.syncSession.uploadAllLocalChanges()
                }
            }
        }

        createSyncConfig(
            dogSchema = DogV3::class,
            version = 3,
        ).let { config ->
            assertTrue(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
                assertEquals(0, realm.query<DogV3>("name = 'v0'").count().find())
                assertEquals(1, realm.query<DogV3>("name = 'v1'").count().find())
                assertEquals(1, realm.query<DogV3>("name = 'v2'").count().find())

                realm.writeBlocking {
                    copyToRealm(
                        DogV3().apply {
                            name = "v3"
                            owner = user.id
                        }
                    )
                }

                assertEquals(1, realm.query<DogV3>("name = 'v3'").count().find())

                runBlocking {
                    realm.syncSession.uploadAllLocalChanges()
                }
            }
        }
    }

    // There is an issue in the baas server that prevents
    // from bootstrapping when a property type changes.
    //
    // see https://jira.mongodb.org/browse/BAAS-31935
    @Test
    fun dataVisibility_downgradeWithPropertyTypeChange_throws() {
        createSyncConfig(
            dogSchema = DogV3::class,
            version = 3,
        ).let { config ->
            assertFalse(config.isSyncMigrationPending)
            Realm.open(config).use { realm ->
                assertNotNull(realm)
                assertEquals(0, realm.query<DogV3>().count().find())

                realm.writeBlocking {
                    copyToRealm(
                        DogV3().apply {
                            name = "v3"
                            owner = user.id
                        }
                    )
                }

                runBlocking {
                    realm.syncSession.uploadAllLocalChanges()
                }
            }
        }

        createSyncConfig(
            dogSchema = DogV0::class,
            version = 0,
        ).let { config ->
            assertTrue(config.isSyncMigrationPending)
            assertFailsWith<DownloadingRealmTimeOutException> {
                Realm.open(config)
            }
        }
    }
}
