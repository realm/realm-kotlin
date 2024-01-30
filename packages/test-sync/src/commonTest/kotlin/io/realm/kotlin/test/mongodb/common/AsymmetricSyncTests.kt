/*
 * Copyright 2023 Realm Inc.
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
import io.realm.kotlin.dynamic.DynamicMutableRealm
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.InternalConfiguration
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.annotations.ExperimentalAsymmetricSyncApi
import io.realm.kotlin.mongodb.ext.insert
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.schema.RealmClassKind
import io.realm.kotlin.test.StandaloneDynamicMutableRealm
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.common.utils.uploadAllLocalChangesOrFail
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.use
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import org.mongodb.kbson.ObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalAsymmetricSyncApi::class)
class AsymmetricSyncTests {

    private lateinit var app: TestApp
    private lateinit var realm: Realm
    private lateinit var config: SyncConfiguration

    @BeforeTest
    fun setup() {
        app = TestApp(this::class.simpleName, appName = TEST_APP_FLEX, logLevel = LogLevel.ALL, builder = { builder ->
            builder.usePlatformNetworking(true)
            builder
        })
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
        config = SyncConfiguration.Builder(
            user,
            schema = FLEXIBLE_SYNC_SCHEMA
        ).errorHandler { session, error ->
            println(error)
        }.initialSubscriptions {
            it.query<DeviceParent>().subscribe()
        }.build()
        realm = Realm.open(config)
    }

    @AfterTest
    fun tearDown() {
        realm.close()
        runBlocking {
            app.deleteDocuments(app.clientAppId, Measurement::class.simpleName!!, "{}")
        }
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun insert() = runBlocking {
        val initialServerDocuments = app.countDocuments("Measurement")
        val newDocuments = 10
        realm.write {
            repeat(newDocuments) { no ->
                insert(
                    Measurement().apply {
                        value = 42.0f + no.toFloat()
                        device = Device(name = "living room", serialNumber = "TMP432142")
                    }
                )
            }
        }

        realm.syncSession.uploadAllLocalChangesOrFail()
        verifyDocuments(clazz = "Measurement", expectedCount = newDocuments, initialCount = initialServerDocuments)
    }

    @Test
    fun insert_samePrimaryKey_throws() {
        val generatedId = ObjectId()
        realm.writeBlocking {
            insert(
                Measurement().apply {
                    id = generatedId
                }
            )
            assertFailsWith<IllegalArgumentException>("foo") {
                insert(
                    Measurement().apply {
                        id = generatedId
                    }
                )
            }
        }
    }

    @Test
    fun realmClassSchema_isAsymmetric() {
        assertEquals(RealmClassKind.ASYMMETRIC, realm.schema()[Measurement::class.simpleName!!]!!.kind)
    }

    @Test
    fun findLatestThrows() {
        realm.writeBlocking {
            // There is no way to get a managed Measurement, but calling `findLatest` on an
            // unmanaged AsymmetricObjet should still fail.
            assertFailsWith<IllegalArgumentException> {
                findLatest(Measurement())
            }
        }
    }

    // If you have A -> B, where A is an asymmetric object and B is embedded it is still possible
    // to query for B. However, no objects belong to asymmetric objects will be found.
    @Test
    fun nestedEmbeddedHierarchyIsQueryable() = runBlocking {
        realm.syncSession.pause()
        realm.write {
            repeat(10) { no ->
                insert(
                    Measurement().apply {
                        value = 42.0f + no.toFloat()
                        device = Device(name = "living room", serialNumber = "TMP432142").apply {
                            backupDevice = BackupDevice(name = "kitchen", serialNumber = "TMP14090")
                        }
                    }
                )
            }
            repeat(10) {
                copyToRealm(
                    DeviceParent().apply {
                        device = Device(name = "living room", serialNumber = "TMP432142")
                    }
                )
            }
        }
        // Will only find the embedded objects belong to standard realm objects
        assertEquals(10, realm.query<Device>().count().find())

        // Nested embedded objects are not searchable either if the top-level is asymmetric.
        assertEquals(0, realm.query<BackupDevice>().count().find())
    }

    @Test
    fun deleteAll_doNotDeleteAsymmetricObjects() = runBlocking {
        val initialServerDocuments = app.countDocuments("Measurement")
        val newDocuments = 10
        realm.syncSession.pause()
        realm.write {
            repeat(newDocuments) { no ->
                insert(
                    Measurement().apply {
                        value = 42.0f + no.toFloat()
                        device = Device(name = "living room", serialNumber = "TMP432142")
                    }
                )
            }
        }

        // Deleting everything should not delete the asymmetric objects
        realm.write {
            deleteAll()
        }

        // Re-enable the sync session and verify that all objects made it to the server.
        realm.syncSession.run {
            resume()
            uploadAllLocalChanges(30.seconds)
        }
        verifyDocuments(clazz = "Measurement", expectedCount = newDocuments, initialCount = initialServerDocuments)
    }

    @Test
    fun mutableDynamicRealm_insert_unsuportedType() {
        useDynamicRealm { dynamicRealm: DynamicMutableRealm ->
            val realmObject = DynamicMutableRealmObject.create(DeviceParent::class.simpleName!!)
            assertFailsWith<IllegalArgumentException> {
                dynamicRealm.insert(realmObject)
            }
            val embeddedRealmObject = DynamicMutableRealmObject.create(BackupDevice::class.simpleName!!)
            assertFailsWith<IllegalArgumentException> {
                dynamicRealm.insert(embeddedRealmObject)
            }
        }
    }

    @Test
    fun mutableDynamicRealm_copyToRealm_throws() {
        useDynamicRealm { dynamicRealm: DynamicMutableRealm ->
            val asymmetricObject = DynamicMutableRealmObject.create(Measurement::class.simpleName!!)
            assertFailsWith<IllegalArgumentException> {
                dynamicRealm.copyToRealm(asymmetricObject)
            }
        }
    }

    @Test
    fun mutableDynamicRealm_query_throws() {
        useDynamicRealm { dynamicRealm: DynamicMutableRealm ->
            assertFailsWith<IllegalArgumentException> {
                dynamicRealm.query(Measurement::class.simpleName!!)
            }
        }
    }

    @Test
    fun mutableDynamicRealm_delete_throws() {
        useDynamicRealm { dynamicRealm: DynamicMutableRealm ->
            assertFailsWith<IllegalArgumentException> {
                dynamicRealm.delete(Measurement::class.simpleName!!)
            }
        }
    }

    // Verify that a schema of Asymmetric -> Embedded -> RealmObject work.
    @Test
    fun asymmetricSchema() = runBlocking {
        config = SyncConfiguration.Builder(
            app.login(Credentials.anonymous()),
            schema = FLEXIBLE_SYNC_SCHEMA
        ).build()
        val initialServerDocuments = app.countDocuments("AsymmetricA")
        Realm.open(config).use {
            it.write {
                insert(
                    AsymmetricA().apply {
                        child = EmbeddedB().apply {
                            StandardC()
                        }
                    }
                )
            }
            it.syncSession.uploadAllLocalChangesOrFail()
            verifyDocuments("AsymmetricA", 1, initialServerDocuments)
        }
    }

    private suspend fun verifyDocuments(clazz: String, expectedCount: Int, initialCount: Int) {
        // Variable shared across while must be atomic
        // https://youtrack.jetbrains.com/issue/KT-64139/Native-Bug-with-while-loop-coroutine-which-is-started-and-stopped-on-the-same-thread
        var documents = atomic(0)
        var found = false
        var attempt = 60 // Wait 1 minute
        // The translator might be slow to incorporate changes into MongoDB, so we retry for a bit
        // before giving up.
        while (!found && attempt > 0) {
            documents.value = app.countDocuments(clazz) - initialCount
            if (documents.value == expectedCount) {
                found = true
            } else {
                attempt -= 1
                delay(1.seconds)
            }
        }
        assertTrue(found, "Number of documents was: ${documents.value} [initialCount: $initialCount, expectedCount: $expectedCount]")
    }

    private fun useDynamicRealm(function: (DynamicMutableRealm) -> Unit) {
        val dynamicMutableRealm = StandaloneDynamicMutableRealm(realm.configuration as InternalConfiguration)
        dynamicMutableRealm.beginTransaction()
        try {
            function(dynamicMutableRealm)
        } finally {
            dynamicMutableRealm.close()
        }
    }
}
