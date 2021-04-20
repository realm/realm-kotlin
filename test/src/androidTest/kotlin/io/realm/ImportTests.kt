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

import io.realm.util.TestRealmFieldTypes
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

class ImportTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = Utils.createTempDir()
        val configuration =
            RealmConfiguration(path = "$tmpDir/default.realm", schema = setOf(Parent::class, Child::class, Sample::class))
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        Utils.deleteTempDir(tmpDir)
    }

    @Test
    fun importPrimitiveDefaults() {
        val sample = Sample()
        realm.beginTransaction()
        realm.copyToRealm(sample)
        realm.commitTransaction()

        val managed = realm.objects(Sample::class)[0]

        // TODO Find a way to ensure that our Sample covers all types. This isn't doable right now
        //  without polluting test project configuration with cinterop dependency. Some of the
        //  internals moves around in https://github.com/realm/realm-kotlin/pull/148, so maybe
        //  possible by just peeking into the Sample.`$realm$fields` with
        //  @Suppress("invisible_reference", "invisible_member") but maybe worth to move such test
        //  requiring internals into a separate module.
        for (value in TestRealmFieldTypes.values()) {
            when (value) {
                TestRealmFieldTypes.BYTE -> assertEquals(0xa, managed.byteField)
                TestRealmFieldTypes.CHAR -> assertEquals('a', managed.charField)
                TestRealmFieldTypes.SHORT -> assertEquals(17, managed.shortField)
                TestRealmFieldTypes.INT -> assertEquals(42, managed.intField)
                TestRealmFieldTypes.LONG -> assertEquals(256, managed.longField)
                TestRealmFieldTypes.BOOLEAN -> assertEquals(true, managed.booleanField)
                TestRealmFieldTypes.FLOAT -> assertEquals(3.14f, managed.floatField)
                TestRealmFieldTypes.DOUBLE -> assertEquals(1.19840122, managed.doubleField)
                TestRealmFieldTypes.LINK -> assertEquals(null, managed.child)
                else -> error("Untested type: $value")
            }
        }
    }

    @Test
    fun importUnmanagedHierarchy() {
        val v1 = "Hello"

        val child = Child().apply { name = v1 }
        val parent = Parent().apply { this.child = child }

        realm.beginTransaction()
        val clone = realm.copyToRealm(parent)
        realm.commitTransaction()

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

        realm.beginTransaction()
        val clone = realm.copyToRealm(root)
        realm.commitTransaction()

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

        realm.beginTransaction()
        val clone = realm.copyToRealm(child)
        clone.name = v2
        realm.commitTransaction()

        assertNotNull(clone)
        assertEquals(v2, clone.name)
    }

    @Test
    fun importByAssignmentToManaged() {
        val v1 = "NEWNAME"
        val v2 = "ASDF"
        val v3 = "FD"

        realm.beginTransaction()
        val parent = realm.create(Parent::class)

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
        realm.commitTransaction()

        // Verify that we cannot update the managed clone outside a transaction (it is in fact managed)
        assertFailsWith<RuntimeException> {
            managedChild.name = v3
        }
    }

    @Test
    fun importMixedManagedAndUnmanagedHierarchy() {
        val v1 = "Managed"
        val v2 = "Initially unmanaged object"

        realm.beginTransaction()
        val managed = realm.create<Sample>().apply { stringField = v1 }
        realm.commitTransaction()

        assertEquals(1, realm.objects(Sample::class).count())

        val unmanaged = Sample().apply {
            stringField = v2
            child = managed
        }

        realm.beginTransaction()
        val importedRoot = realm.copyToRealm(unmanaged)
        realm.commitTransaction()

        assertEquals(2, realm.objects(Sample::class).count())
        assertEquals(v2, importedRoot.stringField)
        assertEquals(v1, importedRoot.child?.stringField)
    }

    @Test
    fun importAlreadyManagedIsNoop() {
        val v1 = "Managed"

        realm.beginTransaction()
        var sample = Sample()
        sample = realm.copyToRealm(sample)
        sample = realm.copyToRealm(sample)
        sample = realm.copyToRealm(sample)
        sample = realm.copyToRealm(sample)
        realm.commitTransaction()

        assertEquals(1, realm.objects(Sample::class).count())
    }
}
