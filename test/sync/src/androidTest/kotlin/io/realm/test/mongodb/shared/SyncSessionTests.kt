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
package io.realm.test.mongodb.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.sync.ChildPk
import io.realm.entities.sync.ParentPk
import io.realm.internal.interop.RealmInterop
import io.realm.internal.platform.runBlocking
import io.realm.mongodb.SyncConfiguration
import io.realm.mongodb.SyncException
import io.realm.mongodb.SyncSession
import io.realm.mongodb.User
import io.realm.mongodb.syncSession
import io.realm.query
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.asTestApp
import io.realm.test.mongodb.createUserAndLogIn
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.TestHelper
import io.realm.test.util.use
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class SyncSessionTests {

    private lateinit var user: User
    private lateinit var tmpDir: String
    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        app = TestApp()
        val (email, password) = TestHelper.randomEmail() to "password1234"
        user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
        tmpDir = PlatformUtils.createTempDir()
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun session() {
        val config = createSyncConfig(user, tmpDir)
        Realm.open(config).use { realm: Realm ->
            val session: SyncSession = realm.syncSession
            assertNotNull(session)
        }
    }

    // The same object is returned for each call to `Realm.session`
    @Test
    fun session_identity() {
        val config = createSyncConfig(user, tmpDir)
        Realm.open(config).use { realm: Realm ->
            val session1: SyncSession = realm.syncSession
            val session2: SyncSession = realm.syncSession
            assertSame(session1, session2)
        }
    }

    // If multiple instances of the same Realm is opened. The Kotlin SyncSession objects will
    // differ, but they point to the same underlying Core Sync Session.
    @Test
    @Suppress("invisible_reference", "invisible_member")
    fun session_sharedStateBetweenRealms() {
        val config1 = createSyncConfig(user, tmpDir, "realm1.realm")
        val config2 = createSyncConfig(user, tmpDir, "realm2.realm")
        val realm1 = Realm.open(config1)
        val realm2 = Realm.open(config2)
        assertNotEquals(realm1.configuration.path, realm2.configuration.path)
        assertNotSame(realm1.syncSession, realm2.syncSession)
        RealmInterop.realm_equals(
            (realm1.syncSession as io.realm.mongodb.internal.SyncSessionImpl).nativePointer,
            (realm2.syncSession as io.realm.mongodb.internal.SyncSessionImpl).nativePointer
        )
    }

    @Test
    fun session_localRealmThrows() {
        val config = RealmConfiguration.Builder(schema = setOf(ParentPk::class, ChildPk::class))
            .directory(tmpDir)
            .build()
        Realm.open(config).use { realm ->
            assertFailsWith<IllegalStateException> { realm.syncSession }
        }
    }

    @Test
    fun downloadAllServerChanges_illegalArgumentThrows() {
        openSyncRealm { realm ->
            val session: SyncSession = realm.syncSession
            assertFailsWith<IllegalArgumentException> {
                runBlocking {
                    session.downloadAllServerChanges(0.seconds)
                }
            }
            assertFailsWith<IllegalArgumentException> {
                runBlocking {
                    session.downloadAllServerChanges((-1).seconds)
                }
            }
        }
    }

    @Test
    fun downloadAllServerChanges_returnFalseOnTimeOut() {
        openSyncRealm { realm ->
            runBlocking {
                val session = realm.syncSession
                assertFalse(session.downloadAllServerChanges(timeout = 1.nanoseconds))
            }
        }
    }

    @Test
    fun uploadAllLocalChanges_illegalArgumentThrows() {
        openSyncRealm { realm ->
            val session: SyncSession = realm.syncSession
            assertFailsWith<IllegalArgumentException> {
                runBlocking {
                    session.uploadAllLocalChanges(0.seconds)
                }
            }
            assertFailsWith<IllegalArgumentException> {
                runBlocking {
                    session.uploadAllLocalChanges((-1).seconds)
                }
            }
        }
    }

    @Test
    fun uploadAllLocalChanges_returnFalseOnTimeOut() {
        openSyncRealm { realm ->
            runBlocking {
                val session = realm.syncSession
                assertFalse(session.uploadAllLocalChanges(timeout = 1.nanoseconds))
            }
        }
    }

    @Test
    fun uploadAndDownloadChangesSuccessfully() = runBlocking {
        val user1 = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")
        val user2 = app.createUserAndLogIn(TestHelper.randomEmail(), "123456")

        val config1 = SyncConfiguration.Builder(user1, DEFAULT_PARTITION_VALUE, schema = setOf(ParentPk::class, ChildPk::class))
            .directory(tmpDir)
            .name("user1.realm")
            .build()
        val config2 = SyncConfiguration.Builder(user2, DEFAULT_PARTITION_VALUE, schema = setOf(ParentPk::class, ChildPk::class))
            .directory(tmpDir)
            .name("user2.realm")
            .build()

        val realm1 = Realm.open(config1)
        val realm2 = Realm.open(config2)

        try {
            realm1.write {
                for (i in 0 until 10) {
                    copyToRealm(
                        ParentPk().apply {
                            _id = i.toString()
                        }
                    )
                }
            }
            assertEquals(10, realm1.query<ParentPk>().count().find())
            assertEquals(0, realm2.query<ParentPk>().count().find())
            assertTrue(realm1.syncSession.uploadAllLocalChanges())

            // Due to the Server Translator, there is a small delay between data
            // being uploaded and it not being immediately ready for download
            // on another Realm. In order to reduce the flakyness, we are
            // re-evaluating the assertion multiple times.
            for (i in 4 downTo 0) {
                assertTrue(realm2.syncSession.downloadAllServerChanges())
                val size = realm2.query<ParentPk>().count().find()
                when (size == 10L) {
                    true -> break // Test succeeded
                    false -> {
                        if (i == 0) {
                            throw kotlin.AssertionError("Realm failed to receive download data: $size")
                        }
                        delay(100)
                    }
                }
            }
        } finally {
            realm1.close()
            realm2.close()
        }
    }

    @Test
    fun uploadAndDownload_throwsInsideSyncErrorHandler() = runBlocking {
        val channel = Channel<SyncSession>(1)
        var wrongSchemaRealm: Realm? = null
        val job = async {

            // Create server side Realm with one schema
            var config = SyncConfiguration.Builder(user, DEFAULT_PARTITION_VALUE, schema = setOf(ParentPk::class, ChildPk::class))
                .directory(tmpDir)
                .build()
            val realm = Realm.open(config)
            realm.syncSession.uploadAllLocalChanges()
            realm.close()

            // Create same Realm with another schema, which will cause a Client Reset.
            config = SyncConfiguration.Builder(user, DEFAULT_PARTITION_VALUE, schema = setOf(ParentPk::class, io.realm.entities.sync.bogus.ChildPk::class))
                .directory(tmpDir)
                .name("new_realm.realm")
                .errorHandler(object : SyncSession.ErrorHandler {
                    override fun onError(session: SyncSession, error: SyncException) {
                        channel.trySend(session)
                    }
                })
                .build()
            wrongSchemaRealm = Realm.open(config)
        }
        val session = channel.receive()
        try {
            assertFailsWith<IllegalStateException> {
                session.uploadAllLocalChanges()
            }
            assertFailsWith<IllegalStateException> {
                session.downloadAllServerChanges()
            }
        } finally {
            wrongSchemaRealm?.close()
            job.cancel()
            channel.close()
        }
        Unit
    }

    @Ignore // TODO https://github.com/realm/realm-core/issues/5365
    @Test
    fun uploadDownload_throwsUnderlyingSyncError() {
        openSyncRealm { realm ->
            val session = realm.syncSession
            runBlocking {
                app.asTestApp.pauseSync()
                app.asTestApp.startSync()
            }
            assertFailsWith<SyncException> {
                runBlocking {
                    session.uploadAllLocalChanges()
                }
            }.also {
                assertTrue(it.message!!.contains("End of input", ignoreCase = true), it.message)
            }

            assertFailsWith<SyncException> {
                runBlocking {
                    session.downloadAllServerChanges()
                }
            }.also {
                assertTrue(it.message!!.contains("End of input", ignoreCase = true), it.message)
            }
        }
    }

    private fun openSyncRealm(block: (Realm) -> Unit) {
        val config = SyncConfiguration.Builder(user, DEFAULT_PARTITION_VALUE, schema = setOf(ParentPk::class, ChildPk::class))
            .directory(tmpDir)
            .build()
        Realm.open(config).use { realm ->
            block(realm)
        }
    }

    @Suppress("LongParameterList")
    private fun createSyncConfig(
        user: User,
        directory: String? = null,
        name: String = DEFAULT_NAME,
    ): SyncConfiguration = SyncConfiguration.Builder(
        schema = setOf(ParentPk::class, ChildPk::class),
        user = user,
        partitionValue = DEFAULT_PARTITION_VALUE
    )
        .directory(directory)
        .name(name)
        .build()
}
