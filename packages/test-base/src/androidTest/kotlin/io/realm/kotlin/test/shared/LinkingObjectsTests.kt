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

package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.linkingObjects
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.internal.asDynamicRealm
import io.realm.kotlin.notifications.InitialResults
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.annotations.Ignore
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Parent(var id: Int) : RealmObject {
    constructor() : this(0)

    var child: Child? = null
    var childList: RealmList<Child> = realmListOf()
    var childSet: RealmSet<Child> = realmSetOf()
}

class Child : RealmObject {
    val parents by linkingObjects(Parent::child)
    val parentsByList by linkingObjects(Parent::childList)
    val parentsBySet by linkingObjects(Parent::childSet)
}

class Recursive : RealmObject {
    var name: RealmUUID = RealmUUID.random()

    var recursiveField: Recursive? = null
    val references by linkingObjects(Recursive::recursiveField)
}

class MissingSourceProperty : RealmObject {
    @Ignore
    var reference: MissingSourceProperty? = null
    val references by linkingObjects(MissingSourceProperty::reference)
}

class LinkingObjectsTests {
    private lateinit var realm: Realm
    private lateinit var tmpDir: String

    private fun Child.assertParents(expectedSize: Int) {
        assertEquals(expectedSize, parents.size)
        assertEquals(expectedSize, parentsByList.size)
        assertEquals(expectedSize, parentsBySet.size)
    }

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(setOf(Parent::class, Child::class, Recursive::class))
                .directory(tmpDir)
                .build()

