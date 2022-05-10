@file:Suppress("invisible_member", "invisible_reference")
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

package io.realm.test.shared.dynamic

import io.realm.RealmConfiguration
import io.realm.RealmResults
import io.realm.dynamic.DynamicMutableRealm
import io.realm.dynamic.DynamicMutableRealmObject
import io.realm.dynamic.DynamicRealmObject
import io.realm.dynamic.getNullableValue
import io.realm.dynamic.getValue
import io.realm.dynamic.getValueList
import io.realm.entities.Sample
import io.realm.entities.embedded.EmbeddedChild
import io.realm.entities.embedded.EmbeddedInnerChild
import io.realm.entities.embedded.EmbeddedParent
import io.realm.entities.primarykey.PrimaryKeyString
import io.realm.entities.primarykey.PrimaryKeyStringNullable
import io.realm.internal.InternalConfiguration
import io.realm.isManaged
import io.realm.isValid
import io.realm.query.RealmQuery
import io.realm.query.RealmSingleQuery
import io.realm.test.StandaloneDynamicMutableRealm
import io.realm.test.assertFailsWithMessage
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicMutableRealmTests {
    private lateinit var tmpDir: String
    private lateinit var configuration: RealmConfiguration
    private lateinit var dynamicMutableRealm: DynamicMutableRealm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration =
            RealmConfiguration.Builder(
                schema = setOf(
                    Sample::class,
                    PrimaryKeyString::class,
                    PrimaryKeyStringNullable::class,
                    EmbeddedParent::class,
                    EmbeddedChild::class,
                    EmbeddedInnerChild::class,
                )
            )
                .directory(tmpDir).build()

        dynamicMutableRealm =
            StandaloneDynamicMutableRealm(configuration as InternalConfiguration).apply {
                beginTransaction()
            }
    }

    @AfterTest
    fun tearDown() {
        if (this::dynamicMutableRealm.isInitialized && !dynamicMutableRealm.isClosed()) {
            (dynamicMutableRealm as StandaloneDynamicMutableRealm).close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // TODO Add test for all BaseRealm methods

    @Test
    fun copyToRealm() {
        val obj = DynamicRealmObject("Sample")
        val dynamicMutableObject = dynamicMutableRealm.copyToRealm(obj)
        assertFalse { obj.isManaged() }
        assertTrue { dynamicMutableObject.isValid() }
        assertTrue { dynamicMutableObject.isManaged() }
    }


    // Main points of unmanaged objects:
    // - Unmanaged object gives ability to construct full graph outside of realm (1)
    // - Imports can be done on the fly, but creating the top level managed object is maybe one
    //   step to annoying (2)
    // - It would be possible to verify setting attributes but it requires access to the
    //   realmReference. Both options for this is a bit annoying
    //   - As argument (3) ... just have to pass the dynamicRealm in all the time
    //   - Using dynamicRealm.create() as factory (4) ... will maybe cause people to think that the
    //     objects are already managed
    //
    // - Requires people to think about managed/unmanaged, but
    // - Would align with construction import pattern of typed objects
    // - Drastically simplify embedded API as we can create unmanaged instances and ownership/parent
    //   will be implicit from the containing
    // - Would maybe need `EmbeddedDynamicX` classes to make it explictly that we can only
    //   copyToRealm non-embedded objects just as we are trying to do with RealmObjects
    //   ... but since there is no compile time linkage to whether a class is embedded or not,
    //   then it is maybe ok to leave this out ... combining embeddedness with mutability messes up the type system
    //
    // #1
    // DynamicRealmObject.create(
    //     "Sample",
    //     mapOf(
    //         "name" to "HELLO",
    //         // FIXME If we could accept a map here with `type` implicitly given from
    //         "child" to DynamicRealmObject.create("Sample")
    //     )
    // )

    // DynamicRealmObject.create("type") {
    //     "" to "Hello",
    //     "" to "Hello"
    // }

    // Or with DynamicRealmObject "extension constructor"
    // DynamicRealmObject(
    //     "Sample",
    //     mapOf(
    //         "name" to "HELLO",
    //         "child" to DynamicRealmObject("Sample")
    //     )
    // )
    // Alternatively
    // #2
    // val obj = dynamicMutableRealm.copyToRealm(DynamicMutableRealm.create("Sample"))
    // obj.apply {
    //     set("name", "Hello")
    //     val child = DynamicRealmObject.create("Sample")
    //     set("child", child)
    // }
    //
    // dynamicMutableRealm.copyToRealm(obj)
    //
    // With Validation
    // #3
    // DynamicMutableRealmObject(
    //     dynamicMutableObject,
    //     "Sample",
    //     mapOf(
    //         "name" to "HELLO",
    //         "child" to DynamicMutableRealmObject(dynamicMutableObject, "Sample")
    //     )
    // )
    // #4
    // dynamicMutableRealm.create(
    //     "Sample",
    //     mapOf(
    //         "name" to "HELLO",
    //         "child" to dynamicMutableObject.create("Sample")
    //     )
    // )

    // TODO Add variants for each type
    @Test
    fun copyToRealm_withPrimaryKey() {
        val dynamicMutableObject =
            dynamicMutableRealm.copyToRealm(DynamicRealmObject("PrimaryKeyString", "primaryKey" to "PRIMARY_KEY"))
        assertTrue { dynamicMutableObject.isValid() }
        assertEquals("PRIMARY_KEY", dynamicMutableObject.getValue("primaryKey"))
    }

    // TODO Add variants for each type
    @Test
    fun copyToRealm_withPrimaryKey_null() {
        val dynamicMutableObject = dynamicMutableRealm.copyToRealm(DynamicRealmObject("PrimaryKeyStringNullable", "primaryKey" to null))
        assertTrue { dynamicMutableObject.isValid() }
        assertNull(dynamicMutableObject.getNullableValue<String>("primaryKey"))
    }

    @Test
    fun copyToRealm_tree() {
        val child = DynamicRealmObject("Sample", "stringField" to "CHILD")
        val obj = DynamicRealmObject(
            "Sample",
            "stringField" to "PARENT",
            "nullableObject" to child,
            // FIXME List not supported yet
            // "stringListField" to listOf("1", "2", "3")
            // "objectListField" to listOf(child, child, child)
        )
        dynamicMutableRealm.copyToRealm(obj)

        val managedParent = dynamicMutableRealm.query("Sample", "stringField = 'PARENT'").find().single()
        val managedChildren = dynamicMutableRealm.query("Sample", "stringField = 'CHILD'").find().single()
    }

    // FIXME Missing tests
    //  - copyToRealm - updatePolicy_all
    //  - copyToRealm - already managed dynamic object
    //  - copyToRealm - typed object
    //  - copyToRealm - already managed typed object

    @Test
    fun copyToRealm_throwsOnUnknownClass() {
        val obj = DynamicRealmObject("UNKNOWN_CLASS")
        assertFailsWithMessage<IllegalArgumentException>("Schema does not contain a class named 'UNKNOWN_CLASS'") {
            dynamicMutableRealm.copyToRealm(obj)
        }
    }

    @Test
    fun copyToRealm_throwsOnUnknownProperty() {
        val obj = DynamicRealmObject("Sample", "UNKNOWN_PROPERTY" to "DONT_CARE")
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_PROPERTY'") {
            dynamicMutableRealm.copyToRealm(obj)
        }
    }

    @Test
    fun copyToRealm_throwsOnPropertyOfWrongType() {
        val obj = DynamicRealmObject("Sample", "stringField" to 42)
        assertFailsWithMessage<IllegalArgumentException>("Property `Sample.stringField` cannot be assigned with value '42' of wrong type") {
            dynamicMutableRealm.copyToRealm(obj)
        }
    }

    // NOT RELEVANT - Primary key is derived from schema metadata
    // @Test
    // fun create_throwsWithPrimaryKey() {
    //     assertFailsWithMessage<IllegalArgumentException>("Class does not have a primary key)") {
    //         dynamicMutableRealm.createObject("Sample", "PRIMARY_KEY")
    //     }
    // }

    @Test
    fun copyToRealm_throwsOnAbsentPrimaryKey() {
        val obj = DynamicRealmObject("PrimaryKeyString")
        // FIXME Should we allowing interpreting unset primary key property as null or should we throw?
        assertFailsWithMessage<IllegalArgumentException>("Property 'primaryKey' of class 'PrimaryKeyString' cannot be NULL") {
            dynamicMutableRealm.copyToRealm(obj)
        }
    }

    @Test
    fun copyToRealm_throwsWithWrongPrimaryKeyType() {
        val obj = DynamicRealmObject("PrimaryKeyString", mapOf("primaryKey" to 42))
        assertFailsWithMessage<IllegalArgumentException>("Wrong primary key type for 'PrimaryKeyString'") {
            dynamicMutableRealm.copyToRealm(obj)
        }
    }

    // @Test
    // fun createEmbedded_child() {
    //     val parent = dynamicMutableRealm.createObject("EmbeddedParent")
    //     dynamicMutableRealm.createEmbedded(parent, "child")
    //     dynamicMutableRealm.query("EmbeddedChild").find().single().also {
    //         assertEquals("EmbeddedChild", it.type)
    //     }
    // }
    //
    // @Test
    // fun createEmbedded_listElement() {
    //     val parent = dynamicMutableRealm.createObject("EmbeddedParent")
    //     dynamicMutableRealm.createEmbedded(parent, "childList")
    //     dynamicMutableRealm.query("EmbeddedChild").find().single().also {
    //         assertEquals("EmbeddedChild", it.type)
    //     }
    // }
    //

    @Test
    fun query_returnsDynamicMutableObject() {
        dynamicMutableRealm.copyToRealm(DynamicRealmObject("Sample"))
        val o1 = dynamicMutableRealm.query("Sample").find().first()
        o1.set("stringField", "value")
    }

    @Test
    fun query_failsOnUnknownClass() {
        assertFailsWithMessage<IllegalArgumentException>("Schema does not contain a class named 'UNKNOWN_CLASS'") {
            dynamicMutableRealm.query("UNKNOWN_CLASS")
        }
    }

    @Test
    fun findLatest() {
        val o1 = dynamicMutableRealm.copyToRealm(DynamicRealmObject("Sample"))
            .set("stringField" to "NEW_VALUE")

        val o2 = dynamicMutableRealm.findLatest(o1)
        assertNotNull(o2)
        assertEquals("NEW_VALUE", o2.getValue("stringField"))
    }

    @Test
    fun findLatest_deleted() {
        dynamicMutableRealm.run {
            val o1 = copyToRealm(DynamicRealmObject("Sample"))
            delete(o1)
            val o2 = findLatest(o1)
            assertNull(o2)
        }
    }

    @Test
    fun findLatest_identityForLiveObject() {
        val instance = dynamicMutableRealm.copyToRealm(DynamicRealmObject("Sample"))
        val latest = dynamicMutableRealm.findLatest(instance)
        assert(instance === latest)
    }

    @Test
    fun findLatest_unmanagedThrows() {
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableRealm.findLatest(DynamicRealmObject("Sample"))
        }
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableRealm.findLatest(Sample())
        }
    }

    @Test
    fun delete_realmObject() {
        dynamicMutableRealm.run {
            val liveObject = copyToRealm(DynamicRealmObject("Sample"))
            assertEquals(1, query("Sample").count().find())
            delete(liveObject)
            assertEquals(0, query("Sample").count().find())
        }
    }

    @Test
    fun delete_realmList() {
        dynamicMutableRealm.run {
            val liveObject = copyToRealm(DynamicRealmObject("Sample")).apply {
                set("stringField", "PARENT")
                getObjectList("objectListField").run {
                    add(DynamicRealmObject("Sample"))
                    add(DynamicRealmObject("Sample"))
                    add(DynamicRealmObject("Sample"))
                }
                getValueList<String>("stringListField").run {
                    add("ELEMENT1")
                    add("ELEMENT2")
                }
            }

            assertEquals(4, query("Sample").count().find())
            liveObject.getObjectList("objectListField").run {
                assertEquals(3, size)
                delete(this)
                assertEquals(0, size)
            }
            liveObject.getValueList<String>("stringListField").run {
                assertEquals(2, size)
                delete(this)
                assertEquals(0, size)
            }
            assertEquals(1, query("Sample").count().find())
        }
    }

    @Test
    fun delete_realmQuery() {
        dynamicMutableRealm.run {
            for (i in 0..9) {
                copyToRealm(DynamicRealmObject("Sample")).set("intField", i % 2)
            }
            assertEquals(10, query("Sample").count().find())
            val deleteable: RealmQuery<DynamicMutableRealmObject> = query("Sample", "intField = 1")
            delete(deleteable)
            val samples: RealmResults<DynamicMutableRealmObject> = query("Sample").find()
            assertEquals(5, samples.size)
            for (sample in samples) {
                assertEquals(0, sample.getValue<Long>("intField"))
            }
        }
    }

    @Test
    fun delete_realmSingleQuery() {
        dynamicMutableRealm.run {
            for (i in 0..3) {
                copyToRealm(DynamicRealmObject("Sample")).set("intField", i)
            }
            assertEquals(4, query("Sample").count().find())
            val deleteable: RealmSingleQuery<DynamicMutableRealmObject> = query("Sample", "intField = 1").first()
            delete(deleteable)
            val samples: RealmResults<DynamicMutableRealmObject> = query("Sample").find()
            assertEquals(3, samples.size)
            for (sample in samples) {
                assertNotEquals(1, sample.getValue<Long>("intField"))
            }
        }
    }

    @Test
    fun delete_realmResults() {
        dynamicMutableRealm.run {
            for (i in 0..9) {
                copyToRealm(DynamicRealmObject("Sample")).set("intField", i % 2)
            }
            assertEquals(10, query("Sample").count().find())
            val deleteable: RealmResults<DynamicMutableRealmObject> = query("Sample", "intField = 1").find()
            delete(deleteable)
            val samples: RealmResults<DynamicMutableRealmObject> = query("Sample").find()
            assertEquals(5, samples.size)
            for (sample in samples) {
                assertEquals(0, sample.getValue<Long>("intField"))
            }
        }
    }

    @Test
    fun delete_deletedObjectThrows() {
        dynamicMutableRealm.run {
            val liveObject = copyToRealm(DynamicRealmObject("Sample"))
            assertEquals(1, query("Sample").count().find())
            delete(liveObject)
            assertEquals(0, query("Sample").count().find())
            assertFailsWith<IllegalArgumentException> {
                delete(liveObject)
            }
        }
    }

    @Test
    fun delete_unmanagedObjectsThrows() {
        dynamicMutableRealm.run {
            assertFailsWith<IllegalArgumentException> {
                delete(Sample())
            }
        }
    }
}
