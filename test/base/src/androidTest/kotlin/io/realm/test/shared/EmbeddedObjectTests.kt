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

import io.realm.MutableRealm
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.dynamic.DynamicMutableRealm
import io.realm.entities.embedded.EmbeddedChild
import io.realm.entities.embedded.EmbeddedInnerChild
import io.realm.entities.embedded.EmbeddedParent
import io.realm.isValid
import io.realm.query
import io.realm.realmListOf
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmbeddedObjectTests {
    // copyToRealm throws on top level embedded
    // throws on multiple parents

    lateinit var tmpDir: String
    lateinit var realm: Realm
    private lateinit var dynamicMutableRealm: DynamicMutableRealm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(schema = setOf(EmbeddedParent::class, EmbeddedChild::class, EmbeddedInnerChild::class))
                .directory(tmpDir)
                .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Suppress("ComplexMethod")
    @Test
    fun copyToRealm() {
        val parent = EmbeddedParent().apply {
            child = EmbeddedChild().apply { id = "Imported child" }
            childList = realmListOf(EmbeddedChild().apply { id = "Imported list child 1" })
        }
        realm.writeBlocking {
            copyToRealm(parent)
        }
        // FIXME Requires updates on all values on import
        assertEquals(2, realm.query<EmbeddedChild>().find().size)
        val roundTripParent1 = realm.query<EmbeddedParent>().find().single()
        assertEquals("Imported child", roundTripParent1.child!!.id)
        assertEquals("Imported list child 1", roundTripParent1.childList[0]!!.id)

        val roundTripParent2 = realm.writeBlocking {
            findLatest(roundTripParent1)!!.apply {
                child = EmbeddedChild().apply { id = "Assigned child" }
                childList = realmListOf(
                    EmbeddedChild().apply { id = "Assigned list child 1" },
                    EmbeddedChild().apply { id = "Assigned list child 2" },
                )
            }
        }
        realm.query<EmbeddedChild>().find().run {
            assertEquals(3, count())
        }

        val roundTripParent3 = realm.writeBlocking {
            findLatest(roundTripParent2)!!.apply {
                childList.add(1, EmbeddedChild().apply { id = "Inserted list child" })
            }
        }
        realm.query<EmbeddedChild>().find().run {
            assertEquals(4, count())
        }
        assertEquals("Inserted list child", roundTripParent3.childList[1].id)

        val roundTripParent4 = realm.writeBlocking {
            findLatest(roundTripParent3)!!.apply {
                childList.set(1, EmbeddedChild().apply { id = "Updated list child" }).run {
                    // embeddedList.set returns newly created element instead of old one as the old
                    // one is deleted when overwritten and we cannot return null on non-nullable List<E>.set()
                    // assertEquals("Updated list child", name)
                }
            }
        }
        realm.query<EmbeddedChild>().find().run {
            assertEquals(4, count())
        }
        assertEquals("Updated list child", roundTripParent4.childList[1].id)

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

    @Test
    fun removeWithParent() { }

    @Test
    fun dynamic_create() {}
    @Test
    fun dynamic_createList() {}
    @Test
    fun dynamic_removedWithParent() {}
    @Test
    fun dynamic_wrongParentPropertyType() {}

    @Test
    fun dynamic_removeByOverride() { }
    @Test
    fun dynamic_removeByListOverride() {}


    // NOT RELEVANT - Impossible through type system
    // @org.junit.Test
    // fun createObject_throwsForEmbeddedClasses() = realm.executeTransaction { realm ->
    //     assertFailsWith<IllegalArgumentException> { realm.createObject<EmbeddedSimpleChild>() }
    // }
    //

    // NOT RELEVANT - Impossible through type system
    // @org.junit.Test
    // fun createObjectWithPrimaryKey_throwsForEmbeddedClasses() = realm.executeTransaction { realm ->
    //     assertFailsWith<IllegalArgumentException> { realm.createObject<EmbeddedSimpleChild>("foo") }
    // }
    //

    // NOT RELEVANT - Not possible through copyToRealm API
    // @org.junit.Test
    // fun createEmbeddedObject_nullArgsThrows() = realm.executeTransaction { realm ->
    //     assertFailsWith<IllegalArgumentException> { realm.createEmbeddedObject(EmbeddedSimpleChild::class.java, TestHelper.getNull(), "foo") }
    //     val parent = realm.createObject<EmbeddedSimpleParent>("parent")
    //     assertFailsWith<IllegalArgumentException> { realm.createEmbeddedObject(EmbeddedSimpleChild::class.java, parent, TestHelper.getNull()) }
    // }
    //

    // NOT RELEVANT - Not possible through copyToRealm API
    // @org.junit.Test
    // fun createEmbeddedObject_nonExistingParentPropertyNameThrows() = realm.executeTransaction { realm ->
    //     val parent = realm.createObject<EmbeddedSimpleParent>("parent")
    //     assertFailsWith<IllegalArgumentException> { realm.createEmbeddedObject<EmbeddedSimpleChild>(parent, "foo") }
    // }
    //

    // NOT RELEVANT - Not possible through copyToRealm API
    // @org.junit.Test
    // fun createEmbeddedObject_wrongParentPropertyTypeThrows() = realm.executeTransaction { realm ->
    //     val parent = realm.createObject<EmbeddedSimpleParent>("parent")
    //
    //     // TODO: Smoke-test for wrong type. Figure out how to test all unsupported types.
    //     assertFailsWith<IllegalArgumentException> { realm.createEmbeddedObject<EmbeddedSimpleChild>(parent, "childId") }
    // }
    //

    // NOT RELEVANT - Not possible through copyToRealm API
    // @org.junit.Test
    // fun createEmbeddedObject_wrongParentPropertyObjectTypeThrows() = realm.executeTransaction { realm ->
    //     val parent = realm.createObject<EmbeddedSimpleParent>("parent")
    //
    //     assertFailsWith<IllegalArgumentException> {
    //         // Embedded object is not of the type the parent object links to.
    //         realm.createEmbeddedObject<EmbeddedTreeLeaf>(parent, "child")
    //     }
    // }
    //

    // NOT RELEVANT - Not possible through copyToRealm API
    // @org.junit.Test
    // fun createEmbeddedObject_wrongParentPropertyListTypeThrows() = realm.executeTransaction { realm ->
    //     val parent = realm.createObject<EmbeddedSimpleListParent>("parent")
    //
    //     assertFailsWith<IllegalArgumentException> {
    //         // Embedded object is not of the type the parent object links to.
    //         realm.createEmbeddedObject<EmbeddedTreeLeaf>(parent, "children")
    //     }
    // }
    //

    // NOT RELEVANT - Only option is copyToRealm API so not relevant
    // @org.junit.Test
    // fun createEmbeddedObject_simpleSingleChild() = realm.executeTransaction { realm ->
    //     val parent = realm.createObject<EmbeddedSimpleParent>("parent")
    //     val child = realm.createEmbeddedObject<EmbeddedSimpleChild>(parent, "child")
    //     assertEquals(child.parent, parent)
    // }
    //

    // NOT RELEVANT - Only option is copyToRealm API so not relevant
    // @org.junit.Test
    // fun createEmbeddedObject_simpleChildList() = realm.executeTransaction { realm ->
    //     // Using createEmbeddedObject() with a parent list, will append the object to the end
    //     // of the list
    //     val parent = realm.createObject<EmbeddedSimpleListParent>(UUID.randomUUID().toString())
    //     val child1 = realm.createEmbeddedObject<EmbeddedSimpleChild>(parent, "children")
    //     val child2 = realm.createEmbeddedObject<EmbeddedSimpleChild>(parent, "children")
    //     Assert.assertEquals(2, parent.children.size.toLong())
    //     assertEquals(child1, parent.children.first()!!)
    //     assertEquals(child2, parent.children.last()!!)
    // }
    //

    // DYNAMIC API CREATE?
    // @org.junit.Test
    // fun dynamicRealm_createEmbeddedObject() =
    //     DynamicRealm.getInstance(realm.configuration).use { realm ->
    //         realm.executeTransaction {
    //             val parent = realm.createObject("EmbeddedSimpleParent", "PK_VALUE")
    //             val child = realm.createEmbeddedObject("EmbeddedSimpleChild", parent, "child")
    //
    //             val idValue = "ID_VALUE"
    //             child.setString("childId", idValue)
    //
    //             val childInParent = parent.getObject("child")
    //             Assert.assertNotNull(childInParent)
    //             Assert.assertEquals(childInParent!!.getString("childId"), idValue)
    //             assertEquals(child, childInParent)
    //
    //             val linkingParent = child.linkingObjects("EmbeddedSimpleParent", "child").first()
    //             Assert.assertNotNull(linkingParent)
    //             assertEquals(parent.getString("_id"), linkingParent!!.getString("_id"))
    //             assertEquals(parent.getObject("child"), linkingParent.getObject("child"))
    //         }
    //     }
    //

    // DYNAMIC API CREATE?
    // @org.junit.Test
    // fun dynamicRealm_createEmbeddedObject_simpleChildList() =
    //     DynamicRealm.getInstance(realm.configuration).use { realm ->
    //         realm.executeTransaction {
    //             val parent = realm.createObject("EmbeddedSimpleListParent", UUID.randomUUID().toString())
    //             val child1 = realm.createEmbeddedObject("EmbeddedSimpleChild", parent, "children")
    //             val child2 = realm.createEmbeddedObject("EmbeddedSimpleChild", parent, "children")
    //             Assert.assertEquals(2, parent.getList("children").size.toLong())
    //             assertEquals(child1, parent.getList("children").first()!!)
    //             assertEquals(child2, parent.getList("children").last()!!)
    //         }
    //     }
    //

    // NOT RELEVANT - Not possible through copyToRealm API
    // @org.junit.Test
    // fun dynamicRealm_createEmbeddedObject_wrongParentPropertyTypeThrows() {
    //     DynamicRealm.getInstance(realm.configuration).use { realm ->
    //         realm.executeTransaction {
    //             val parent = realm.createObject("EmbeddedSimpleParent", "parent")
    //             assertFailsWith<IllegalArgumentException> { realm.createEmbeddedObject("EmbeddedSimpleChild", parent, "_id") }
    //         }
    //     }
    // }
    //

    // NOT RELEVANT - Not possible through copyToRealm API
    // @org.junit.Test
    // fun dynamicRealm_createEmbeddedObject_wrongParentPropertyObjectTypeThrows() =
    //     DynamicRealm.getInstance(realm.configuration).use { realm ->
    //         realm.executeTransaction {
    //             val parent = realm.createObject("EmbeddedSimpleParent", "parent")
    //
    //             assertFailsWith<IllegalArgumentException> {
    //                 // Embedded object is not of the type the parent object links to.
    //                 realm.createEmbeddedObject("EmbeddedTreeLeaf", parent, "child")
    //             }
    //         }
    //     }
    //

    // NOT RELEVANT - Not possible through copyToRealm API
    // @org.junit.Test
    // fun dynamicRealm_createEmbeddedObject_wrongParentPropertyListTypeThrows() =
    //     DynamicRealm.getInstance(realm.configuration).use { realm ->
    //         realm.executeTransaction {
    //             val parent = realm.createObject("EmbeddedSimpleListParent", "parent")
    //
    //             assertFailsWith<IllegalArgumentException> {
    //                 // Embedded object is not of the type the parent object links to.
    //                 realm.createEmbeddedObject("EmbeddedTreeLeaf", parent, "children")
    //             }
    //         }
    //     }
    //

    // @org.junit.Test
    // fun settingParentFieldDeletesChild() = realm.executeTransaction { realm ->
    //     val parent = EmbeddedSimpleParent("parent")
    //     parent.child = EmbeddedSimpleChild("child")
    //
    //     val managedParent: EmbeddedSimpleParent = realm.copyToRealm(parent)
    //     val managedChild: EmbeddedSimpleChild = managedParent.child!!
    //     managedParent.child = null // Will delete the embedded object
    //     Assert.assertFalse(managedChild.isValid)
    //     Assert.assertEquals(0, realm.where<EmbeddedSimpleChild>().count())
    // }
    //

    // @org.junit.Test
    // fun dynamicRealm_settingParentFieldDeletesChild() =
    //     DynamicRealm.getInstance(realm.configuration).use { realm ->
    //         realm.executeTransaction {
    //             val parent = realm.createObject("EmbeddedSimpleParent", "parent")
    //             val child = realm.createEmbeddedObject("EmbeddedSimpleChild", parent, "child")
    //
    //             Assert.assertEquals(1, realm.where("EmbeddedSimpleChild").count())
    //             parent.setObject("child", null)
    //             Assert.assertFalse(child.isValid)
    //             Assert.assertEquals(0, realm.where("EmbeddedSimpleChild").count())
    //         }
    //     }
    //

    // @org.junit.Test
    // fun objectAccessor_willAutomaticallyCopyUnmanaged() = realm.executeTransaction { realm ->
    //     // Checks that adding an unmanaged embedded object to a property will automatically copy it.
    //     val parent = EmbeddedSimpleParent("parent")
    //     val managedParent: EmbeddedSimpleParent = realm.copyToRealm(parent)
    //
    //     Assert.assertEquals(0, realm.where<EmbeddedSimpleChild>().count())
    //     managedParent.child = EmbeddedSimpleChild("child") // Will copy the object to Realm
    //     Assert.assertEquals(1, realm.where<EmbeddedSimpleChild>().count())
    //     Assert.assertTrue(managedParent.child!!.isValid)
    // }
    //

    // @org.junit.Test
    // fun objectAccessor_willAutomaticallyCopyManaged() = realm.executeTransaction { realm ->
    //     // Checks that setting a link to a managed embedded object will automatically copy it unlike
    //     // normal objects that allow multiple parents. Note: This behavior is a bit controversial
    //     // and was subject to a lot of discussion during API design. The problem is that making
    //     // the behavior explicit will result in an extremely annoying API. We need to carefully
    //     // monitor if people understand how this behaves.
    //     val managedParent1: EmbeddedSimpleParent = realm.copyToRealm(EmbeddedSimpleParent("parent1"))
    //     val managedParent2: EmbeddedSimpleParent = realm.copyToRealm(EmbeddedSimpleParent("parent2"))
    //
    //     Assert.assertEquals(0, realm.where<EmbeddedSimpleChild>().count())
    //     managedParent1.child = EmbeddedSimpleChild("child")
    //     Assert.assertEquals(1, realm.where<EmbeddedSimpleChild>().count())
    //     managedParent2.child = managedParent1.child // Will copy the embedded object
    //     Assert.assertEquals(2, realm.where<EmbeddedSimpleChild>().count())
    //     Assert.assertNotEquals(managedParent1.child, managedParent2.child)
    // }
    //

    // @org.junit.Test
    // fun objectAccessor_willCopyUnderConstruction() = realm.executeTransaction { realm ->
    //     val unmanagedObj = EmbeddedWithConstructorArgs()
    //     val managedObj = realm.copyToRealm(unmanagedObj)
    //     assertEquals(EmbeddedWithConstructorArgs.INNER_CHILD_ID, managedObj.child!!.childId)
    // }
    //

    // @org.junit.Test
    // fun realmList_add_willAutomaticallyCopy() = realm.executeTransaction { realm ->
    //     val parent = realm.copyToRealm(EmbeddedSimpleListParent("parent"))
    //     Assert.assertTrue(parent.children.add(EmbeddedSimpleChild("child")))
    //     val child = parent.children.first()!!
    //     Assert.assertTrue(child.isValid)
    //     Assert.assertEquals("child", child.childId)
    //
    //     // FIXME: How to handle DynamicRealmObject :(
    // }
    //

    // @org.junit.Test
    // fun realmList_addIndex_willAutomaticallyCopy() = realm.executeTransaction { realm ->
    //     val parent = realm.copyToRealm(EmbeddedSimpleListParent("parent"))
    //     parent.children.add(EmbeddedSimpleChild("secondChild"))
    //     parent.children.add(0, EmbeddedSimpleChild("firstChild"))
    //     val child = parent.children.first()!!
    //     Assert.assertTrue(child.isValid)
    //     Assert.assertEquals("firstChild", child.childId)
    //
    //     // FIXME: How to handle DynamicRealmObject :(
    // }
    //

    // @org.junit.Test
    // fun realmList_set_willAutomaticallyCopy() = realm.executeTransaction { realm ->
    //     // Checks that adding an unmanaged embedded object to a list will automatically make
    //     // it managed
    //     val parent = realm.copyToRealm(EmbeddedSimpleListParent("parent"))
    //     Assert.assertTrue(parent.children.add(EmbeddedSimpleChild("child")))
    //     Assert.assertEquals(1, realm.where<EmbeddedSimpleChild>().count())
    //     parent.children[0] = EmbeddedSimpleChild("OtherChild")
    //     Assert.assertEquals("OtherChild", parent.children.first()!!.childId)
    //     Assert.assertEquals(1, realm.where<EmbeddedSimpleChild>().count())
    //
    //     // FIXME: How to handle DynamicRealmObject :(
    // }
    //

    // NOT RELEVANT - Not possible with EmbeddedObject
    // @org.junit.Test
    // fun copyToRealm_noParentThrows() = realm.executeTransaction {
    //     assertFailsWith<IllegalArgumentException> {
    //         realm.copyToRealm(EmbeddedSimpleChild("child"))
    //     }
    // }
    //

    // NOT RELEVANT - Not possible with EmbeddedObject
    // @org.junit.Test
    // fun copyToRealmOrUpdate_NoParentThrows() = realm.executeTransaction {
    //     assertFailsWith<IllegalArgumentException> {
    //         realm.copyToRealmOrUpdate(EmbeddedSimpleChild("child"))
    //     }
    // }
    //

    @Test
    fun copyToRealm_child() {
        realm.writeBlocking {
            val parent = EmbeddedParent()
            parent.child = EmbeddedChild()
            copyToRealm(parent)
        }

        realm.query<EmbeddedParent>().find().single()
        realm.query<EmbeddedChild>().find().single()
    }

    @Test
    fun copyToRealm_childList() {
        realm.writeBlocking {
            val parent = EmbeddedParent()
            parent.childList = realmListOf(EmbeddedChild())
            copyToRealm(parent)
        }

        realm.query<EmbeddedParent>().find().single()
        realm.query<EmbeddedChild>().find().single()
    }

    @Test
    fun copyToRealm_treeSchema() {
        realm.writeBlocking {
            val parent = EmbeddedParent().apply {
                child = EmbeddedChild().apply {
                    id = "level1-child1"
                    innerChild = EmbeddedInnerChild().apply {
                        id = "level2-child1"
                    }
                }
                // Verify that we cache parent reference and don't reimport it
                child!!.subTree = this

                childList = realmListOf(
                    EmbeddedChild().apply {
                        id = "level1-child2"
                        innerChild = EmbeddedInnerChild().apply { id = "level2-child2" }
                    },
                )
            }
            copyToRealm(parent)
        }

        realm.query<EmbeddedParent>().find().single()
        assertEquals(2, realm.query<EmbeddedChild>().find().count())
        assertEquals(2, realm.query<EmbeddedInnerChild>().find().count())
    }

    // Is this the semantic that we want
    // @org.junit.Test
    // fun copyToRealm_throwsIfMultipleRefsToListObjectsExists() {
    //     realm.executeTransaction { r ->
    //         val parent = EmbeddedSimpleListParent("parent")
    //         val child = EmbeddedSimpleChild("child")
    //         parent.children = RealmList(child, child)
    //         assertFailsWith<IllegalArgumentException> { r.copyToRealm(parent) }
    //     }
    // }
    //

    @Test
    fun copyToRealm_update_deleteReplacedObjects() {
        realm.writeBlocking {
            copyToRealm(
                EmbeddedParent().apply {
                    id = "parent"
                    child = EmbeddedChild()
                }
            )
        }
        realm.query<EmbeddedParent>().find().single()
        realm.query<EmbeddedChild>().find().single()

        realm.writeBlocking {
            copyToRealm(
                // Need to replicate object as sharing it on native freezes the old one
                EmbeddedParent().apply {
                    id = "parent"
                child = null
                },
                MutableRealm.UpdatePolicy.ALL
            )
        }
        realm.query<EmbeddedParent>().find().single()
        assertTrue {  realm.query<EmbeddedChild>().find().none() }
    }

    // NOT RELEVANT - No import in API
    // @org.junit.Test
    // fun insert_noParentThrows() {
    //     realm.executeTransaction { realm ->
    //         val child = EmbeddedSimpleChild("child")
    //         assertFailsWith<IllegalArgumentException> { realm.insert(child) }
    //     }
    // }
    //

    // NOT SURE WHAT TO TEST HERE
    // @org.junit.Test
    // @Ignore("Add in another PR")
    // fun insertOrUpdate_throws() {
    //     TODO()
    // }
    //

    // NOT RELEVANT - No import in API
    // @org.junit.Test
    // fun insert_simpleSingleChild() {
    //     realm.executeTransaction {
    //         val parent = EmbeddedSimpleParent("parent1")
    //         parent.child = EmbeddedSimpleChild("child1")
    //         it.insert(parent)
    //     }
    //
    //     Assert.assertEquals(1, realm.where<EmbeddedSimpleParent>().count())
    //     Assert.assertEquals(1, realm.where<EmbeddedSimpleChild>().count())
    // }
    //

    // NOT RELEVANT - No import in API
    // @org.junit.Test
    // fun insert_simpleChildList() {
    //     realm.executeTransaction {
    //         val parent = EmbeddedSimpleListParent("parent1")
    //         parent.children = RealmList(EmbeddedSimpleChild("child1"))
    //         it.insert(parent)
    //     }
    //
    //     Assert.assertEquals(1, realm.where<EmbeddedSimpleListParent>().count())
    //     Assert.assertEquals(1, realm.where<EmbeddedSimpleChild>().count())
    // }
    //

    // NOT RELEVANT - No import in API
    // @org.junit.Test
    // fun insert_treeSchema() {
    //     realm.executeTransaction {
    //         val parent = EmbeddedTreeParent("parent1")
    //
    //         val node1 = EmbeddedTreeNode("node1")
    //         node1.leafNode = EmbeddedTreeLeaf("leaf1")
    //         parent.middleNode = node1
    //         val node2 = EmbeddedTreeNode("node2")
    //         node2.leafNodeList.add(EmbeddedTreeLeaf("leaf2"))
    //         node2.leafNodeList.add(EmbeddedTreeLeaf("leaf3"))
    //         parent.middleNodeList.add(node2)
    //
    //         it.insert(parent)
    //     }
    //
    //     Assert.assertEquals(1, realm.where<EmbeddedTreeParent>().count())
    //     Assert.assertEquals("parent1", realm.where<EmbeddedTreeParent>().findFirst()!!._id)
    //
    //     Assert.assertEquals(2, realm.where<EmbeddedTreeNode>().count())
    //     val nodeResults = realm.where<EmbeddedTreeNode>().findAll()
    //     Assert.assertTrue(nodeResults.any { it.treeNodeId == "node1" })
    //     Assert.assertTrue(nodeResults.any { it.treeNodeId == "node2" })
    //
    //     Assert.assertEquals(3, realm.where<EmbeddedTreeLeaf>().count())
    //     val leafResults = realm.where<EmbeddedTreeLeaf>().findAll()
    //     Assert.assertTrue(leafResults.any { it.treeLeafId == "leaf1" })
    //     Assert.assertTrue(leafResults.any { it.treeLeafId == "leaf2" })
    //     Assert.assertTrue(leafResults.any { it.treeLeafId == "leaf3" })
    // }
    //

    // ALREADY COVERED by copyToRealm_update_deleteReplacedObjects
    // @org.junit.Test
    // fun insertOrUpdate_deletesOldEmbeddedObject() {
    //     realm.executeTransaction { realm ->
    //         val parent = EmbeddedSimpleParent("parent")
    //         val originalChild = EmbeddedSimpleChild("originalChild")
    //         parent.child = originalChild
    //         realm.insert(parent)
    //
    //         Assert.assertEquals(1, realm.where<EmbeddedSimpleChild>().count())
    //         val managedChild = realm.where<EmbeddedSimpleChild>()
    //             .equalTo("childId", "originalChild")
    //             .findFirst()
    //         Assert.assertTrue(managedChild!!.isValid)
    //
    //         val newChild = EmbeddedSimpleChild("newChild")
    //         parent.child = newChild
    //         realm.insertOrUpdate(parent)
    //         Assert.assertTrue(!managedChild.isValid)
    //
    //         Assert.assertEquals(1, realm.where<EmbeddedSimpleChild>().count())
    //         val managedNewChild = realm.where<EmbeddedSimpleChild>()
    //             .equalTo("childId", "newChild")
    //             .findFirst()
    //         Assert.assertEquals(managedNewChild!!.childId, "newChild")
    //         Assert.assertEquals(
    //             0,
    //             realm.where<EmbeddedSimpleChild>()
    //                 .equalTo("childId", "originalChild")
    //                 .findAll()
    //                 .size
    //         )
    //     }
    // }
    //

    // NOT RELEVANT - No import in API
    // @org.junit.Test
    // fun insert_listWithEmbeddedObjects() {
    //     realm.executeTransaction { realm ->
    //         val list = listOf<EmbeddedSimpleParent>(
    //             EmbeddedSimpleParent("parent1").apply { child = EmbeddedSimpleChild("child1") },
    //             EmbeddedSimpleParent("parent2").apply { child = EmbeddedSimpleChild("child2") },
    //             EmbeddedSimpleParent("parent3").apply { child = EmbeddedSimpleChild("child3") }
    //         )
    //         realm.insert(list)
    //
    //         realm.where<EmbeddedSimpleParent>()
    //             .findAll()
    //             .sort("_id")
    //             .also { results ->
    //                 Assert.assertEquals(3, results.count())
    //                 assertEquals(list[0]._id, results[0]!!._id)
    //                 assertEquals(list[1]._id, results[1]!!._id)
    //                 assertEquals(list[2]._id, results[2]!!._id)
    //                 assertEquals(list[0].child!!.childId, results[0]!!.child!!.childId)
    //                 assertEquals(list[1].child!!.childId, results[1]!!.child!!.childId)
    //                 assertEquals(list[2].child!!.childId, results[2]!!.child!!.childId)
    //             }
    //
    //         realm.where<EmbeddedSimpleChild>()
    //             .findAll()
    //             .sort("childId")
    //             .also { results ->
    //                 Assert.assertEquals(3, results.count())
    //                 assertEquals(list[0].child!!.childId, results[0]!!.childId)
    //                 assertEquals(list[1].child!!.childId, results[1]!!.childId)
    //                 assertEquals(list[2].child!!.childId, results[2]!!.childId)
    //                 assertEquals(list[0]._id, results[0]!!.parent._id)
    //                 assertEquals(list[1]._id, results[1]!!.parent._id)
    //                 assertEquals(list[2]._id, results[2]!!.parent._id)
    //             }
    //     }
    // }
    //

    // NOT RELEVANT - No import in API
    // @org.junit.Test
    // fun insert_listWithEmbeddedObjects_duplicatePrimaryKeyThrows() {
    //     realm.executeTransaction { realm ->
    //         val list = listOf<EmbeddedSimpleParent>(
    //             EmbeddedSimpleParent("parent1").apply { child = EmbeddedSimpleChild("child1") },
    //             EmbeddedSimpleParent("parent2").apply { child = EmbeddedSimpleChild("child2") },
    //             EmbeddedSimpleParent("parent3").apply { child = EmbeddedSimpleChild("child3") }
    //         )
    //         realm.insert(list)
    //
    //         assertFailsWith<RealmPrimaryKeyConstraintException> {
    //             realm.insert(list)
    //         }
    //     }
    // }
    //

    // NOT RELEVANT - No import in API
    // @org.junit.Test
    // fun insert_listWithEmbeddedObjects_insertingChildrenDirectlyThrows() {
    //     val list = listOf<EmbeddedSimpleChild>(
    //         EmbeddedSimpleChild("child1"),
    //         EmbeddedSimpleChild("child2"),
    //         EmbeddedSimpleChild("child3")
    //     )
    //
    //     realm.executeTransaction { realm ->
    //         assertFailsWith<IllegalArgumentException> {
    //             realm.insert(list);
    //         }
    //     }
    // }
    //
    // @org.junit.Test
    // fun insertOrUpdate_listWithEmbeddedObjects() {
    //     realm.executeTransaction { realm ->
    //         val list = listOf<EmbeddedSimpleParent>(
    //             EmbeddedSimpleParent("parent1").apply { child = EmbeddedSimpleChild("child1") },
    //             EmbeddedSimpleParent("parent2").apply { child = EmbeddedSimpleChild("child2") },
    //             EmbeddedSimpleParent("parent3").apply { child = EmbeddedSimpleChild("child3") }
    //         )
    //         realm.insert(list)
    //
    //         val newList = listOf<EmbeddedSimpleParent>(
    //             EmbeddedSimpleParent("parent1").apply { child = EmbeddedSimpleChild("newChild1") },
    //             EmbeddedSimpleParent("parent2").apply { child = EmbeddedSimpleChild("newChild2") },
    //             EmbeddedSimpleParent("parent3").apply { child = EmbeddedSimpleChild("newChild3") }
    //         )
    //         realm.insertOrUpdate(newList)
    //
    //         Assert.assertNull(
    //             realm.where<EmbeddedSimpleChild>().equalTo("childId", list[0].child!!.childId)
    //                 .findFirst()
    //         )
    //         Assert.assertNull(
    //             realm.where<EmbeddedSimpleChild>().equalTo("childId", list[1].child!!.childId)
    //                 .findFirst()
    //         )
    //         Assert.assertNull(
    //             realm.where<EmbeddedSimpleChild>().equalTo("childId", list[2].child!!.childId)
    //                 .findFirst()
    //         )
    //         Assert.assertNotNull(
    //             realm.where<EmbeddedSimpleChild>().equalTo("childId", newList[0].child!!.childId)
    //                 .findFirst()
    //         )
    //         Assert.assertNotNull(
    //             realm.where<EmbeddedSimpleChild>().equalTo("childId", newList[1].child!!.childId)
    //                 .findFirst()
    //         )
    //         Assert.assertNotNull(
    //             realm.where<EmbeddedSimpleChild>().equalTo("childId", newList[2].child!!.childId)
    //                 .findFirst()
    //         )
    //
    //         val query = realm.where<EmbeddedSimpleParent>()
    //         Assert.assertEquals(3, query.count())
    //         query.findAll()
    //             .sort("_id")
    //             .also { results ->
    //                 assertEquals(newList[0]._id, results[0]!!._id)
    //                 assertEquals(newList[1]._id, results[1]!!._id)
    //                 assertEquals(newList[2]._id, results[2]!!._id)
    //                 assertEquals(newList[0].child!!.childId, results[0]!!.child!!.childId)
    //                 assertEquals(newList[1].child!!.childId, results[1]!!.child!!.childId)
    //                 assertEquals(newList[2].child!!.childId, results[2]!!.child!!.childId)
    //             }
    //
    //         realm.where<EmbeddedSimpleParent>()
    //             .also { Assert.assertEquals(3, it.count()) }
    //             .findAll()
    //             .sort("_id")
    //             .also { results ->
    //                 assertEquals(newList[0]._id, results[0]!!._id)
    //                 assertEquals(newList[1]._id, results[1]!!._id)
    //                 assertEquals(newList[2]._id, results[2]!!._id)
    //                 assertEquals(newList[0].child!!.childId, results[0]!!.child!!.childId)
    //                 assertEquals(newList[1].child!!.childId, results[1]!!.child!!.childId)
    //                 assertEquals(newList[2].child!!.childId, results[2]!!.child!!.childId)
    //             }
    //     }
    // }
    //

    // NOT RELEVANT - No json APIs yet
    // // TODO Move all json import tests to RealmJsonTests when RealmJsonTests have been
    // //  converted to Kotlin
    // // Sanity check of string based variants. Implementation dispatches to json variant covered
    // // below, so not covering all cases for the string-variants.
    // @org.junit.Test
    // fun createObjectFromJson_json_embeddedObjectList() {
    //     realm.executeTransaction { realm ->
    //         realm.createObjectFromJson(EmbeddedSimpleListParent::class.java, json(simpleListParentData))
    //     }
    //     val parent = realm.where(EmbeddedSimpleListParent::class.java).findFirst()!!
    //     Assert.assertEquals(3, parent.children.count())
    //     Assert.assertEquals(childId1, parent.children[0]!!.childId)
    //     Assert.assertEquals(childId2, parent.children[1]!!.childId)
    //     Assert.assertEquals(childId3, parent.children[2]!!.childId)
    // }
    //

    // NOT RELEVANT - No json APIs yet
    // @org.junit.Test
    // fun createObjectFromJson_stream_embeddedObjectList() {
    //     val clz = EmbeddedSimpleListParent::class.java
    //     realm.executeTransaction { realm ->
    //         Assert.assertTrue(realm.schema.getSchemaForClass(clz).hasPrimaryKey())
    //         realm.createObjectFromJson(clz, stream(simpleListParentData))
    //     }
    //     val all = realm.where(EmbeddedSimpleListParent::class.java).findAll()
    //     Assert.assertEquals(1, all.count())
    //     val parent = all.first()!!
    //     Assert.assertEquals(3, parent.children.count())
    //     Assert.assertEquals(childId1, parent.children[0]!!.childId)
    //     Assert.assertEquals(childId2, parent.children[1]!!.childId)
    //     Assert.assertEquals(childId3, parent.children[2]!!.childId)
    // }
    //

    // NOT RELEVANT - No json APIs yet
    // // Stream based import implementation is differentiated depending on whether the class has a primary key
    // @org.junit.Test
    // fun createObjectFromJson_stream_embeddedObjectListWithNoPrimaryKeyParent() {
    //     val clz = EmbeddedSimpleListParentWithoutPrimaryKey::class.java
    //     realm.executeTransaction { realm ->
    //         Assert.assertFalse(realm.schema.getSchemaForClass(clz).hasPrimaryKey())
    //         realm.createObjectFromJson(clz, stream(simpleListParentData))
    //     }
    //     val all = realm.where(EmbeddedSimpleListParentWithoutPrimaryKey::class.java).findAll()
    //     Assert.assertEquals(1, all.count())
    //     val parent = all.first()!!
    //     Assert.assertEquals(parentId, parent._id)
    //     Assert.assertEquals(3, parent.children.count())
    //     Assert.assertEquals(childId1, parent.children[0]!!.childId)
    //     Assert.assertEquals(childId2, parent.children[1]!!.childId)
    //     Assert.assertEquals(childId3, parent.children[2]!!.childId)
    // }
    //

    // NOT RELEVANT - No json APIs yet
    // @org.junit.Test
    // fun createObjectFromJson_orphanedEmbeddedObjectThrows() {
    //     throws { realm.createObjectFromJson(EmbeddedSimpleChild::class.java, json(simpleListParentData)) }
    //     throws { realm.createObjectFromJson(EmbeddedSimpleChild::class.java, string(simpleListParentData)) }
    //     throws { realm.createObjectFromJson(EmbeddedSimpleChild::class.java, stream(simpleListParentData)) }
    // }
    //
    // private fun throws(block: () -> Unit) {
    //     assertFailsWith<IllegalArgumentException> {
    //         realm.executeTransaction { realm ->
    //             block()
    //         }
    //     }
    // }
    //
    //

    // NOT RELEVANT - No schema modificiation API yet
    // @org.junit.Test
    // fun realmObjectSchema_setEmbedded() {
    //     DynamicRealm.getInstance(realm.configuration).use { realm ->
    //         realm.executeTransaction {
    //             val objSchema: RealmObjectSchema = realm.schema[EmbeddedSimpleChild.NAME]!!
    //             Assert.assertTrue(objSchema.isEmbedded)
    //             objSchema.isEmbedded = false
    //             Assert.assertFalse(objSchema.isEmbedded)
    //             objSchema.isEmbedded = true
    //             Assert.assertTrue(objSchema.isEmbedded)
    //         }
    //     }
    // }
    //

    // NOT RELEVANT - No json APIs yet
    // @org.junit.Test
    // fun realmObjectSchema_setEmbedded_throwsWithPrimaryKey() {
    //     DynamicRealm.getInstance(realm.configuration).use { realm ->
    //         realm.executeTransaction {
    //             val objSchema: RealmObjectSchema = realm.schema[AllJavaTypes.CLASS_NAME]!!
    //             assertFailsWith<IllegalStateException> { objSchema.isEmbedded = true }
    //         }
    //     }
    // }
    //

    // NOT RELEVANT - No json APIs yet
    // @org.junit.Test
    // fun realmObjectSchema_setEmbedded_throwsIfBreaksParentInvariants() {
    //     // Classes can only be converted to be embedded if all objects have exactly one other
    //     // object pointing to it.
    //     DynamicRealm.getInstance(realm.configuration).use { realm ->
    //         realm.executeTransaction {
    //
    //             // Create object with no parents
    //             realm.createObject(Dog.CLASS_NAME)
    //             val dogSchema = realm.schema[Dog.CLASS_NAME]!!
    //             assertFailsWith<IllegalStateException> {
    //                 dogSchema.isEmbedded = true
    //             }
    //
    //             // Create object with two parents
    //             val cat: DynamicRealmObject = realm.createObject(Cat.CLASS_NAME)
    //             val owner1: DynamicRealmObject = realm.createObject(Owner.CLASS_NAME)
    //             owner1.setObject(Owner.FIELD_CAT, cat)
    //             val owner2: DynamicRealmObject = realm.createObject(Owner.CLASS_NAME)
    //             owner2.setObject(Owner.FIELD_CAT, cat)
    //             val catSchema = realm.schema[Cat.CLASS_NAME]!!
    //             assertFailsWith<IllegalStateException> {
    //                 catSchema.isEmbedded = true
    //             }
    //         }
    //     }
    // }
    //

    // In RealmSchemaTest
    // @org.junit.Test
    // fun realmObjectSchema_isEmbedded() {
    //     Assert.assertTrue(realm.schema[EmbeddedSimpleChild.NAME]!!.isEmbedded)
    //     Assert.assertFalse(realm.schema[AllTypes.CLASS_NAME]!!.isEmbedded)
    // }
    //

    // NOT RELEVANT - Not specific to Dynamic realms, so above should be sufficient
    // @org.junit.Test
    // fun dynamicRealm_realmObjectSchema_isEmbedded() {
    //     DynamicRealm.getInstance(realm.configuration).use { realm ->
    //         Assert.assertTrue(realm.schema[EmbeddedSimpleChild.NAME]!!.isEmbedded)
    //         Assert.assertFalse(realm.schema[AllTypes.CLASS_NAME]!!.isEmbedded)
    //     }
    // }
    //

    // // Check that deleting a non-embedded parent deletes all embedded children
    // @org.junit.Test
    // fun deleteParentObject_deletesEmbeddedChildren() = realm.executeTransaction {
    //     val parent = EmbeddedSimpleParent("parent")
    //     parent.child = EmbeddedSimpleChild("child")
    //
    //     val managedParent: EmbeddedSimpleParent = it.copyToRealm(parent)
    //     Assert.assertEquals(1, realm.where<EmbeddedSimpleChild>().count())
    //     val managedChild: EmbeddedSimpleChild = managedParent.child!!
    //
    //     managedParent.deleteFromRealm()
    //     Assert.assertFalse(managedChild.isValid)
    //     Assert.assertEquals(0, realm.where<EmbeddedSimpleParent>().count())
    //     Assert.assertEquals(0, realm.where<EmbeddedSimpleChild>().count())
    // }
    //

    // @org.junit.Test
    // fun dynamicRealm_deleteParentObject_deletesEmbeddedChildren() =
    //     DynamicRealm.getInstance(realm.configuration).use { realm ->
    //         realm.executeTransaction {
    //             val parent = realm.createObject("EmbeddedSimpleParent", "parent")
    //             Assert.assertEquals(0, realm.where("EmbeddedSimpleChild").count())
    //
    //             val child = realm.createEmbeddedObject("EmbeddedSimpleChild", parent, "child")
    //             Assert.assertEquals(1, realm.where("EmbeddedSimpleChild").count())
    //
    //             parent.deleteFromRealm()
    //             Assert.assertFalse(child.isValid)
    //             Assert.assertEquals(0, realm.where("EmbeddedSimpleParent").count())
    //             Assert.assertEquals(0, realm.where("EmbeddedSimpleChild").count())
    //         }
    //     }
    //

    // // Check that deleting a embedded parent deletes all embedded children
    // @org.junit.Test
    // fun deleteParentEmbeddedObject_deletesEmbeddedChildren() = realm.executeTransaction {
    //     val parent = EmbeddedTreeParent("parent1")
    //     val middleNode = EmbeddedTreeNode("node1")
    //     middleNode.leafNode = EmbeddedTreeLeaf("leaf1")
    //     middleNode.leafNodeList.add(EmbeddedTreeLeaf("leaf2"))
    //     middleNode.leafNodeList.add(EmbeddedTreeLeaf("leaf3"))
    //     parent.middleNode = middleNode
    //
    //     val managedParent: EmbeddedTreeParent = it.copyToRealm(parent)
    //     Assert.assertEquals(1, realm.where<EmbeddedTreeNode>().count())
    //     Assert.assertEquals(3, realm.where<EmbeddedTreeLeaf>().count())
    //     managedParent.deleteFromRealm()
    //     Assert.assertEquals(0, realm.where<EmbeddedTreeNode>().count())
    //     Assert.assertEquals(0, realm.where<EmbeddedSimpleChild>().count())
    // }
    //

    // @org.junit.Test
    // fun dynamic_deleteParentEmbeddedObject_deletesEmbeddedChildren() =
    //     DynamicRealm.getInstance(realm.configuration).use { realm ->
    //         realm.executeTransaction {
    //             val parent = realm.createObject("EmbeddedTreeParent", "parent1")
    //             val middleNode = realm.createEmbeddedObject("EmbeddedTreeNode", parent, "middleNode")
    //             middleNode.setString("treeNodeId", "node1")
    //             val leaf1 = realm.createEmbeddedObject("EmbeddedTreeLeaf", middleNode, "leafNode")
    //             val leaf2 = realm.createEmbeddedObject("EmbeddedTreeLeaf", middleNode, "leafNodeList")
    //             val leaf3 = realm.createEmbeddedObject("EmbeddedTreeLeaf", middleNode, "leafNodeList")
    //
    //             Assert.assertEquals(1, realm.where("EmbeddedTreeNode").count())
    //             Assert.assertEquals(3, realm.where("EmbeddedTreeLeaf").count())
    //             parent.deleteFromRealm()
    //             Assert.assertEquals(0, realm.where("EmbeddedTreeNode").count())
    //             Assert.assertEquals(0, realm.where("EmbeddedSimpleChild").count())
    //             Assert.assertFalse(parent.isValid)
    //             Assert.assertFalse(middleNode.isValid)
    //             Assert.assertFalse(leaf1.isValid)
    //             Assert.assertFalse(leaf2.isValid)
    //             Assert.assertFalse(leaf3.isValid)
    //         }
    //     }
    //

    // // Cascade deleting an embedded object will trigger its object listener.
    // @org.junit.Test
    // fun deleteParent_triggerChildObjectNotifications() = looperThread.runBlocking {
    //     val realm = Realm.getInstance(realm.configuration)
    //     looperThread.closeAfterTest(realm)
    //
    //     realm.executeTransaction {
    //         val parent = EmbeddedSimpleParent("parent")
    //         val child = EmbeddedSimpleChild("child")
    //         parent.child = child
    //         it.copyToRealm(parent)
    //     }
    //
    //     val child = realm.where<EmbeddedSimpleParent>().findFirst()!!.child!!
    //     child.addChangeListener(RealmChangeListener<EmbeddedSimpleChild> {
    //         if (!it.isValid) {
    //             looperThread.testComplete()
    //         }
    //     })
    //
    //     realm.executeTransaction {
    //         child.parent.deleteFromRealm()
    //     }
    // }
    //

    // @org.junit.Test
    // fun dynamicRealm_deleteParent_triggerChildObjectNotifications() = looperThread.runBlocking {
    //     val realm = DynamicRealm.getInstance(realm.configuration)
    //     looperThread.closeAfterTest(realm)
    //
    //     realm.executeTransaction {
    //         val parent = realm.createObject("EmbeddedSimpleParent", "parent")
    //         realm.createEmbeddedObject("EmbeddedSimpleChild", parent, "child")
    //     }
    //
    //     val queriedChild = realm.where("EmbeddedSimpleParent")
    //         .findFirst()!!
    //         .getObject("child")!!
    //         .apply {
    //             addChangeListener(RealmChangeListener<DynamicRealmObject> {
    //                 if (!it.isValid) {
    //                     looperThread.testComplete()
    //                 }
    //             })
    //         }
    //
    //     realm.executeTransaction {
    //         queriedChild.linkingObjects("EmbeddedSimpleParent", "child")
    //             .first()!!
    //             .deleteFromRealm()
    //     }
    // }
    //

    // // Cascade deleting a parent will trigger the listener on any lists in child embedded
    // // objects
    // @org.junit.Test
    // fun deleteParent_triggerChildListObjectNotifications() = looperThread.runBlocking {
    //     val realm = Realm.getInstance(realm.configuration)
    //     looperThread.closeAfterTest(realm)
    //
    //     realm.executeTransaction {
    //         val parent = EmbeddedSimpleListParent("parent")
    //         val child1 = EmbeddedSimpleChild("child1")
    //         val child2 = EmbeddedSimpleChild("child2")
    //         parent.children.add(child1)
    //         parent.children.add(child2)
    //         it.copyToRealm(parent)
    //     }
    //
    //     val children: RealmList<EmbeddedSimpleChild> = realm.where<EmbeddedSimpleListParent>()
    //         .findFirst()!!
    //         .children
    //
    //     children.addChangeListener { list ->
    //         if (!list.isValid) {
    //             looperThread.testComplete()
    //         }
    //     }
    //
    //     realm.executeTransaction {
    //         realm.where<EmbeddedSimpleListParent>().findFirst()!!.deleteFromRealm()
    //     }
    // }
    //

    // @org.junit.Test
    // fun dynamicRealm_deleteParent_triggerChildListObjectNotifications() = looperThread.runBlocking {
    //     val realm = DynamicRealm.getInstance(realm.configuration)
    //     looperThread.closeAfterTest(realm)
    //
    //     realm.executeTransaction {
    //         val parent = realm.createObject("EmbeddedSimpleListParent", "parent")
    //         realm.createEmbeddedObject("EmbeddedSimpleChild", parent, "children")
    //         realm.createEmbeddedObject("EmbeddedSimpleChild", parent, "children")
    //     }
    //
    //     realm.where("EmbeddedSimpleListParent")
    //         .findFirst()!!
    //         .getList("children")
    //         .apply {
    //             addChangeListener { list ->
    //                 if (!list.isValid) {
    //                     looperThread.testComplete()
    //                 }
    //             }
    //         }
    //
    //     realm.executeTransaction {
    //         realm.where("EmbeddedSimpleListParent")
    //             .findFirst()!!
    //             .deleteFromRealm()
    //     }
    // }
    //

    // @org.junit.Test
    // fun copyToRealmOrUpdate_replacesEmbededdList() {
    //     realm.beginTransaction()
    //     val parent = EmbeddedTreeParent()
    //     parent.middleNodeList = RealmList(EmbeddedTreeNode("1"), EmbeddedTreeNode("2"))
    //     parent._id = "1"
    //     realm.copyToRealm(parent)
    //     realm.commitTransaction()
    //
    //     realm.beginTransaction()
    //     parent.middleNodeList.add(EmbeddedTreeNode("3"))
    //     val managedParent = realm.copyToRealmOrUpdate(parent)
    //     Assert.assertEquals(3, managedParent.middleNodeList.size)
    //     realm.commitTransaction()
    // }
    //

    // @org.junit.Test
    // fun insertOrUpdate_replacesEmbededdList() {
    //     realm.beginTransaction()
    //     val parent = EmbeddedTreeParent()
    //     parent.middleNodeList = RealmList(EmbeddedTreeNode("1"), EmbeddedTreeNode("2"))
    //     parent._id = "1"
    //     val managedParent = realm.copyToRealm(parent)
    //     realm.commitTransaction()
    //
    //     realm.beginTransaction()
    //
    //     // insertOrUpdate has different code paths for lists of equal size vs. lists of different sizes
    //     parent.middleNodeList = RealmList(EmbeddedTreeNode("3"), EmbeddedTreeNode("4"))
    //     realm.insertOrUpdate(parent)
    //     Assert.assertEquals(2, managedParent.middleNodeList.size)
    //     Assert.assertEquals("3", managedParent.middleNodeList[0]!!.treeNodeId)
    //     Assert.assertEquals("4", managedParent.middleNodeList[1]!!.treeNodeId)
    //
    //     parent.middleNodeList.add(EmbeddedTreeNode("5"))
    //     realm.insertOrUpdate(parent)
    //     Assert.assertEquals(3, managedParent.middleNodeList.size)
    //     realm.commitTransaction()
    // }
    //

    // NO BULD APIS YET
    // @org.junit.Test
    // @Ignore("Add in another PR")
    // fun results_bulkUpdate() {
    //     // What happens if you bulk update a RealmResults. Should it be allowed to use embeded
    //     // objects here?
    //     TODO()
    // }
}
