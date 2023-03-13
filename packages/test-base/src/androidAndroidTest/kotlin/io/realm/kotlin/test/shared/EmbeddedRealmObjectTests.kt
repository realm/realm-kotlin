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

package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.entities.embedded.EmbeddedChild
import io.realm.kotlin.entities.embedded.EmbeddedChildWithInitializer
import io.realm.kotlin.entities.embedded.EmbeddedChildWithPrimaryKeyParent
import io.realm.kotlin.entities.embedded.EmbeddedInnerChild
import io.realm.kotlin.entities.embedded.EmbeddedParent
import io.realm.kotlin.entities.embedded.EmbeddedParentWithPrimaryKey
import io.realm.kotlin.entities.embedded.embeddedSchema
import io.realm.kotlin.entities.embedded.embeddedSchemaWithPrimaryKey
import io.realm.kotlin.ext.parent
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.TypedRealmObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EmbeddedRealmObjectTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(schema = embeddedSchema + embeddedSchemaWithPrimaryKey + EmbeddedChildWithInitializer::class)
                .directory(tmpDir)
                .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun parent() {
        realm.writeBlocking {
            val parent = copyToRealm(
                EmbeddedParent().apply {
                    id = RealmUUID.random().toString()
                    child = EmbeddedChild()
                }
            )
            parent.child!!.let { child ->
                child.parent<EmbeddedParent>().let { backlinkParent: EmbeddedParent ->
                    assertNotNull(backlinkParent)
                    assertIs<EmbeddedParent>(backlinkParent)
                    assertEquals(parent.id, backlinkParent.id)
                }

                child.parent<TypedRealmObject>().let { backlinkParent: TypedRealmObject ->
                    assertNotNull(backlinkParent)
                    assertIs<TypedRealmObject>(backlinkParent)
                    assertIs<EmbeddedParent>(backlinkParent)
                    assertEquals(parent.id, backlinkParent.id)
                }
            }
        }
    }

    @Test
    fun parentWrongType_throws() {
        realm.writeBlocking {
            val parent = copyToRealm(
                EmbeddedParent().apply {
                    child = EmbeddedChild()
                }
            )
            val child = parent.child!!
            assertFailsWith<ClassCastException>("io.realm.kotlin.entities.embedded.EmbeddedParent cannot be cast to io.realm.kotlin.entities.embedded.EmbeddedChild") {
                val parentFromChild: EmbeddedChild = child.parent()
            }
        }
    }

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
            val child = EmbeddedChild()
            parent.child = child
            parent.childrenList = realmListOf(child, child)
            parent.childrenDictionary = realmDictionaryOf("A" to child, "B" to child)
            copyToRealm(parent)
        }

        realm.query<EmbeddedParent>().find().single().run {
            assertNotNull(child)
            assertEquals(2, childrenList.size)
            assertEquals(2, childrenDictionary.size)
        }
        realm.query<EmbeddedChild>().find().run {
            // Every reference to an embedded object is cloned
            assertEquals(5, size)
        }
    }

    @Test
    @Suppress("NestedBlockDepth")
    fun copyToRealm_tree_mixedRealmAndEmbeddedRealmObject_list() {
        realm.writeBlocking {
            val parent = EmbeddedParent().apply {
                id = "level1-parent"
                child = EmbeddedChild().apply {
                    id = "level1-child1"
                    innerChild = EmbeddedInnerChild().apply {
                        id = "level1-innerchild1"
                    }
                }
                child!!.subTree = EmbeddedParent().apply {
                    id = "level2-parent"
                    child = EmbeddedChild().apply {
                        id = "level2-child1"
                        innerChild = EmbeddedInnerChild().apply {
                            id = "level2-innerchild1"
                        }
                    }
                }

                childrenList = realmListOf(
                    EmbeddedChild().apply {
                        id = "level1-child2"
                        innerChild = EmbeddedInnerChild().apply { id = "level2-child2" }
                    },
                )
            }
            // Verify that we cache parent reference and don't reimport it
            parent.child!!.subTree!!.child!!.subTree = parent
            copyToRealm(parent)
        }

        realm.query<EmbeddedParent>("id = 'level1-parent'").find().single().run {
            assertEquals("level1-parent", id)
            child!!.run {
                assertEquals("level1-child1", id)
                assertEquals("level1-innerchild1", innerChild!!.id)
                subTree!!.run {
                    assertEquals("level2-parent", id)
                    child!!.run {
                        assertEquals("level2-child1", id)
                        assertEquals("level2-innerchild1", innerChild!!.id)
                        assertEquals("level1-parent", subTree!!.id)
                    }
                }
            }
        }

        assertEquals(3, realm.query<EmbeddedChild>().find().count())
        assertEquals(3, realm.query<EmbeddedInnerChild>().find().count())
    }

    @Test
    @Suppress("NestedBlockDepth")
    fun copyToRealm_tree_mixedRealmAndEmbeddedRealmObject_dictionary() {
        realm.writeBlocking {
            val parent = EmbeddedParent().apply {
                id = "level1-parent"
                child = EmbeddedChild().apply {
                    id = "level1-child1"
                    innerChild = EmbeddedInnerChild().apply {
                        id = "level1-innerchild1"
                    }
                }
                child!!.subTree = EmbeddedParent().apply {
                    id = "level2-parent"
                    child = EmbeddedChild().apply {
                        id = "level2-child1"
                        innerChild = EmbeddedInnerChild().apply {
                            id = "level2-innerchild1"
                        }
                    }
                }

                childrenDictionary = realmDictionaryOf(
                    "A" to EmbeddedChild().apply {
                        id = "level1-child2"
                        innerChild = EmbeddedInnerChild().apply { id = "level2-child2" }
                    },
                )
            }
            // Verify that we cache parent reference and don't reimport it
            parent.child!!.subTree!!.child!!.subTree = parent
            copyToRealm(parent)
        }

        realm.query<EmbeddedParent>("id = 'level1-parent'").find().single().run {
            assertEquals("level1-parent", id)
            child!!.run {
                assertEquals("level1-child1", id)
                assertEquals("level1-innerchild1", innerChild!!.id)
                subTree!!.run {
                    assertEquals("level2-parent", id)
                    child!!.run {
                        assertEquals("level2-child1", id)
                        assertEquals("level2-innerchild1", innerChild!!.id)
                        assertEquals("level1-parent", subTree!!.id)
                    }
                }
            }
        }

        assertEquals(3, realm.query<EmbeddedChild>().find().count())
        assertEquals(3, realm.query<EmbeddedInnerChild>().find().count())
    }

    @Test
    fun copyToRealm_update_deleteReplacedObjects() {
        realm.writeBlocking {
            copyToRealm(
                EmbeddedParentWithPrimaryKey().apply {
                    id = 1
                    child = EmbeddedChildWithPrimaryKeyParent("child1")
                    childrenList = realmListOf(EmbeddedChildWithPrimaryKeyParent("child2"))
                    childrenDictionary =
                        realmDictionaryOf("A" to EmbeddedChildWithPrimaryKeyParent("child3"))
                }
            )
        }
        realm.query<EmbeddedParentWithPrimaryKey>().find().single().run {
            assertNotNull(child)
            assertEquals(1, childrenList.size)
            assertEquals(1, childrenDictionary.size)
        }
        assertEquals(3, realm.query<EmbeddedChildWithPrimaryKeyParent>().find().size)

        realm.writeBlocking {
            copyToRealm(
                // Need to replicate object as sharing it on native freezes the old one
                EmbeddedParentWithPrimaryKey().apply {
                    id = 1
                    child = EmbeddedChildWithPrimaryKeyParent("child3")
                    childrenList = realmListOf(
                        EmbeddedChildWithPrimaryKeyParent("child4"),
                        EmbeddedChildWithPrimaryKeyParent("child5")
                    )
                    childrenDictionary = realmDictionaryOf(
                        "A" to EmbeddedChildWithPrimaryKeyParent("child6"),
                        "B" to EmbeddedChildWithPrimaryKeyParent("child7")
                    )
                },
                UpdatePolicy.ALL
            )
        }
        realm.query<EmbeddedParentWithPrimaryKey>().find().single()
        realm.query<EmbeddedChildWithPrimaryKeyParent>().find().run {
            assertEquals(5, size)
            forEach {
                assertTrue { it.id in setOf("child3", "child4", "child5", "child6", "child7") }
            }
        }
    }

    @Test
    fun copyToRealm_withInitializer() {
        realm.writeBlocking {
            copyToRealm(EmbeddedChildWithInitializer())
        }
        realm.query<EmbeddedChild>().find().single().run {
            assertEquals("Initial child", id)
        }
    }

    @Test
    fun setWillDeleteEmbeddedRealmObject() {
        val parent = realm.writeBlocking {
            copyToRealm(EmbeddedParent().apply { child = EmbeddedChild() })
        }
        realm.query<EmbeddedChild>().find().single()
        realm.writeBlocking {
            findLatest(parent)!!.apply {
                child = null
            }
        }
        assertTrue(realm.query<EmbeddedChild>().find().isEmpty())
    }

    @Test
    fun set_unmanaged() {
        val parent = realm.writeBlocking {
            copyToRealm(EmbeddedParent())
        }
        realm.query<EmbeddedChild>().find().none()
        realm.writeBlocking {
            findLatest(parent)!!.apply {
                child = EmbeddedChild().apply {
                    id = "child1"
                    innerChild = EmbeddedInnerChild()
                }
            }
        }
        realm.query<EmbeddedChild>().find().single().run {
            assertEquals("child1", id)
        }
        assertEquals(1, realm.query<EmbeddedInnerChild>().find().size)
    }

    @Test
    fun set_managed() {
        realm.writeBlocking {
            val parent1 = copyToRealm(EmbeddedParent().apply { id = "parent1" })
            val parent2 = copyToRealm(EmbeddedParent().apply { id = "parent2" })

            parent1.child = EmbeddedChild("child1")
            parent2.child = parent1.child
        }
        val children = realm.query<EmbeddedChild>().find()
        children.run {
            assertEquals(2, size)
            forEach { assertEquals("child1", it.id) }
        }
    }

    @Test
    fun set_updatesExistingObjectInTree() {
        val parent = realm.writeBlocking {
            copyToRealm(
                EmbeddedParentWithPrimaryKey().apply {
                    id = 2
                    child = EmbeddedChildWithPrimaryKeyParent().apply {
                        subTree = EmbeddedParentWithPrimaryKey().apply {
                            id = 1
                            name = "INIT"
                        }
                    }
                }
            )
        }
        realm.query<EmbeddedParentWithPrimaryKey>("id = 1").find().single().run {
            assertEquals("INIT", name)
        }

        realm.writeBlocking {
            findLatest(parent)!!.run {
                child = EmbeddedChildWithPrimaryKeyParent().apply {
                    subTree = EmbeddedParentWithPrimaryKey().apply {
                        id = 1
                        name = "UPDATED"
                    }
                }
            }
        }

        realm.query<EmbeddedParentWithPrimaryKey>("id = 1").find().single().run {
            assertEquals("UPDATED", name)
        }
    }

    @Test
    fun list_add() {
        realm.writeBlocking {
            copyToRealm(EmbeddedParent()).apply {
                childrenList.add(EmbeddedChild("child1"))
            }
        }
        realm.query<EmbeddedChild>().find().single().run {
            assertEquals("child1", id)
        }
    }

    @Test
    fun list_addWithIndex() {
        realm.writeBlocking {
            val child1 = EmbeddedChild("child1")
            val child2 = EmbeddedChild("child2")
            copyToRealm(EmbeddedParent()).apply {
                childrenList.add(child1)
                childrenList.add(0, child2)
            }
        }
        realm.query<EmbeddedParent>().find().single().run {
            assertEquals("child2", childrenList[0].id)
            assertEquals("child1", childrenList[1].id)
        }
    }

    @Test
    fun list_addAll() {
        realm.writeBlocking {
            copyToRealm(EmbeddedParent()).run {
                val child = EmbeddedChild("child1").apply {
                    subTree = this@run // EmbeddedParent
                }
                childrenList.addAll(listOf(child, child))
            }
        }
        realm.query<EmbeddedParent>().find().single().run {
            childrenList.forEach { assertEquals("child1", it.id) }
        }
        realm.query<EmbeddedChild>().find().run {
            assertEquals(2, size)
            forEach { assertEquals("child1", it.id) }
        }
    }

    @Test
    fun list_addAllWithIndex() {
        realm.writeBlocking {
            copyToRealm(EmbeddedParent()).apply {
                childrenList.addAll(setOf(EmbeddedChild("child1"), EmbeddedChild("child2")))
                childrenList.addAll(0, setOf(EmbeddedChild("child3"), EmbeddedChild("child4")))
            }
        }
        realm.query<EmbeddedParent>().find().single().run {
            assertEquals("child3", childrenList[0].id)
            assertEquals("child4", childrenList[1].id)
            assertEquals("child1", childrenList[2].id)
            assertEquals("child2", childrenList[3].id)
        }
    }

    @Test
    fun list_set() {
        realm.writeBlocking {
            val parent = copyToRealm(EmbeddedParent()).apply {
                childrenList.add(EmbeddedChild("child1"))
                childrenList.set(0, EmbeddedChild("child2"))
            }
        }
        realm.query<EmbeddedChild>().find().single().run {
            assertEquals("child2", id)
        }
    }

    @Test
    fun dictionary_put() {
        realm.writeBlocking {
            copyToRealm(EmbeddedParent()).apply {
                childrenDictionary["A"] = EmbeddedChild("child1")
                childrenDictionary["B"] = null // Dictionaries of embedded objects support null values
            }
        }
        realm.query<EmbeddedParent>().find().single().run {
            assertEquals(2, childrenDictionary.size)
        }
        realm.query<EmbeddedChild>().find().single().run {
            assertEquals("child1", id)
        }
    }

    @Test
    fun dictionary_putAll() {
        realm.writeBlocking {
            copyToRealm(EmbeddedParent()).run {
                val child = EmbeddedChild("child1").apply {
                    subTree = this@run // EmbeddedParent
                }
                childrenDictionary.putAll(listOf("A" to child, "B" to child))
            }
        }
        realm.query<EmbeddedParent>().find().single().run {
            childrenDictionary.forEach { assertEquals("child1", assertNotNull(it.value).id) }
        }
        realm.query<EmbeddedChild>().find().run {
            assertEquals(2, size)
            forEach { assertEquals("child1", it.id) }
        }
    }

    @Test
    fun deleteParentObject_deletesEmbeddedChildren_list() {
        val parent = EmbeddedParent().apply {
            child = EmbeddedChild("child1")
            childrenList.addAll(setOf(EmbeddedChild("child2"), EmbeddedChild("child3")))
        }

        val managedParent = realm.writeBlocking { copyToRealm(parent) }

        assertEquals(3, realm.query<EmbeddedChild>().find().size)

        realm.writeBlocking { findLatest(managedParent)!!.let { delete(it) } }

        assertEquals(0, realm.query<EmbeddedChild>().find().size)
    }

    @Test
    fun deleteParentObject_deletesEmbeddedChildren_dictionary() {
        val parent = EmbeddedParent().apply {
            child = EmbeddedChild("child1")
            childrenDictionary.putAll(
                setOf("A" to EmbeddedChild("child2"), "B" to EmbeddedChild("child3"))
            )
        }

        val managedParent = realm.writeBlocking { copyToRealm(parent) }

        assertEquals(3, realm.query<EmbeddedChild>().find().size)

        realm.writeBlocking { findLatest(managedParent)!!.let { delete(it) } }

        assertEquals(0, realm.query<EmbeddedChild>().find().size)
    }

    @Test
    fun deleteParentEmbeddedRealmObject_deletesEmbeddedChildren_list() {
        val parent = EmbeddedParent().apply {
            child = EmbeddedChild("child1").apply { innerChild = EmbeddedInnerChild() }
            childrenList.add(EmbeddedChild("child2").apply { innerChild = EmbeddedInnerChild() })
        }

        val managedParent = realm.writeBlocking { copyToRealm(parent) }

        assertEquals(2, realm.query<EmbeddedChild>().find().size)
        assertEquals(2, realm.query<EmbeddedInnerChild>().find().size)

        realm.writeBlocking {
            findLatest(managedParent)!!.run {
                child = null
                childrenList.clear()
            }
        }

        assertEquals(0, realm.query<EmbeddedChild>().find().size)
        assertEquals(0, realm.query<EmbeddedInnerChild>().find().size)
    }

    @Test
    fun deleteParentEmbeddedRealmObject_deletesEmbeddedChildren_dictionary() {
        val parent = EmbeddedParent().apply {
            child = EmbeddedChild("child1").apply { innerChild = EmbeddedInnerChild() }
            childrenDictionary["A"] =
                EmbeddedChild("child2").apply { innerChild = EmbeddedInnerChild() }
        }

        val managedParent = realm.writeBlocking { copyToRealm(parent) }

        assertEquals(2, realm.query<EmbeddedChild>().find().size)
        assertEquals(2, realm.query<EmbeddedInnerChild>().find().size)

        realm.writeBlocking {
            findLatest(managedParent)!!.run {
                child = null
                childrenDictionary.clear()
            }
        }

        assertEquals(0, realm.query<EmbeddedChild>().find().size)
        assertEquals(0, realm.query<EmbeddedInnerChild>().find().size)
    }
}
