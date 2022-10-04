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

package io.realm.kotlin.test.shared.dynamic

import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.dynamic.DynamicMutableRealm
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.getNullableValue
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.dynamic.getValueList
import io.realm.kotlin.dynamic.getValueSet
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.SampleWithPrimaryKey
import io.realm.kotlin.entities.embedded.embeddedSchema
import io.realm.kotlin.entities.embedded.embeddedSchemaWithPrimaryKey
import io.realm.kotlin.entities.primarykey.PrimaryKeyString
import io.realm.kotlin.entities.primarykey.PrimaryKeyStringNullable
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.isValid
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.internal.InternalConfiguration
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.test.StandaloneDynamicMutableRealm
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
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
                    SampleWithPrimaryKey::class,
                    PrimaryKeyStringNullable::class
                ) + embeddedSchema + embeddedSchemaWithPrimaryKey
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
        val obj = DynamicMutableRealmObject.create("Sample")
        val dynamicMutableObject = dynamicMutableRealm.copyToRealm(obj)
        assertFalse { obj.isManaged() }
        assertTrue { dynamicMutableObject.isValid() }
        assertTrue { dynamicMutableObject.isManaged() }
    }

    // TODO Add variants for each type
    @Test
    fun copyToRealm_withPrimaryKey() {
        val dynamicMutableObject =
            dynamicMutableRealm.copyToRealm(
                DynamicMutableRealmObject.create(
                    "PrimaryKeyString",
                    "primaryKey" to "PRIMARY_KEY"
                )
            )
        assertTrue { dynamicMutableObject.isValid() }
        assertEquals("PRIMARY_KEY", dynamicMutableObject.getValue("primaryKey"))
    }

    // TODO Add variants for each type
    @Test
    fun copyToRealm_withPrimaryKey_null() {
        val dynamicMutableObject = dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                "PrimaryKeyStringNullable",
                "primaryKey" to null
            )
        )
        assertTrue { dynamicMutableObject.isValid() }
        assertNull(dynamicMutableObject.getNullableValue<String>("primaryKey"))
    }

    @Test
    fun copyToRealm_tree_mixedRealmAndEmbeddedRealmObject() {
        val child = DynamicMutableRealmObject.create(
            "EmbeddedChild",
            "id" to "CHILD",
            "subTree" to DynamicMutableRealmObject.create(
                "EmbeddedParent",
                "id" to "SUBTREE_PARENT",
                "child" to DynamicMutableRealmObject.create(
                    "EmbeddedChild",
                    "id" to "SUBTREE_CHILD",
                    "innerChild" to DynamicMutableRealmObject.create("EmbeddedChild", "id" to "SUBTREE_INNER_CHILD")
                )
            ),
            "innerChild" to DynamicMutableRealmObject.create("EmbeddedInnerChild", "id" to "INNER")

        )
        dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                "EmbeddedParent",
                "id" to "PARENT",
                "child" to child,
                "children" to realmListOf(child, child)
            )
        )

        dynamicMutableRealm.query("EmbeddedParent", "id = 'PARENT'").find().single().let { parent ->
            parent.getObject("child").let { child ->
                assertEquals("CHILD", child!!.getNullableValue("id"))
                child.getObject("innerChild").let { innerChild ->
                    assertEquals("INNER", innerChild!!.getNullableValue("id"))
                }
                child.getObject("subTree")!!.run {
                    assertEquals("SUBTREE_PARENT", getNullableValue("id"))
                }
            }
            parent.getObjectList("children").forEach { child ->
                assertEquals("CHILD", child.getNullableValue("id"))
                child.getObject("innerChild").let { innerChild ->
                    assertEquals("INNER", innerChild!!.getNullableValue("id"))
                }
            }
        }
        dynamicMutableRealm.query("EmbeddedParent", "id = 'SUBTREE_PARENT'").find().single().run {
            assertEquals("SUBTREE_PARENT", getNullableValue("id"))
            getObject("child").let { child ->
                assertEquals("SUBTREE_CHILD", child!!.getNullableValue("id"))
            }
        }
        dynamicMutableRealm.query("EmbeddedChild", "id = 'CHILD'").find().run {
            assertEquals(3, size)
        }
        dynamicMutableRealm.query("EmbeddedChild", "id = 'SUBTREE_CHILD'").find().run {
            assertEquals(1, size)
        }
        dynamicMutableRealm.query("EmbeddedInnerChild", "id = 'INNER'").find().run {
            assertEquals(3, size)
        }
        dynamicMutableRealm.query("EmbeddedInnerChild", "id = 'SUBTREE_INNER_CHILD'").find().run {
            assertEquals(1, size)
        }
    }

    @Test
    fun copyToRealm_withManagedDynamicObject() {
        val child = dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                "Sample",
                "stringField" to "CHILD",
            )
        )
        dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                "Sample",
                "stringField" to "PARENT",
                "nullableObject" to child
            )
        )
        dynamicMutableRealm.query("Sample", "stringField = 'PARENT'").find().single().run {
            getObject("nullableObject")!!.run {
                assertEquals("CHILD", getValue("stringField"))
            }
        }
    }

    @Test
    fun copyToRealm_withUnmanagedTypedObject() {
        val child = Sample().apply { stringField = "CHILD" }
        dynamicMutableRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                "Sample",
                "stringField" to "PARENT",
                "nullableObject" to child
            )
        )
        dynamicMutableRealm.query("Sample", "stringField = 'PARENT'").find().single().run {
            getObject("nullableObject")!!.run {
                assertEquals("CHILD", getValue("stringField"))
            }
        }
    }

    @Test
    fun copyToRealm_updatePolicy_all() {
        val child = DynamicMutableRealmObject.create(
            "SampleWithPrimaryKey",
            "primaryKey" to 1L,
            "stringField" to "INITIAL_VALUE",
        )
        val parent = DynamicMutableRealmObject.create(
            "SampleWithPrimaryKey",
            "primaryKey" to 2L,
            "stringField" to "INITIAL_VALUE",
            "nullableObject" to child
        )
        dynamicMutableRealm.copyToRealm(parent)

        parent.set("stringField", "UPDATED_VALUE")
        child.set("stringField", "UPDATED_VALUE")

        dynamicMutableRealm.copyToRealm(parent, UpdatePolicy.ALL)

        dynamicMutableRealm.query("SampleWithPrimaryKey").find().run {
            assertEquals(2, size)
            forEach { assertEquals("UPDATED_VALUE", it.getValue("stringField")) }
        }
    }

    @Test
    fun copyToRealm_updatePolicy_error_throwsOnDuplicatePrimaryKey() {
        val child = DynamicMutableRealmObject.create(
            "SampleWithPrimaryKey",
            "primaryKey" to 1L,
            "stringField" to "INITIAL_VALUE",
        )
        val parent = DynamicMutableRealmObject.create(
            "SampleWithPrimaryKey",
            "primaryKey" to 1L,
            "stringField" to "INITIAL_VALUE",
            "nullableObject" to child
        )
        assertFailsWithMessage<IllegalArgumentException>("Object with this primary key already exists") {
            dynamicMutableRealm.copyToRealm(parent)
        }
        dynamicMutableRealm.query("SampleWithPrimaryKey").find().none()
    }

    @Test
    fun copyToRealm_throwsOnUnknownClass() {
        val obj = DynamicMutableRealmObject.create("UNKNOWN_CLASS")
        assertFailsWithMessage<IllegalArgumentException>("Schema does not contain a class named 'UNKNOWN_CLASS'") {
            dynamicMutableRealm.copyToRealm(obj)
        }
    }

    @Test
    fun copyToRealm_throwsOnUnknownProperty() {
        val obj = DynamicMutableRealmObject.create("Sample", "UNKNOWN_PROPERTY" to "DONT_CARE")
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_PROPERTY'") {
            dynamicMutableRealm.copyToRealm(obj)
        }
    }

    @Test
    fun copyToRealm_throwsOnPropertyOfWrongType() {
        val obj = DynamicMutableRealmObject.create("Sample", "stringField" to 42)
        assertFailsWithMessage<IllegalArgumentException>("Property 'Sample.stringField' of type 'class kotlin.String' cannot be assigned with value '42' of type 'class kotlin.Int'") {
            dynamicMutableRealm.copyToRealm(obj)
        }
    }

    @Test
    fun copyToRealm_throwsOnAbsentPrimaryKey() {
        val obj = DynamicMutableRealmObject.create("PrimaryKeyString")
        assertFailsWithMessage<IllegalArgumentException>("Cannot create object of type 'PrimaryKeyString' without primary key property 'primaryKey'") {
            dynamicMutableRealm.copyToRealm(obj)
        }
    }

    @Test
    fun copyToRealm_throwsOnNullPrimaryKey() {
        val obj = DynamicMutableRealmObject.create("PrimaryKeyString", "primaryKey" to null)
        assertFailsWithMessage<IllegalArgumentException>("Property 'primaryKey' of class 'PrimaryKeyString' cannot be NULL") {
            dynamicMutableRealm.copyToRealm(obj)
        }
    }

    @Test
    fun copyToRealm_throwsWithWrongPrimaryKeyType() {
        val obj = DynamicMutableRealmObject.create("PrimaryKeyString", mapOf("primaryKey" to 42))
        assertFailsWithMessage<IllegalArgumentException>("Wrong primary key type for 'PrimaryKeyString'") {
            dynamicMutableRealm.copyToRealm(obj)
        }
    }

    @Test
    fun copyToRealm_throwsOnTopLevelEmbeddedRealmObject() {
        val obj = DynamicMutableRealmObject.create("EmbeddedChild")
        assertFailsWithMessage<IllegalArgumentException>("Cannot create embedded object without a parent") {
            dynamicMutableRealm.copyToRealm(obj)
        }
    }

    @Test
    fun copyToRealm_embeddedRealmObject() {
        val obj = DynamicMutableRealmObject.create(
            "EmbeddedParent",
            "child" to DynamicMutableRealmObject.create("EmbeddedChild")
        )
        dynamicMutableRealm.copyToRealm(obj)
        dynamicMutableRealm.query("EmbeddedChild").find().single().also {
            assertEquals("EmbeddedChild", it.type)
        }
    }

    @Test
    fun copyToRealm_embeddedRealmObjectList() {
        val obj = DynamicMutableRealmObject.create(
            "EmbeddedParent",
            "children" to realmListOf(
                DynamicMutableRealmObject.create(
                    "EmbeddedChild",
                    "id" to "child1"
                ),
                DynamicMutableRealmObject.create("EmbeddedChild", "id" to "child2")
            )
        )
        dynamicMutableRealm.copyToRealm(obj)
        dynamicMutableRealm.query("EmbeddedParent").find().single().run {
            getObjectList("children").run {
                assertEquals(2, size)
                assertEquals("child1", get(0).getNullableValue("id"))
                assertEquals("child2", get(1).getNullableValue("id"))
            }
        }
        dynamicMutableRealm.query("EmbeddedChild").find().run {
            assertEquals(2, size)
        }
    }

    @Test
    @Suppress("ComplexMethod")
    fun copyToRealm_embeddedTree_updatePolicy_replacesEmbeddedRealmObject() {
        val innerChild = DynamicMutableRealmObject.create("EmbeddedInnerChild", "id" to "INNER")
        val child = DynamicMutableRealmObject.create(
            "EmbeddedChildWithPrimaryKeyParent",
            "id" to "CHILD",
            "innerChild" to innerChild
        )
        val parent = DynamicMutableRealmObject.create(
            "EmbeddedParentWithPrimaryKey",
            "id" to 1L,
            "child" to child,
            "children" to realmListOf(child, child)
        )
        dynamicMutableRealm.copyToRealm(parent)

        dynamicMutableRealm.query("EmbeddedParentWithPrimaryKey").find().single().let { parent ->
            parent.getObject("child").let { child ->
                assertEquals("CHILD", child!!.getNullableValue("id"))
                child.getObject("innerChild").let { innerChild ->
                    assertEquals("INNER", innerChild!!.getNullableValue("id"))
                }
            }
            parent.getObjectList("children").forEach { child ->
                assertEquals("CHILD", child.getNullableValue("id"))
                child.getObject("innerChild").let { innerChild ->
                    assertEquals("INNER", innerChild!!.getNullableValue("id"))
                }
            }
        }
        dynamicMutableRealm.query("EmbeddedChildWithPrimaryKeyParent").find().run {
            assertEquals(3, size)
        }
        dynamicMutableRealm.query("EmbeddedInnerChild").find().run {
            assertEquals(3, size)
        }

        child.set("id", "UPDATED")
        innerChild.set("id", "UPDATED")

        dynamicMutableRealm.copyToRealm(parent, updatePolicy = UpdatePolicy.ALL)

        dynamicMutableRealm.query("EmbeddedParentWithPrimaryKey").find().single().let { parent ->
            parent.getObject("child").let { child ->
                assertEquals("UPDATED", child!!.getNullableValue("id"))
                child.getObject("innerChild").let { innerChild ->
                    assertEquals("UPDATED", innerChild!!.getNullableValue("id"))
                }
            }
            parent.getObjectList("children").forEach { child ->
                assertEquals("UPDATED", child.getNullableValue("id"))
                child.getObject("innerChild").let { innerChild ->
                    assertEquals("UPDATED", innerChild!!.getNullableValue("id"))
                }
            }
        }
        dynamicMutableRealm.query("EmbeddedChildWithPrimaryKeyParent").find().run {
            assertEquals(3, size)
        }
        dynamicMutableRealm.query("EmbeddedInnerChild").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun query_returnsDynamicMutableObject() {
        dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
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
        val o1 = dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
            .set("stringField" to "NEW_VALUE")

        val o2 = dynamicMutableRealm.findLatest(o1)
        assertNotNull(o2)
        assertEquals("NEW_VALUE", o2.getValue("stringField"))
    }

    @Test
    fun findLatest_deleted() {
        dynamicMutableRealm.run {
            val o1 = copyToRealm(DynamicMutableRealmObject.create("Sample"))
            delete(o1)
            val o2 = findLatest(o1)
            assertNull(o2)
        }
    }

    @Test
    fun findLatest_identityForLiveObject() {
        val instance =
            dynamicMutableRealm.copyToRealm(DynamicMutableRealmObject.create("Sample"))
        val latest = dynamicMutableRealm.findLatest(instance)
        assert(instance === latest)
    }

    @Test
    fun findLatest_unmanagedThrows() {
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableRealm.findLatest(DynamicMutableRealmObject.create("Sample"))
        }
    }

    @Test
    fun delete_realmObject() {
        dynamicMutableRealm.run {
            val liveObject = copyToRealm(DynamicMutableRealmObject.create("Sample"))
            assertEquals(1, query("Sample").count().find())
            delete(liveObject)
            assertEquals(0, query("Sample").count().find())
        }
    }

    @Test
    fun delete_cascadedToEmbeddedRealmObject() {
        val obj = DynamicMutableRealmObject.create(
            "EmbeddedParent",
            "child" to DynamicMutableRealmObject.create("EmbeddedChild")
        )
        val managedObject = dynamicMutableRealm.copyToRealm(obj)
        dynamicMutableRealm.query("EmbeddedChild").find().single().also {
            assertEquals("EmbeddedChild", it.type)
        }
        dynamicMutableRealm.delete(managedObject)
        dynamicMutableRealm.query("EmbeddedChild").find().none()
    }

    @Test
    fun delete_realmList() {
        dynamicMutableRealm.run {
            val liveObject = copyToRealm(DynamicMutableRealmObject.create("Sample")).apply {
                set("stringField", "PARENT")
                getObjectList("objectListField").run {
                    add(DynamicMutableRealmObject.create("Sample"))
                    add(DynamicMutableRealmObject.create("Sample"))
                    add(DynamicMutableRealmObject.create("Sample"))
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
    fun delete_realmSet() {
        dynamicMutableRealm.run {
            val liveObject = copyToRealm(DynamicMutableRealmObject.create("Sample")).apply {
                set("stringField", "PARENT")
                getObjectSet("objectSetField").run {
                    add(DynamicMutableRealmObject.create("Sample"))
                    add(DynamicMutableRealmObject.create("Sample"))
                    add(DynamicMutableRealmObject.create("Sample"))
                }
                getValueSet<String>("stringSetField").run {
                    add("ELEMENT1")
                    add("ELEMENT2")
                }
            }

            assertEquals(4, query("Sample").count().find())
            liveObject.getObjectSet("objectSetField").run {
                assertEquals(3, size)
                delete(this)
                assertEquals(0, size)
            }
            liveObject.getValueSet<String>("stringSetField").run {
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
                copyToRealm(DynamicMutableRealmObject.create("Sample")).set("intField", i % 2L)
            }
            assertEquals(10, query("Sample").count().find())
            val deleteable: RealmQuery<DynamicMutableRealmObject> =
                query("Sample", "intField = 1")
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
                copyToRealm(DynamicMutableRealmObject.create("Sample")).set(
                    "intField",
                    i.toLong()
                )
            }
            assertEquals(4, query("Sample").count().find())
            val deleteable: RealmSingleQuery<DynamicMutableRealmObject> =
                query("Sample", "intField = 1").first()
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
                copyToRealm(DynamicMutableRealmObject.create("Sample")).set("intField", i % 2L)
            }
            assertEquals(10, query("Sample").count().find())
            val deleteable: RealmResults<DynamicMutableRealmObject> =
                query("Sample", "intField = 1").find()
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
            val liveObject = copyToRealm(DynamicMutableRealmObject.create("Sample"))
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

    @Test
    fun deleteAll() {
        dynamicMutableRealm.run {
            for (i in 0..9) {
                copyToRealm(DynamicMutableRealmObject.create("Sample"))
                copyToRealm(DynamicMutableRealmObject.create("PrimaryKeyString").set("primaryKey", i.toString()))
                copyToRealm(DynamicMutableRealmObject.create("PrimaryKeyStringNullable").set("primaryKey", i.toString()))
                copyToRealm(DynamicMutableRealmObject.create("SampleWithPrimaryKey").set("primaryKey", i.toLong()))
            }
            assertEquals(10, query("Sample").count().find())
            assertEquals(10, query("PrimaryKeyString").count().find())
            assertEquals(10, query("PrimaryKeyStringNullable").count().find())
            assertEquals(10, query("SampleWithPrimaryKey").count().find())
            deleteAll()
            assertEquals(0, query("Sample").count().find())
            assertEquals(0, query("PrimaryKeyString").count().find())
            assertEquals(0, query("PrimaryKeyStringNullable").count().find())
            assertEquals(0, query("SampleWithPrimaryKey").count().find())
        }
    }
}
