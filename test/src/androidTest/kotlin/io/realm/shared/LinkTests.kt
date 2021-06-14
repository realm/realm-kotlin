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
package io.realm.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.util.PlatformUtils
import test.link.Child
import test.link.Parent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LinkTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration(path = "$tmpDir/default.realm", schema = setOf(Parent::class, Child::class))
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun basics() {
        val name = "Realm"
        val parent = realm.writeBlocking {
            val parent = create(Parent::class)
            val child = create(Child::class)
            child.name = name

            assertNull(parent.child)
            parent.child = child
            assertNotNull(parent.child)
            parent
        }

        assertEquals(1, realm.objects(Parent::class).size)

        val child1 = realm.objects(Parent::class).first().child
        assertEquals(name, child1?.name)

        realm.writeBlocking {
            val parent = objects<Parent>().first()
            assertNotNull(parent.child)
            parent.child = null
            assertNull(parent.child)
        }

        assertNull(realm.objects(Parent::class)[0].child)
    }
}
