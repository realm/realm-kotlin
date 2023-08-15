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
package io.realm.kotlin.test.common

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.VersionId
import io.realm.kotlin.entities.SampleWithPrimaryKey
import io.realm.kotlin.entities.link.Child
import io.realm.kotlin.entities.link.Parent
import io.realm.kotlin.ext.isFrozen
import io.realm.kotlin.ext.isValid
import io.realm.kotlin.ext.version
import io.realm.kotlin.test.common.utils.RealmStateTest
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// Model class with toString/equals/hashCode
class CustomMethods : RealmObject {
    var name: String = ""
    var age: Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as CustomMethods
        return this.age == 42 && other.age == 42
    }

    override fun hashCode(): Int {
        return if (isValid()) {
            42
        } else {
            -1
        }
    }

    override fun toString(): String {
        return "customToString"
    }
}

class RealmObjectTests : RealmStateTest {

    companion object {
        // Expected version after writing Parent to Realm
        private val EXPECTED_VERSION = VersionId(3)
    }

    private lateinit var tmpDir: String
    private lateinit var realm: Realm
    private lateinit var parent: Parent

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class, SampleWithPrimaryKey::class, CustomMethods::class))
            .directory(tmpDir)
            .build()
        realm = Realm.open(configuration)
        parent = realm.writeBlocking { copyToRealm(Parent()) }
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    override fun version() {
        assertEquals(EXPECTED_VERSION, parent.version())
    }

    override fun version_throwsOnUnmanagedObject() {
        val unmanagedParent = Parent()
        assertFailsWith<IllegalArgumentException> {
            unmanagedParent.version()
        }
    }

    @Test
    override fun version_throwsIfRealmIsClosed() {
        realm.close()
        assertFailsWith<IllegalStateException> { parent.version() }
    }

    @Test
    fun isValid() {
        val unmanagedParent = Parent()
        assertTrue(unmanagedParent.isValid())
        val obj: Parent = realm.writeBlocking { copyToRealm(unmanagedParent) }
        assertTrue(obj.isValid())
        realm.close()
        assertFalse(obj.isValid())
    }

    @Test
    override fun isFrozen() {
        assertTrue { parent.isFrozen() }
        realm.writeBlocking {
            val parent = copyToRealm(Parent())
            assertFalse { parent.isFrozen() }
        }
    }

    @Test
    override fun isFrozen_throwsOnUnmanagedObject() {
        val unmanagedParent = Parent()
        assertFailsWith<IllegalArgumentException> {
            unmanagedParent.isFrozen()
        }
    }

    @Test
    fun observeWhenObjectIsDeleted() {
        // FIXME
    }

    @Test
    fun toString_managed() {
        val managedObj = realm.writeBlocking {
            copyToRealm(Parent())
        }
        val regex = Regex("io.realm.kotlin.entities.link.Parent\\{state=VALID, schemaName=Parent, objKey=[0-9]*, version=[0-9]*, realm=${realm.configuration.name}\\}")
        assertTrue(regex.matches(managedObj.toString()), managedObj.toString())
    }

    @Test
    fun toString_managed_cyclicData() {
        val p1 = SampleWithPrimaryKey()
        p1.stringField = "Parent"
        p1.nullableObject = p1
        val managedObj = realm.writeBlocking { copyToRealm(p1) }
        val regex = Regex("io.realm.kotlin.entities.SampleWithPrimaryKey\\{state=VALID, schemaName=SampleWithPrimaryKey, objKey=[0-9]*, version=[0-9]*, realm=${realm.configuration.name}\\}")
        assertTrue(regex.matches(managedObj.toString()), managedObj.toString())
    }

    @Test
    fun toString_managed_invalid() {
        realm.writeBlocking {
            val managedObject = copyToRealm(Parent())
            delete(managedObject)
            val regex = Regex("io.realm.kotlin.entities.link.Parent\\{state=INVALID, schemaName=Parent, realm=${realm.configuration.name}, hashCode=[-0-9]*\\}")
            assertTrue(regex.matches(managedObject.toString()), managedObject.toString())
            cancelWrite()
        }
    }

    @Test
    fun toString_managed_closedRealm() {
        val managedObject = realm.writeBlocking {
            copyToRealm(Parent())
        }
        realm.close()
        val regex = Regex("io.realm.kotlin.entities.link.Parent\\{state=CLOSED, schemaName=Parent, realm=${realm.configuration.name}, hashCode=[-0-9]*\\}")
        assertTrue(regex.matches(managedObject.toString()), managedObject.toString())
    }

    @Test
    fun toString_customMethod() {
        assertEquals("customToString", CustomMethods().toString())
        val managedObj = realm.writeBlocking { copyToRealm(CustomMethods()) }
        assertEquals("customToString", managedObj.toString())
    }

    @Test
    fun toString_unmanaged() {
        val unmanagedObject = Parent()
        val regex = Regex("io.realm.kotlin.entities.link.Parent\\{state=UNMANAGED, schemaName=Parent, hashCode=[-0-9]*\\}")
        assertTrue(regex.matches(unmanagedObject.toString()), unmanagedObject.toString())
    }

    @Test
    fun equals_hashCode_managed() {
        realm.writeBlocking {
            val p1 = copyToRealm(Parent().apply { this.name = "Jane" })
            val p2 = copyToRealm(Parent())
            val p3 = query(Parent::class, "name = 'Jane'").first().find()!!
            assertEquals(p1, p1)
            assertEquals(p1, p3)
            assertEquals(p1.hashCode(), p1.hashCode())
            assertEquals(p1.hashCode(), p3.hashCode())

            // Not restrictions are given on hashCode if two objects are not equal
            assertNotEquals(p2, p1)
            assertNotEquals(p2, p3)
        }
    }

    @Test
    fun equals_hashCode_unmanaged() {
        val p1 = Parent()
        val p2 = Parent()
        assertEquals(p1, p1)
        assertEquals(p1.hashCode(), p1.hashCode())
        assertNotEquals(p1, p2)
    }

    @Test
    fun equals_hashCode_mixed() {
        val unmanagedObj = Parent()
        val managedObj = realm.writeBlocking { copyToRealm(Parent()) }
        assertNotEquals(unmanagedObj, managedObj)
        assertNotEquals(managedObj, unmanagedObj)
        // When objects are not equal, no guarantees are given on the behavior of hashCode()
        // thus nothing can be asserted here.
    }

    @Test
    fun equals_hashCode_managed_cyclicData() {
        realm.writeBlocking {
            val p1 = copyToRealm(
                SampleWithPrimaryKey().apply {
                    primaryKey = 1
                    stringField = "Jane"
                }
            )
            p1.nullableObject = p1
            val p2 = copyToRealm(
                SampleWithPrimaryKey().apply {
                    primaryKey = 2
                    stringField = "John"
                }
            )
            val p3 = query(SampleWithPrimaryKey::class, "stringField = 'Jane'").first().find()!!
            assertEquals(p1, p1)
            assertEquals(p1, p3)
            assertEquals(p1.hashCode(), p1.hashCode())
            assertEquals(p1.hashCode(), p3.hashCode())

            // Not restrictions are given on hashCode if two objects are not equal
            assertNotEquals(p2, p1)
            assertNotEquals(p2, p3)
        }
    }

    @Test
    fun equals_hashCode_customMethod() {
        // Only equals if age = 42 or same instance
        val obj1 = CustomMethods()
        val obj2 = CustomMethods()
        assertEquals(obj1, obj1)
        assertEquals(obj1.hashCode(), obj1.hashCode())
        assertEquals(42, obj1.hashCode())
        assertNotEquals(obj1, obj2)

        val obj3 = CustomMethods().apply { age = 42 }
        val obj4 = CustomMethods().apply { age = 42 }
        assertEquals(obj3, obj3)
        assertEquals(obj3.hashCode(), obj4.hashCode())
        assertEquals(42, obj3.hashCode())
        assertEquals(obj3, obj4)
        assertEquals(obj3.hashCode(), obj4.hashCode())

        // Managed objects
        realm.writeBlocking {
            val obj1 = copyToRealm(CustomMethods())
            val obj2 = copyToRealm(CustomMethods())
            assertEquals(obj1, obj1)
            assertEquals(obj1.hashCode(), obj1.hashCode())
            assertEquals(42, obj1.hashCode())
            assertNotEquals(obj1, obj2)

            val obj3 = copyToRealm(CustomMethods().apply { age = 42 })
            val obj4 = copyToRealm(CustomMethods().apply { age = 42 })
            assertEquals(obj3, obj3)
            assertEquals(obj3.hashCode(), obj3.hashCode())
            assertEquals(42, obj1.hashCode())
            assertEquals(obj3, obj4)
            assertEquals(obj3.hashCode(), obj4.hashCode())
        }
    }

    @Test
    fun equals_hashCode_managed_invalid() {
        realm.writeBlocking<Unit> {
            val p1 = copyToRealm(Parent().apply { this.name = "Jane" })
            val p2 = copyToRealm(Parent())
            delete(p1)
            delete(p2)

            assertEquals(p1, p1)
            assertEquals(p1.hashCode(), p1.hashCode())
            assertNotEquals(p1, p2)
        }
    }

    override fun isFrozen_throwsIfRealmIsClosed() {
        realm.close()
        assertFailsWith<IllegalStateException> {
            parent.isFrozen()
        }
    }

    // FIXME RealmObject doesn't actually implement RealmState yet
    @Ignore
    override fun isClosed() {
        TODO("Not yet implemented")
    }
}
