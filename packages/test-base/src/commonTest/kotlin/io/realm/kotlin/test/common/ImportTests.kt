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
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.link.Child
import io.realm.kotlin.entities.link.Parent
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.test.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TypeDescriptor.classifiers
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
            RealmConfiguration.Builder(schema = setOf(Parent::class, Child::class, Sample::class))
                .directory(tmpDir)
                .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    @Suppress("ComplexMethod")
    fun importPrimitiveDefaults() {
        realm.writeBlocking { copyToRealm(Sample()) }
        val managed = realm.query(Sample::class).find()[0]

        // TODO Find a way to ensure that our Sample covers all types. This isn't doable right now
        //  without polluting test project configuration with cinterop dependency. Some of the
        //  internals moves around in https://github.com/realm/realm-kotlin/pull/148, so maybe
        //  possible by just peeking into the Sample.`io_realm_kotlin_fields` with
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
                Decimal128::class -> assertEquals(Decimal128("1.8446744073709551618E-6157"), managed.decimal128Field)
                RealmInstant::class -> assertEquals(RealmInstant.from(100, 1000), managed.timestampField)
                BsonObjectId::class -> assertEquals(BsonObjectId("507f1f77bcf86cd799439011"), managed.bsonObjectIdField)
                RealmUUID::class -> assertEquals(RealmUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d76"), managed.uuidField)
                RealmObject::class -> assertEquals(null, managed.nullableObject)
                ByteArray::class -> assertContentEquals(byteArrayOf(42), managed.binaryField)
                MutableRealmInt::class -> assertEquals(MutableRealmInt.create(42), managed.mutableRealmIntField)
                RealmAny::class -> assertEquals(null, managed.nullableRealmAnyField)
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
            nullableObject = this
        }
        val root = Sample().apply { nullableObject = selfReferencingSample }
        val clone = realm.writeBlocking { copyToRealm(root) }

        assertNotNull(clone)
        val query = realm.query(Sample::class)
        assertEquals(2L, query.count().find())
        assertEquals(2, query.find().size)
        val child = clone.nullableObject
        assertNotNull(child)
        assertNotNull(child.stringField)
        assertEquals(v1, child.stringField)
        // Verifying the self/cyclic reference by validating that the child (self reference) has
        // the same stringField value as the object. This will be safest verified when we have
        // support for primary keys (https://github.com/realm/realm-kotlin/issues/122)
        assertEquals(child.stringField, child.nullableObject?.stringField)
        // Just another level down to see that we are going in cycles.
        val child2 = child.nullableObject!!
        assertEquals(child2.stringField, child2.nullableObject?.stringField)
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
            val parent = copyToRealm(Parent())

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
        assertFailsWith<IllegalStateException> {
            managedChild.name = "bar"
        }
    }

    @Test
    fun importOutdatedReferenceThrows() {
        val v1 = "Managed"
        val v2 = "Initially unmanaged object"

        val managed = realm.writeBlocking {
            copyToRealm(Sample()).apply { stringField = v1 }
        }
        assertEquals(1L, realm.query(Sample::class).count().find())

        val unmanagedRootWithReferenceToOldManagedObject = Sample().apply {
            stringField = v2
            nullableObject = managed
        }

        val importedRoot = realm.writeBlocking {
            assertFailsWithMessage<IllegalArgumentException>("Cannot import an outdated object") {
                copyToRealm(unmanagedRootWithReferenceToOldManagedObject)
            }
        }
    }

    @Test
    fun importAlreadyManagedIsNoop() {
        realm.writeBlocking {
            val sample = copyToRealm(Sample())
            copyToRealm(sample)
        }

        assertEquals(1L, realm.query(Sample::class).count().find())
    }

    @Test
    fun importRealmAnyWithUnmanagedObject() {
        val unmanagedObject = Sample().apply { stringField = "INNER" }
        val realmAny = RealmAny.create(unmanagedObject)
        val managedObject = realm.writeBlocking {
            val container = Sample().apply {
                stringField = "OUTER"
                nullableRealmAnyField = realmAny
            }
            copyToRealm(container)
        }

        // Now we should have two Sample objects: the container and the INNER
        assertEquals(2, realm.query<Sample>().count().find())
        val expected = unmanagedObject.stringField
        val actual = managedObject.nullableRealmAnyField
            ?.asRealmObject<Sample>()
            ?.stringField
        assertEquals(expected, actual)
    }

    @Test
    fun importRealmAnyToManagedObjectWithUnmanagedObject() {
        val unmanagedObject = Sample().apply { stringField = "INNER" }
        val realmAny = RealmAny.create(unmanagedObject)
        val managedObject = realm.writeBlocking {
            val container = Sample().apply {
                stringField = "OUTER"
            }
            copyToRealm(container)
        }
        val latestManaged = realm.writeBlocking {
            val latestContainer = assertNotNull(findLatest(managedObject))
            latestContainer.nullableRealmAnyField = realmAny
            latestContainer
        }

        // Now we should have two Sample objects: the container and the INNER
        assertEquals(2, realm.query<Sample>().count().find())
        val expected = unmanagedObject.stringField
        val actual = latestManaged.nullableRealmAnyField
            ?.asRealmObject<Sample>()
            ?.stringField
        assertEquals(expected, actual)
    }
}
