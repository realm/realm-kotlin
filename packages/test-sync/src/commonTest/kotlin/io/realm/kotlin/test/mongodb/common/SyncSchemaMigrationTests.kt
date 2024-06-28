@file:Suppress("invisible_member", "invisible_reference")

package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.BadFlexibleSyncQueryException
import io.realm.kotlin.mongodb.sync.InitialSubscriptionsCallback
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.mongodb.syncServerAppName
import io.realm.kotlin.test.mongodb.util.BaseAppInitializer
import io.realm.kotlin.test.mongodb.util.addEmailProvider
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.BaseRealmObject
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Tests for [io.realm.kotlin.mongodb.sync.Sync] that is accessed through
 * [io.realm.kotlin.mongodb.App.sync].
 */

@PersistedName("Dog")
class DogV0 : RealmObject {
    @PrimaryKey
    var _id: BsonObjectId = BsonObjectId()

    var name: String = ""

    var breed: String = ""
}

@PersistedName("Dog")
class DogV1 : RealmObject {
    @PrimaryKey
    var _id: BsonObjectId = BsonObjectId()

    var name: String = ""
}

@PersistedName("Dog")
class DogV2 : RealmObject {
    @PrimaryKey
    var _id: BsonObjectId = BsonObjectId()

    var name: String? = ""
}

@PersistedName("Dog")
class DogV3 : RealmObject {
    @PrimaryKey
    var _id: BsonObjectId = BsonObjectId()

    var name: String? = ""

    var breed: ObjectId = ObjectId()
}

class SyncSchemaMigrationTests {

    private lateinit var user: User
    private lateinit var app: App

    fun openRealm(
        schema: Set<KClass<out BaseRealmObject>>,
        version: Long,
        initialSubscriptionBlock: InitialSubscriptionsCallback = InitialSubscriptionsCallback { },
    ) = Realm.open(
        SyncConfiguration
            .Builder(
                schema = schema,
                user = user,
            )
            .waitForInitialRemoteData()
            .initialSubscriptions(false, initialSubscriptionBlock)
            .schemaVersion(version)
            .build()
    )

    @BeforeTest
    fun setup() {
        app = TestApp(
            this::class.simpleName,
            object : BaseAppInitializer(syncServerAppName("schver"), { app ->
                addEmailProvider(app)

                val database = app.clientAppId

                val namesToIds = app.setSchema(
                    schema = setOf(DogV0::class)
                )

                app.mongodbService.setSyncConfig(
                    """
                    {
                        "flexible_sync": {
                            "state": "enabled",
                            "database_name": "$database",
                            "is_recovery_mode_disabled": false,
                            "queryable_fields_names": [
                                "name",
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

                // TODO do we support deleting tables?
//                app.deleteSchema(namesToIds["Dog"]!!)

                app.waitForSchemaVersion(2)
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

    // bump version but same schema
    @Test
    fun bumpVersionNotSchema() {
        openRealm(
            schema = setOf(DogV0::class),
            version = 0,
        ) { realm ->
            add(realm.query<DogV0>())
        }.use {
            assertNotNull(it)
        }

        // Destructive change on server schema
        assertFailsWith<BadFlexibleSyncQueryException> {
            openRealm(
                schema = setOf(DogV0::class),
                version = 1,
            ).use {
                assertNotNull(it)
            }
        }
    }

    // change schema but not version
    @Test
    fun changeSchemaButNotVersion() {
        openRealm(
            schema = setOf(DogV0::class),
            version = 0,
        ) { realm ->
            add(realm.query<DogV0>())
        }.use {
            assertNotNull(it)
        }

        // It is a destructive change on the client schema so it works normally.
        openRealm(
            schema = setOf(DogV1::class),
            version = 0,
        ).use {
            assertNotNull(it)
        }

        // Invalid schema change
        assertFailsWith<IllegalStateException>("The following changes cannot be made in additive-only schema mode") {
            openRealm(
                schema = setOf(DogV2::class),
                version = 0,
            ).use {
                assertNotNull(it)
            }
        }
    }

    // fails with future schema version
    @Test
    fun failsWithFutureSchemaVersion() {
        assertFailsWithMessage<IllegalStateException>("Client provided invalid schema version") {
            openRealm(
                schema = setOf(DogV2::class),
                version = 5,
            ) { realm ->
                add(realm.query<DogV2>())
            }.use {
                assertNotNull(it)
            }
        }
    }

    // migrate consecutive
    @Test
    fun migrateConsecutiveVersions() {
        openRealm(
            schema = setOf(DogV0::class),
            version = 0,
        ) { realm ->
            add(realm.query<DogV0>())
        }.use {
            assertNotNull(it)
        }

        openRealm(
            schema = setOf(DogV1::class),
            version = 1,
        ).use {
            assertNotNull(it)
        }

        // DogV2 and DogV3 share same schema version
        // because they only differ with additive changes.
        openRealm(
            schema = setOf(DogV2::class),
            version = 2,
        ).use {
            assertNotNull(it)
        }

        openRealm(
            schema = setOf(DogV3::class),
            version = 2,
        ).use {
            assertNotNull(it)
        }
    }


    // migrate skipping
    @Test
    fun migrateSkippingVersions() {
        openRealm(
            schema = setOf(DogV0::class),
            version = 0,
        ) { realm ->
            add(realm.query<DogV0>())
        }.use {
            assertNotNull(it)
        }

        openRealm(
            schema = setOf(DogV2::class),
            version = 2,
        ).use {
            assertNotNull(it)
        }
    }

    // migrate skipping
    @Test
    fun downgradeSchema() {
        openRealm(
            schema = setOf(DogV2::class),
            version = 2,
        ) { realm ->
            add(realm.query<DogV2>())
        }.use {
            assertNotNull(it)
        }

        openRealm(
            schema = setOf(DogV0::class),
            version = 0,
        ).use {
            assertNotNull(it)
        }
    }
}
