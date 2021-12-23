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
import io.realm.internal.platform.runBlocking
import io.realm.log.LogLevel
import io.realm.mongodb.SyncConfiguration
import io.realm.mongodb.User
import io.realm.objects
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.asTestApp
import io.realm.test.mongodb.createUserAndLogIn
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.TestHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RealmSchemaTests {

    private lateinit var app: TestApp
    private lateinit var user: User

    @BeforeTest
    fun setup() {
        app = TestApp(logLevel = LogLevel.DEBUG)

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
    fun schemaChanged() = runBlocking {
        val dir1 = PlatformUtils.createTempDir()
        val partition = Random.nextInt().toString()
        val config1 = SyncConfiguration.Builder(user, partition, setOf(ChildPk::class))
            .log(LogLevel.DEBUG)
            .path(dir1 + "/tesadfst1.realm")
            .build()
        val realm1 = Realm.open(config1)
        assertEquals(1, realm1.schema().classes.size)

        delay(1000)
        println("opening second realm")

        val dir2 = PlatformUtils.createTempDir()
        val config2 =
            SyncConfiguration.Builder(user, partition, setOf(ChildPk::class, ParentPk::class))
                .log(LogLevel.DEBUG)
                .path(dir2 + "/test2.realm")
                .build()
        val realm2 = Realm.open(config2)
        assertNotNull(realm2)
        realm2.writeBlocking {
            println("child")
            copyToRealm(ChildPk())
            println("parent")
            copyToRealm(ParentPk())
        }

        // Wait until we have one child synced from realm2 which must mean that we also should have the updated schema
        val async = async {
            realm1.objects<ChildPk>().observe()
                .onEach {
                    println("Results: $it")
                }
                .takeWhile { it.size < 1 }
                .collect {
                    println("ASDFASDFASDF: ${it.size}")
                }
        }

        println("await")
        async.await()

        // Verify that the schema is in place
        assertEquals(2, realm1.schema().classes.size)

        realm1.close()
        realm2.close()
    }


}
