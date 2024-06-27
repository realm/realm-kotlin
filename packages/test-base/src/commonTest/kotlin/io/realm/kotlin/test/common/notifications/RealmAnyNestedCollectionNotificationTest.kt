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
import io.realm.kotlin.ext.realmAnyDictionaryOf
import io.realm.kotlin.ext.realmAnyListOf
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.notifications.DeletedObject
import io.realm.kotlin.notifications.InitialObject
import io.realm.kotlin.notifications.ObjectChange
import io.realm.kotlin.notifications.UpdatedObject
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.types.RealmAny
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
            copyToRealm(
                JsonStyleRealmObject().apply {
                    value = realmAnyListOf(realmAnyListOf(1, 2, 3))
                }
            )
        }

        val listener = async {
            o.asFlow().collect { change ->
                channel.send(change)
            }
        }

        assertIs<InitialObject<JsonStyleRealmObject>>(channel.receiveOrFail())

        realm.write {
            findLatest(o)!!.value!!.asList()[0]!!.asList()[1] = RealmAny.create(4)
        }

        val objectUpdate = channel.receiveOrFail()
        assertIs<UpdatedObject<JsonStyleRealmObject>>(objectUpdate)
        objectUpdate.run {
            assertEquals(1, changedFields.size)
            assertTrue(changedFields.contains("value"))
            val nestedList = obj.value!!.asList().first()!!.asList()
            assertEquals(listOf(1, 4, 3), nestedList.map { it!!.asInt() })
        }

        // List operations
        realm.write {
            findLatest(o)!!.value = realmAnyListOf(1, 2, 3)
        }
        channel.receiveOrFail().apply {
            assertTrue { this is UpdatedObject<JsonStyleRealmObject> }
            assertContentEquals(realmAnyListOf(1, 2, 3).asList(), this.obj!!.value!!.asList())
        }

        // List add
        realm.write {
            findLatest(o)!!.value!!.asList().add(RealmAny.create("Realm"))
        }
        channel.receiveOrFail().apply {
            assertTrue { this is UpdatedObject<JsonStyleRealmObject> }
            assertContentEquals(realmAnyListOf(1, 2, 3, "Realm").asList(), this.obj!!.value!!.asList())
        }

        // List remove
        realm.write {
            findLatest(o)!!.value!!.asList().remove(RealmAny.create(2))
        }
        channel.receiveOrFail().apply {
            assertTrue { this is UpdatedObject<JsonStyleRealmObject> }
            assertContentEquals(realmAnyListOf(1, 3, "Realm").asList(), this.obj!!.value!!.asList())
        }

        // Dictionary operations
        realm.write {
            findLatest(o)!!.value = realmAnyDictionaryOf("key1" to 1, "key2" to 2, "key3" to 3)
        }

        channel.receiveOrFail().apply {
            assertTrue { this is UpdatedObject<JsonStyleRealmObject> }
            assertEquals(realmAnyDictionaryOf("key1" to 1, "key2" to 2, "key3" to 3).asDictionary(), this.obj!!.value!!.asDictionary())
        }

        realm.write {
            findLatest(o)!!.value!!.asDictionary()["key4"] = RealmAny.create("Realm")
        }

        channel.receiveOrFail().apply {
            assertTrue { this is UpdatedObject<JsonStyleRealmObject> }
            assertEquals(realmAnyDictionaryOf("key1" to 1, "key2" to 2, "key3" to 3, "key4" to "Realm").asDictionary(), this.obj!!.value!!.asDictionary())
        }

        realm.write {
            findLatest(o)!!.value!!.asDictionary().remove("key2")
        }

        channel.receiveOrFail().apply {
            assertTrue { this is UpdatedObject<JsonStyleRealmObject> }
            assertEquals(realmAnyDictionaryOf("key1" to 1, "key3" to 3, "key4" to "Realm").asDictionary(), this.obj!!.value!!.asDictionary())
        }

        realm.write {
            delete(findLatest(o)!!)
        }

        assertIs<DeletedObject<JsonStyleRealmObject>>(channel.receiveOrFail())

        listener.cancel()
        channel.close()
    }
}
