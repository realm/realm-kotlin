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

package io.realm.kotlin.test.common.notifications

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.JsonStyleRealmObject
import io.realm.kotlin.ext.asFlow
import io.realm.kotlin.ext.realmAnyListOf
import io.realm.kotlin.ext.realmAnyOf
import io.realm.kotlin.ext.realmAnySetOf
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.notifications.DeletedSet
import io.realm.kotlin.notifications.InitialObject
import io.realm.kotlin.notifications.InitialSet
import io.realm.kotlin.notifications.ObjectChange
import io.realm.kotlin.notifications.SetChange
import io.realm.kotlin.notifications.UpdatedObject
import io.realm.kotlin.notifications.UpdatedSet
import io.realm.kotlin.test.common.utils.RealmEntityNotificationTests
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.types.RealmAny
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RealmAnyNestedCollectionNotificationTest {


    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(
            schema = setOf(JsonStyleRealmObject::class)
        ).directory(tmpDir)
            .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun objectNotificationsOnNestedCollections() = runBlocking<Unit> {
        val channel = Channel<ObjectChange<JsonStyleRealmObject>>()

        val o: JsonStyleRealmObject = realm.write {
            copyToRealm(JsonStyleRealmObject().apply {
                _id = "SET"
                value = realmAnyListOf(realmAnyListOf(1, 2, 3))
            })
        }

        val listener = async {
            o.asFlow().collect { change ->
                channel.send(change)
            }
        }

        assertIs<InitialObject<JsonStyleRealmObject>>(channel.receive())

        realm.write {
            findLatest(o)!!.value!!.asList()[0]!!.asList()[1] = RealmAny.create(4)
        }

        val objectUpdate = channel.receive()
        assertIs<UpdatedObject<JsonStyleRealmObject>>(objectUpdate)
        objectUpdate.run {
            assertEquals(1, changedFields.size)
            assertTrue(changedFields.contains("value"))
            val nestedList = obj.value!!.asList().first()!!.asList()
            assertEquals(listOf(1, 4, 3), nestedList.map { it!!.asInt() })
        }
        listener.cancel()
        channel.close()
    }
}

