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

package io.realm.test.shared

import io.realm.Realm
import io.realm.query
import io.realm.realmListOf
import io.realm.RealmConfiguration
import io.realm.entities.embedded.EmbeddedChild
import io.realm.entities.embedded.EmbeddedParent
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmbeddedObjectTests {
    // copyToRealm throws on top level embedded
    // throws on multiple parents

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(schema = setOf(EmbeddedParent::class, EmbeddedChild::class))
                .directory(tmpDir)
                .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun copyToRealm() {
        val parent = EmbeddedParent().apply {
            child = EmbeddedChild().apply { name = "Imported child" }
            childList = realmListOf(EmbeddedChild().apply { name = "Imported list child 1" })
        }
        realm.writeBlocking {
            copyToRealm(parent)
        }
        // FIXME Requires updates on all values on import
        assertEquals(2, realm.query<EmbeddedChild>().find().size)
        val roundTripParent1 = realm.query<EmbeddedParent>().find().single()
        assertEquals("Imported child", roundTripParent1.child!!.name)
        assertEquals("Imported list child 1", roundTripParent1.childList[0]!!.name)

        val roundTripParent2 = realm.writeBlocking {
            findLatest(roundTripParent1)!!.apply {
                child = EmbeddedChild().apply { name = "Assigned child" }
                childList = realmListOf(
                    EmbeddedChild().apply { name = "Assigned list child 1" },
                    EmbeddedChild().apply { name = "Assigned list child 2" },
                )
            }
        }
        realm.query<EmbeddedChild>().find().run {
            assertEquals(3, count())
        }

        val roundTripParent3 = realm.writeBlocking {
            findLatest(roundTripParent2)!!.apply {
                childList.add(1, EmbeddedChild().apply { name = "Inserted list child" })
            }
        }
        realm.query<EmbeddedChild>().find().run {
            assertEquals(4, count())
        }
        assertEquals("Inserted list child", roundTripParent3.childList[1].name)

        val roundTripParent4 = realm.writeBlocking {
            findLatest(roundTripParent3)!!.apply {
                childList.set(1, EmbeddedChild().apply { name = "Updated list child" }).run {
                    // embeddedList.set returns newly created element instead of old one as the old
                    // one is deleted when overwritten and we cannot return null on non-nullable List<E>.set()
                    // assertEquals("Updated list child", name)
                }
            }
        }
        realm.query<EmbeddedChild>().find().run {
            assertEquals(4, count())
        }
        assertEquals("Updated list child", roundTripParent4.childList[1].name)

        // clear the embedded child again
        realm.writeBlocking {
            findLatest(roundTripParent4)?.apply {
                child = null
                childList.clear()
            }
        }
        assertEquals(0, realm.query<EmbeddedChild>().find().size)
    }

    // FIXME TEST Import of embedded object with multiple reference to same unmanaged object should reuse
    //  single instance
    // FIXME TEST Nested embedded objects

    // No longer possible since copyToRealm only accepts RealmObjects
    // @Test
    // fun copyToRealm_throwsOnEmbeddedObject() {
    //     realm.writeBlocking {
    //         assertFailsWithMessage<IllegalArgumentException>("Failed to create object of type 'EmbeddedChild'") {
    //             copyToRealm(EmbeddedChild())
    //         }
    //     }
    //
    // }
}