        realm = Realm.open(configuration)
    }

    @Test
    fun unmanaged_throws() {
        val child = Child()
        val parent = Parent()

        parent.child = child
        parent.childSet = realmSetOf(child)
        parent.childList = realmListOf(child)

        assertFailsWith<IllegalStateException> {
            child.parents
        }

        assertFailsWith<IllegalStateException> {
            child.parentsBySet
        }

        assertFailsWith<IllegalStateException> {
            child.parentsByList
        }
    }

    // TODO not part of the final test suite, its is here to validate that the users can write their own code
    private val Child.parentsOrNull: RealmResults<Parent>?
        get() = if (isManaged()) parents else null

    @Test
    fun validateExtensionMethod() {
        val child = Child()
        assertNull(child.parentsOrNull)
    }

    @Test
    fun managed_multipleChildren() {
        runBlocking {
            realm.write {
                val child = this.copyToRealm(Child())

                val parents = Array(5) {
                    this.copyToRealm(Parent(it))
                }

                child.assertParents(0)

                parents.forEach { parent ->
                    parent.child = child
                    parent.childList.add(child)
                    parent.childSet.add(child)
                }

                child.assertParents(parents.size)
            }
        }
    }

    @Test
    fun managed_duplicateChildren() {
        runBlocking {
            realm.write {
                val child = this.copyToRealm(Child())

                val parents = Array(5) {
                    this.copyToRealm(Parent(it))
                }

                child.assertParents(0)

                parents.forEach { parent ->
                    parent.child = child
                    parent.childList.add(child)
                    parent.childList.add(child)
                    parent.childSet.add(child)
                    parent.childSet.add(child)
                }

                assertEquals(5, child.parents.size)
                assertEquals(10, child.parentsByList.size)
                assertEquals(5, child.parentsBySet.size)
            }
        }
    }

    @Test
    fun recursive() {
        runBlocking {
            val recursive = realm.write {
                val recursive = this.copyToRealm(Recursive())
                recursive.recursiveField = recursive
                recursive
            }
            assertEquals(1, recursive.references.size)
            assertEquals(recursive.name, recursive.references[0].name)
        }
    }

    // Dynamic tests
    @Test
    fun dynamic() {
        runBlocking {
            realm.write {
                val child = this.copyToRealm(Child())

                this.copyToRealm(
                    Parent().apply
                    {
                        this.child = child
                        this.childSet.add(child)
                        this.childList.add(child)
                    }
                )
            }

            realm.asDynamicRealm().query(Child::class.simpleName!!).first().find()!!
                .let { dynamicObject ->
                    assertEquals(1, dynamicObject.getLinkingObjects(Child::parents.name).size)
                    assertEquals(1, dynamicObject.getLinkingObjects(Child::parentsByList.name).size)
                    assertEquals(1, dynamicObject.getLinkingObjects(Child::parentsBySet.name).size)
                }
        }
    }

    @Test
    fun dynamicMissingProperty_throws() {
        runBlocking {
            realm.write {
                this.copyToRealm(Recursive())
            }
            realm.asDynamicRealm().let { dynamicRealm ->
                val child = dynamicRealm.query("Recursive").first().find()!!
                assertFailsWith<IllegalArgumentException> {
                    child.getLinkingObjects("missing")
                }
            }
        }
    }

    // Missing stuff
    @Test
    fun classNotInSchema() {
        runBlocking {
            tmpDir = PlatformUtils.createTempDir()
            val configuration =
                RealmConfiguration.Builder(setOf(Child::class))
                    .directory(tmpDir)
                    .build()

            assertFailsWith<IllegalStateException> {
                Realm.open(configuration)
            }
        }
    }

    @Test
    fun fieldNotInClass() {
        runBlocking {
            tmpDir = PlatformUtils.createTempDir()
            val configuration =
                RealmConfiguration.Builder(setOf(MissingSourceProperty::class))
                    .directory(tmpDir)
                    .build()

            assertFailsWith<IllegalStateException> {
                Realm.open(configuration)
            }
        }
    }

    @Test
    fun migration(): Unit = TODO()

    // Notification smoke tests
    @Test
    fun notifications_source_object() {
        runBlocking {
            val child = realm.write {
                copyToRealm(Child())
            }

            val c = Channel<ResultsChange<Parent>?>(1)
            val observer = async {
                realm.query<Child>()
                    .first()
                    .find()!!
                    .parents
                    .asFlow()
                    .onCompletion {
                        c.trySend(null)
                    }
                    .collect {
                        c.trySend(it)
                    }
            }

            c.receive()!!.let { resultsChange ->
                assertIs<InitialResults<*>>(resultsChange)
                assertTrue(resultsChange.list.isEmpty())
            }

            realm.write {
                repeat(5) {
                    copyToRealm(Parent(it)).apply {
                        this.child = findLatest(child)
                    }
                }
            }

            c.receive()!!.let { resultsChange ->
                assertIs<UpdatedResults<*>>(resultsChange)
                assertEquals(5, resultsChange.list.size)
            }

            // Validate the flow completes once the target object gets deleted
            realm.write {
                delete(findLatest(child)!!)
            }

            assertNull(c.receive())

            observer.cancel()
            c.close()
        }
    }

    @Test
    fun notifications_source_list() {
        runBlocking {
            val child = realm.write {
                copyToRealm(Child())
            }

            val c = Channel<ResultsChange<Parent>?>(1)
            val observer = async {
                realm.query<Child>()
                    .first()
                    .find()!!
                    .parentsByList
                    .asFlow()
                    .onCompletion {
                        c.trySend(null)
                    }
                    .collect {
                        c.trySend(it)
                    }
            }

            c.receive()!!.let { resultsChange ->
                assertIs<InitialResults<*>>(resultsChange)
                assertTrue(resultsChange.list.isEmpty())
            }

            realm.write {
                repeat(5) {
                    copyToRealm(Parent(it)).apply {
                        this.childList.add(findLatest(child)!!)
                    }
                }
            }

            c.receive()!!.let { resultsChange ->
                assertIs<UpdatedResults<*>>(resultsChange)
                assertEquals(5, resultsChange.list.size)
            }

            // Validate the flow completes once the target object gets deleted
            realm.write {
                delete(findLatest(child)!!)
            }

            assertNull(c.receive())

            observer.cancel()
            c.close()
        }
    }

    @Test
    fun notifications_source_set() {
        runBlocking {
            val child = realm.write {
                copyToRealm(Child())
            }

            val c = Channel<ResultsChange<Parent>?>(1)
            val observer = async {
                realm.query<Child>()
                    .first()
                    .find()!!
                    .parentsBySet
                    .asFlow()
                    .onCompletion {
                        c.trySend(null)
                    }
                    .collect {
                        c.trySend(it)
                    }
            }

            c.receive()!!.let { resultsChange ->
                assertIs<InitialResults<*>>(resultsChange)
                assertTrue(resultsChange.list.isEmpty())
            }

            realm.write {
                repeat(5) {
                    copyToRealm(Parent(it)).apply {
                        this.childSet.add(findLatest(child)!!)
                    }
                }
            }

            c.receive()!!.let { resultsChange ->
                assertIs<UpdatedResults<*>>(resultsChange)
                assertEquals(5, resultsChange.list.size)
            }

            // Validate the flow completes once the target object gets deleted
            realm.write {
                delete(findLatest(child)!!)
            }

            assertNull(c.receive())

            observer.cancel()
            c.close()
        }
    }

    // queries smoke tests
    @Test
    fun queries() {
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
            }

            child.assertParents(parents.size)
        }

        realm.query<Child>().first().find()!!.let { child ->
            assertEquals(2, child.parents.query("id > 2").find().size)
            assertEquals(2, child.parentsByList.query("id > 2").find().size)
            assertEquals(2, child.parentsBySet.query("id > 2").find().size)
        }
    }
}
