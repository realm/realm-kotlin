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
import io.realm.kotlin.ext.linkingObjects
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class Parent : RealmObject {
    var child: Child? = null
}

class Child : RealmObject {
    val parents by linkingObjects(Parent::child)
}

class LinkingObjectsTests {
    private lateinit var realm: Realm
    private lateinit var tmpDir: String

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(setOf(Parent::class, Child::class))
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
    }

    @Test
    fun managed_works() {
        runBlocking {
            val child = realm.write {
                val parent = this.copyToRealm(Parent())
                val child = this.copyToRealm(Child())
                parent.child = child
                child
            }
            assertEquals(1, child.parents.size)
        }
    }
}
