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
import io.realm.RealmObject
import io.realm.isManaged
import io.realm.util.PlatformUtils
import io.realm.util.TypeDescriptor.classifiers
import io.realm.util.Utils.createRandomString
import test.Sample
import test.link.Child
import test.link.Parent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration(path = "$tmpDir/${createRandomString(16)}.realm", schema = setOf(Parent::class, Child::class, Sample::class))
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun importPrimitiveDefaults() {
        realm.writeBlocking { copyToRealm(Sample()) }
        val managed = realm.objects(Sample::class)[0]

        // TODO Find a way to ensure that our Sample covers all types. This isn't doable right now
        //  without polluting test project configuration with cinterop dependency. Some of the
        //  internals moves around in https://github.com/realm/realm-kotlin/pull/148, so maybe
        //  possible by just peeking into the Sample.`$realm$fields` with
        //  @Suppress("invisible_reference", "invisible_member") but maybe worth to move such test
        //  requiring internals into a separate module.
        for (type in classifiers.keys) {
            when (type) {
                String::class -> assertEquals("Realm", managed.stringField)
                Byte::class -> assertEquals(0xa, managed.byteField)
                Char::class -> assertEquals('a', managed.charField)
                Short::class -> assertEquals(17, managed.shortField)
                Int::class -> assertEquals(42, managed.intField)
                Long::class -> assertEquals(256, managed.longField)
                Boolean::class -> assertEquals(true, managed.booleanField)
                Float::class -> assertEquals(3.14f, managed.floatField)
                Double::class -> assertEquals(1.19840122, managed.doubleField)
                RealmObject::class -> assertEquals(null, managed.child)
                else -> error("Untested type: $type")
            }
        }
    }

    @Test
    fun importUnmanagedHierarchy() {
        val v1 = "Hello"

        val child = Child().apply { name = v1 }
        val parent = Parent().apply { this.child = child }
        val clone = realm.writeBlocking { copyToRealm(parent) }

        assertNotNull(clone)
        assertNotNull(clone.child)
        assertEquals(v1, clone.child?.name)
    }

    @Test
    fun importUnmanagedCyclicHierarchy() {
        val v1 = "Hello"
        val selfReferencingSample = Sample().apply {
            stringField = v1
            child = this
        }
        val root = Sample().apply { child = selfReferencingSample }
        val clone = realm.writeBlocking { copyToRealm(root) }

        assertNotNull(clone)
        assertEquals(2, realm.objects(Sample::class).count())
        val child = clone.child
        assertNotNull(child)
        assertNotNull(child.stringField)
        assertEquals(v1, child.stringField)
        // Verifying the self/cyclic reference by validating that the child (self reference) has
        // the same stringField value as the object. This will be safest verified when we have
        // support for primary keys (https://github.com/realm/realm-kotlin/issues/122)
        assertEquals(child.stringField, child.child?.stringField)
        // Just another level down to see that we are going in cycles.
        val child2 = child.child!!
        assertEquals(child2.stringField, child2.child?.stringField)
    }

    @Test
    fun updateImportedHierarchy() {
        val v1 = "Hello"
        val v2 = "UPDATE"

        val child = Child().apply { name = v1 }

        val clone = realm.writeBlocking {
            copyToRealm(child).apply { name = v2 }
        }

        assertNotNull(clone)
        assertEquals(v2, clone.name)
    }

    @Test
    fun importByAssignmentToManaged() {
        val v1 = "NEWNAME"
        val v2 = "ASDF"
        val v3 = "FD"

        val managedChild = realm.writeBlocking {
            val parent = create(Parent::class)

            val unmanaged = Child()
            unmanaged.name = v1
            assertEquals(v1, unmanaged.name)

            assertNull(parent.child)
            parent.child = unmanaged
            assertNotNull(parent.child)
            val managedChild = parent.child
            assertNotNull(managedChild)

            // Verify that properties have been migrated
            assertEquals(v1, parent.child!!.name)

            // Verify that changes to original object does not affect managed clone
            unmanaged.name = v2
            assertEquals(v2, unmanaged.name)
            assertEquals(v1, parent.child!!.name)

            // Verify that we can update the clone
            managedChild.name = v3
            assertEquals(v3, parent.child!!.name)
            managedChild
        }

        // Verify that we cannot update the managed clone outside a transaction (it is in fact managed)
        assertTrue(managedChild.isManaged())
        assertFailsWith<RuntimeException> {
            managedChild.name = "bar"
        }
    }

    @Test
    fun importMixedManagedAndUnmanagedHierarchy() {
        val v1 = "Managed"
        val v2 = "Initially unmanaged object"

        val managed = realm.writeBlocking {
            create<Sample>().apply { stringField = v1 }
        }
        assertEquals(1, realm.objects(Sample::class).count())

        val unmanaged = Sample().apply {
            stringField = v2
            child = managed
        }

        val importedRoot = realm.writeBlocking {
            copyToRealm(unmanaged)
        }

        assertEquals(2, realm.objects(Sample::class).count())
        assertEquals(v2, importedRoot.stringField)
        assertEquals(v1, importedRoot.child?.stringField)
    }

    @Test
    fun importAlreadyManagedIsNoop() {
        realm.writeBlocking {
            val sample = copyToRealm(Sample())
            copyToRealm(sample)
        }

        assertEquals(1, realm.objects(Sample::class).count())
    }
}
