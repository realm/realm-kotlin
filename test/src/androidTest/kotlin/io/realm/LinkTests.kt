/*
 * Copyright 2021 Realm Inc.
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

package io.realm

import io.realm.runtimeapi.RealmModule
import test.link.Child
import test.link.Parent
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LinkTests {

    @RealmModule(Parent::class, Child::class)
    class MySchema

    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        val configuration = RealmConfiguration.Builder(schema = MySchema()).build()
        realm = Realm.open(configuration)
        // FIXME Cleaning up realm to overcome lack of support for deleting actual files
        //  https://github.com/realm/realm-kotlin/issues/95
        realm.beginTransaction()
        realm.objects(Parent::class).delete()
        realm.commitTransaction()
        assertEquals(0, realm.objects(Parent::class).size, "Realm is not empty")
    }

    @Test
    fun basics() {
        val name = "Realm"
        realm.beginTransaction()
        val parent = realm.create(Parent::class)
        val child = realm.create(Child::class)
        child.name = name

        assertNull(parent.child)
        parent.child = child
        assertNotNull(parent.child)

        realm.commitTransaction()

        assertEquals(1, realm.objects(Parent::class).size)

        val child1 = realm.objects(Parent::class)[0].child
        assertEquals(name, child1?.name)

        realm.beginTransaction()
        assertNotNull(parent.child)
        parent.child = null
        assertNull(parent.child)
        realm.commitTransaction()

        assertNull(realm.objects(Parent::class)[0].child)
    }
}
