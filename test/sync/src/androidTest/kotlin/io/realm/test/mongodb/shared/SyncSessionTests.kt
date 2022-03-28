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
import io.realm.internal.platform.runBlocking
import io.realm.mongodb.SyncConfiguration
import io.realm.mongodb.SyncSession
import io.realm.mongodb.User
import io.realm.mongodb.syncSession
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.asTestApp
import io.realm.test.mongodb.createUserAndLogIn
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.TestHelper
import io.realm.test.util.use
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

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
            app.asTestApp.close()
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
    fun session_sharedStateBetweenRealms() {
        val config1 = createSyncConfig(user, tmpDir, "realm1.realm")
        val config2 = createSyncConfig(user, tmpDir, "realm2.realm")
        val realm1 = Realm.open(config1)
        val realm2 = Realm.open(config2)
        assertNotEquals(realm1.configuration.path, realm2.configuration.path)
        assertNotSame(realm1.syncSession, realm2.syncSession)
        // TODO I don't think there is a good way to test this until we have more knobs to turn
        //  on SyncSession. One option is using `start/stop` and `getState()`.
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
