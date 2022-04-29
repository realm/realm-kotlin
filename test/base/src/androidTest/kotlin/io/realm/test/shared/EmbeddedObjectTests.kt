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
import io.realm.RealmConfiguration
import io.realm.entities.Sample
import io.realm.entities.embedded.EmbeddedChild
import io.realm.entities.embedded.EmbeddedParent
import io.realm.entities.link.Child
import io.realm.entities.link.Parent
import io.realm.test.assertFailsWithMessage
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
        }
        realm.writeBlocking {
            copyToRealm(parent)
        }
        // FIXME Requires updates on all values on import
        assertEquals(1, realm.query<EmbeddedChild>().find().size)
        val roundTripParent = realm.query<EmbeddedParent>().find().single()
        val roundTripChild = roundTripParent.child
        assertEquals("Imported child", roundTripChild!!.name)

        realm.writeBlocking {
            findLatest(roundTripParent)?.apply {
                child = EmbeddedChild().apply { name = "Assigned child" }
            }
        }
        realm.query<EmbeddedChild>().find().single().run {
            assertEquals("Assigned child", name)
        }

        // clear the embedded child again
        realm.writeBlocking {
            findLatest(roundTripParent)?.apply { child = null }
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
