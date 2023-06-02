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

@file:Suppress("invisible_member", "invisible_reference")

package io.realm.kotlin.test.common

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.backlink.Child
import io.realm.kotlin.entities.backlink.EmbeddedChild
import io.realm.kotlin.entities.backlink.MissingSourceProperty
import io.realm.kotlin.entities.backlink.Parent
import io.realm.kotlin.entities.backlink.Parent2
import io.realm.kotlin.entities.backlink.Recursive
import io.realm.kotlin.ext.backlinks
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.internal.asDynamicRealm
import io.realm.kotlin.query.find
import io.realm.kotlin.test.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class BacklinksTests {
    private lateinit var realm: Realm
    private lateinit var tmpDir: String

    private fun Child.assertParents(expectedSize: Int) {
        parents.firstOrNull()?.let { assertIs<Parent>(it) }

        assertEquals(expectedSize, parents.size)
        assertEquals(expectedSize, parentsByList.size)
        assertEquals(expectedSize, parentsBySet.size)
        assertEquals(expectedSize, parentsByDictionary.size)
    }

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(
                setOf(
                    Parent::class,
                    Parent2::class,
                    Child::class,
                    Recursive::class,
                    EmbeddedChild::class
                )
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
    fun unmanaged_throws() {
        val child = Child()
        val parent = Parent()

        parent.child = child
        parent.childSet = realmSetOf(child)
        parent.childList = realmListOf(child)
        parent.childDictionary = realmDictionaryOf("A" to child)

        assertFailsWithMessage<IllegalStateException>("Unmanaged objects don't support backlinks.") {
            child.parents
        }

        assertFailsWithMessage<IllegalStateException>("Unmanaged objects don't support backlinks.") {
            child.parentsBySet
        }

        assertFailsWithMessage<IllegalStateException>("Unmanaged objects don't support backlinks.") {
            child.parentsByList
        }
    }

    @Test
    fun managed_childrenDictionaryWithNullValues() {
        realm.writeBlocking {
            val child = this.copyToRealm(Child())

            val parents = (1..5).map {
                this.copyToRealm(Parent(it))
            }

            child.assertParents(0)

            parents.forEach { parent ->
                parent.childDictionary["A"] = child
                parent.childDictionary["B"] = null
            }

            // Putting a null object doesn't affect the backlink tally
            assertEquals(parents.size, child.parentsByDictionary.size)
        }
    }

    @Test
    fun managed_multipleChildren() {
        realm.writeBlocking {
            val child = this.copyToRealm(Child())

            val parents = (1..5).map {
                this.copyToRealm(Parent(it))
            }

            child.assertParents(0)

            parents.forEach { parent ->
                parent.child = child
                parent.childList.add(child)
                parent.childSet.add(child)
                parent.childDictionary["A"] = child
            }

            child.assertParents(parents.size)
        }
    }

    @Test
    fun managed_duplicateChildren() {
        realm.writeBlocking {
            val child = this.copyToRealm(Child())

            val parents = (1..5).map {
                this.copyToRealm(Parent(it))
            }

            child.assertParents(0)

            parents.forEach { parent ->
                parent.child = child
                parent.childList.add(child)
                parent.childList.add(child)
                parent.childSet.add(child)
                parent.childSet.add(child)

                // Dictionaries allow duplicate values as long as they have different keys
                parent.childDictionary["A"] = child
                parent.childDictionary["B"] = child
            }

            assertEquals(5, child.parents.size)
            assertEquals(10, child.parentsByList.size)
            assertEquals(5, child.parentsBySet.size)
            assertEquals(10, child.parentsByDictionary.size)
        }
    }

    @Test
    fun recursive() {
        val recursive = realm.writeBlocking {
            val recursive = this.copyToRealm(Recursive())
            recursive.recursiveField = recursive
            recursive
        }
        assertEquals(1, recursive.references.size)
        assertEquals(recursive.name, recursive.references[0].name)
    }

    @Test
    fun dynamic() {
        realm.writeBlocking {
            val child = this.copyToRealm(Child())

            this.copyToRealm(
                Parent().apply
                {
                    this.child = child
                    this.childSet.add(child)
                    this.childList.add(child)
                    this.childDictionary["A"] = child
                }
            )
        }

        realm.asDynamicRealm().query(Child::class.simpleName!!)
            .first()
            .find { dynamicObject ->
                assertNotNull(dynamicObject)
                assertEquals(1, dynamicObject.getBacklinks(Child::parents.name).size)
                assertEquals(1, dynamicObject.getBacklinks(Child::parentsByList.name).size)
                assertEquals(1, dynamicObject.getBacklinks(Child::parentsBySet.name).size)
                assertEquals(
                    1,
                    dynamicObject.getBacklinks(Child::parentsByDictionary.name).size
                )
            }
    }

    @Test
    fun dynamicMissingProperty_throws() {
        realm.writeBlocking {
            this.copyToRealm(Recursive())
        }
        realm.asDynamicRealm().let { dynamicRealm ->
            dynamicRealm.query("Recursive")
                .first()
                .find { child ->
                    assertNotNull(child)
                    assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Recursive' doesn't contain a property named 'missing'") {
                        child.getBacklinks("missing")
                    }
                }
        }
    }

    @Test
    fun dynamicWrongProperty_throws() {
        realm.writeBlocking {
            this.copyToRealm(Recursive())
        }
        realm.asDynamicRealm().let { dynamicRealm ->
            dynamicRealm.query("Recursive")
                .first()
                .find { child ->
                    assertNotNull(child)
                    assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'name' as an object reference but schema type is 'class io.realm.kotlin.types.RealmUUID'") {
                        child.getBacklinks("name")
                    }
                }
        }

        realm.asDynamicRealm().let { dynamicRealm ->
            dynamicRealm.query("Recursive")
                .first()
                .find { child ->
                    assertNotNull(child)
                    assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'uuidSet' as an object reference but schema type is 'RealmSet<class io.realm.kotlin.types.RealmUUID>'") {
                        child.getBacklinks("uuidSet")
                    }
                }
        }

        realm.asDynamicRealm().let { dynamicRealm ->
            dynamicRealm.query("Recursive")
                .first()
                .find { child ->
                    assertNotNull(child)
                    assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'uuidList' as an object reference but schema type is 'RealmList<class io.realm.kotlin.types.RealmUUID>'") {
                        child.getBacklinks("uuidList")
                    }
                }
        }

        realm.asDynamicRealm().let { dynamicRealm ->
            dynamicRealm.query("Recursive")
                .first()
                .find { child ->
                    assertNotNull(child)
                    assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'uuidDictionary' as an object reference but schema type is 'RealmDictionary<class io.realm.kotlin.types.RealmUUID>'") {
                        child.getBacklinks("uuidDictionary")
                    }
                }
        }
    }

    @Test
    fun classNotInSchema() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(setOf(Child::class))
                .directory(tmpDir)
                .build()

        assertFailsWithMessage<IllegalStateException>(
            """
                - Property 'Child.parents' of type 'linking objects' has unknown object type 'Parent'
                - Property 'Child.parentsByList' of type 'linking objects' has unknown object type 'Parent'
                - Property 'Child.parentsBySet' of type 'linking objects' has unknown object type 'Parent'
                - Property 'Child.parentsByDictionary' of type 'linking objects' has unknown object type 'Parent'
            """.trimIndent()
        ) {
            Realm.open(configuration)
        }
    }

    @Test
    fun fieldNotInClass() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(setOf(MissingSourceProperty::class))
                .directory(tmpDir)
                .build()

        assertFailsWithMessage<IllegalStateException>(
            "Property 'MissingSourceProperty.reference' declared as origin of linking objects property 'MissingSourceProperty.references' does not exist"
        ) {
            Realm.open(configuration)
        }
    }

    @Test
    fun query() {
        realm.writeBlocking {
            val child = this.copyToRealm(Child())

            val parents = Array(5) {
                this.copyToRealm(Parent(it))
            }

            child.assertParents(0)

            parents.forEach { parent ->
                parent.child = child
                parent.childList.add(child)
                parent.childSet.add(child)
                parent.childDictionary["A"] = child
            }

            child.assertParents(parents.size)
        }

        realm.query<Child>()
            .first()
            .find { child ->
                assertNotNull(child)
                assertEquals(2, child.parents.query("id > 2").find().size)
                assertEquals(2, child.parentsByList.query("id > 2").find().size)
                assertEquals(2, child.parentsBySet.query("id > 2").find().size)
                assertEquals(2, child.parentsByDictionary.query("id > 2").find().size)
            }
    }

    @Test
    fun deleteSourceObject() {
        val child = realm.writeBlocking {
            val child = this.copyToRealm(Child())

            val parents = (1..5).map {
                this.copyToRealm(Parent(it))
            }

            child.assertParents(0)

            parents.forEach { parent ->
                parent.child = child
                parent.childList.add(child)
                parent.childSet.add(child)
                parent.childDictionary["A"] = child
            }

            child.also {
                it.assertParents(parents.size)
            }
        }

        listOf(
            child.parents,
            child.parentsByList,
            child.parentsBySet,
            child.parentsByDictionary
        ).let { linkingObjects ->
            linkingObjects.forEach {
                assertEquals(5, it.size)
            }

            realm.writeBlocking {
                delete(findLatest(child)!!)
            }

            // Deleting the object should not affect as it happens in a different Realm version.
            linkingObjects.forEach {
                assertEquals(5, it.size)
            }
        }
    }

    @Test
    fun closingRealmInvalidatesBacklinks() {
        val child = realm.writeBlocking {
            val child = this.copyToRealm(Child())

            val parents = (1..5).map {
                this.copyToRealm(Parent(it))
            }

            child.assertParents(0)

            parents.forEach { parent ->
                parent.child = child
                parent.childList.add(child)
                parent.childSet.add(child)
                parent.childDictionary["A"] = child
            }

            child.also {
                it.assertParents(parents.size)
            }
        }

        listOf(
            child.parents,
            child.parentsByList,
            child.parentsBySet,
            child.parentsByDictionary
        ).let { linkingObjects ->
            linkingObjects.forEach {
                assertEquals(5, it.size)
            }

            realm.close()

            // Closing the Realm instance should make backlinks inaccessible
            linkingObjects.forEach {
                assertFailsWithMessage<IllegalStateException>("Access to invalidated Results objects") {
                    it.size
                }
            }
        }
    }

    @Test
    fun linkingFromEmbeddedObjects() {
        val parent = realm.writeBlocking {
            copyToRealm(
                Parent().also { parent ->
                    parent.embeddedChild = EmbeddedChild().apply {
                        this.parent = parent
                    }
                }
            )
        }

        assertEquals(1, parent.embeddedChildren.size)
        assertEquals(parent.embeddedChild!!.id, parent.embeddedChildren.first().id)
    }

    @Test
    fun linkingNull() {
        val parent = realm.writeBlocking { copyToRealm(Parent()) }

        assertFailsWithMessage<IllegalArgumentException>("Target property 'parents' not defined in 'Parent'.") {
            parent.backlinks(EmbeddedChild::parent).getValue(parent, Child::parents)
        }

        assertFailsWithMessage<IllegalArgumentException>("Target property 'embeddedChild' is not a backlink property.") {
            parent.backlinks(EmbeddedChild::parent).getValue(parent, Parent::embeddedChild)
        }

        assertFailsWithMessage<IllegalArgumentException>("Target property type 'EmbeddedChild' does not match backlink type 'Parent'.") {
            parent.backlinks(Parent::child).getValue(parent, Parent::embeddedChildren)
        }
    }

    @Test
    fun linkingObjects_namedQueries() {
        val parent = realm.writeBlocking {
            copyToRealm(
                Parent().also { parent ->
                    parent.child = Child()
                }
            )
        }

        assertEquals(
            1,
            realm.query<Child>("@links.Parent.child.id == $0", parent.id).count().find()
        )
    }

    @Test
    fun linkingObjects_namedQueriesRecursive() {
        val recursive = realm.writeBlocking {
            copyToRealm(
                Recursive().also { parent ->
                    parent.recursiveField = parent
                }
            )
        }

        assertEquals(
            1,
            realm.query<Recursive>("@links.Recursive.recursiveField.name == $0", recursive.name).count().find()
        )
    }

    @Test
    fun linkingEmbeddedObjects_unnamedLinkQueries() {
        val parent1 = realm.writeBlocking {
            copyToRealm(
                Parent(0).also { parent ->
                    parent.embeddedChild = EmbeddedChild()
                }
            )
        }

        val parent2 = realm.writeBlocking {
            copyToRealm(
                Parent2(1).also { parent ->
                    parent.embeddedChild = EmbeddedChild()
                }
            )
        }

        assertEquals(
            1,
            realm.query<EmbeddedChild>("@links.Parent.embeddedChild.id == $0", parent1.id).count().find()
        )
        assertEquals(
            0,
            realm.query<EmbeddedChild>("@links.Parent.embeddedChild.id == $0", parent2.id).count().find()
        )
        assertEquals(
            1,
            realm.query<EmbeddedChild>("@links.Parent2.embeddedChild.id == $0", parent2.id).count().find()
        )
        assertEquals(
            0,
            realm.query<EmbeddedChild>("@links.Parent2.embeddedChild.id == $0", parent1.id).count().find()
        )
    }

    @Test
    fun embeddedBacklinks() {
        val parent1 = realm.writeBlocking {
            copyToRealm(
                Parent(0).also { parent ->
                    parent.embeddedChild = EmbeddedChild()
                }
            )
        }

        val parent2 = realm.writeBlocking {
            copyToRealm(
                Parent2(1).also { parent ->
                    parent.embeddedChild = EmbeddedChild()
                }
            )
        }

        assertEquals(parent1.id, parent1.embeddedChild!!.parentViaBacklinks.id)
        assertEquals(parent2.id, parent2.embeddedChild!!.parent2ViaBacklinks.id)

        assertFailsWithMessage<IllegalStateException>("Backlink 'parentViaBacklinks' is not an instance of target property type 'Parent'.") {
            parent2.embeddedChild!!.parentViaBacklinks
        }

        assertFailsWithMessage<IllegalStateException>("Backlink 'parent2ViaBacklinks' is not an instance of target property type 'Parent2'.") {
            parent1.embeddedChild!!.parent2ViaBacklinks
        }
    }

    @Test
    fun linkingEmbeddedObjects_namedLinkQueries() {
        val parent1 = realm.writeBlocking {
            copyToRealm(
                Parent(0).also { parent ->
                    parent.embeddedChild = EmbeddedChild()
                }
            )
        }

        val parent2 = realm.writeBlocking {
            copyToRealm(
                Parent2(1).also { parent ->
                    parent.embeddedChild = EmbeddedChild()
                }
            )
        }

        assertEquals(
            1,
            realm.query<EmbeddedChild>("parentViaBacklinks.id == $0", parent1.id).count().find()
        )
        assertEquals(
            0,
            realm.query<EmbeddedChild>("parentViaBacklinks.id == $0", parent2.id).count().find()
        )
        assertEquals(
            1,
            realm.query<EmbeddedChild>("parent2ViaBacklinks.id == $0", parent2.id).count().find()
        )
        assertEquals(
            0,
            realm.query<EmbeddedChild>("parent2ViaBacklinks.id == $0", parent1.id).count().find()
        )
    }
}
