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
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.internal.asDynamicRealm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmSet
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class Parent : RealmObject {
    var child: Child? = null
    var childList: RealmList<Child> = realmListOf()
    var childSet: RealmSet<Child> = realmSetOf()
}

class Child : RealmObject {
    val parents by linkingObjects(Parent::child)
    val parentsByList by linkingObjects(Parent::childList)
    val parentsBySet by linkingObjects(Parent::childSet)
}

val Child.parentsOrNull: RealmResults<Parent>?
    get() = if (isManaged()) parents else null

class Recursive(var name: String) : RealmObject {
    constructor(): this("")
    var recursive: Recursive? = null
    val references by linkingObjects(Recursive::recursive)
}

class LinkingObjectsTests {
    private lateinit var realm: Realm
    private lateinit var tmpDir: String

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(setOf(Parent::class, Child::class, Recursive::class))
            .directory(tmpDir)
            .build()

        realm = Realm.open(configuration)
    }

    @Test
    fun unmanaged_throw() {
        val a = Child()

        assertFails {
            a.parents
        }

        assertNull(a.parentsOrNull)
    }

    @Test
    fun managed_works() {
        runBlocking {
            val child = realm.write {
                val parent = this.copyToRealm(Parent())
                val child = this.copyToRealm(Child())

                parent.child = child
                parent.childList.add(child)
                parent.childSet.add(child)

                child
            }
            assertEquals(1, child.parents.size)
            assertEquals(1, child.parentsByList.size)
            assertEquals(1, child.parentsBySet.size)
        }

        realm.asDynamicRealm().query(Child::class.simpleName!!).first().find()!!
            .let { dynamicObject ->
                assertEquals(1, dynamicObject.getLinkingObjects(Child::parents.name).size)
                assertEquals(1, dynamicObject.getLinkingObjects(Child::parentsByList.name).size)
                assertEquals(1, dynamicObject.getLinkingObjects(Child::parentsBySet.name).size)
            }
    }

    @Test
    fun recursive() {
        runBlocking {
            val recursive = realm.write {
                val recursive = this.copyToRealm(Recursive("hello world"))
                recursive.recursive = recursive
                recursive
            }
            assertEquals(1, recursive.references.size)
            assertEquals(recursive.name, recursive.references[0].name)
        }
    }

    @Test
    fun dynamic() {
        runBlocking {
            val recursive = realm.write {
                val recursive = this.copyToRealm(Recursive("hello world"))
                recursive.recursive = recursive
                recursive
            }
            assertEquals(1, recursive.references.size)
            assertEquals(recursive.name, recursive.references[0].name)
            realm.asDynamicRealm().let { dynamicRealm ->
                val child = dynamicRealm.query("Recursive").first().find()!!
                assertFailsWith<IllegalArgumentException> {
                    child.getLinkingObjects("missing")
                }
                assertEquals(1, child.getLinkingObjects("references").size)
            }
        }
    }
}
