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

@file:Suppress("invisible_reference", "invisible_member")
package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.embedded.EmbeddedChild
import io.realm.kotlin.entities.embedded.EmbeddedParent
import io.realm.kotlin.entities.embedded.embeddedSchema
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.ext.toRealmSet
import io.realm.kotlin.internal.RealmObjectInternal
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.realmObjectCompanionOrThrow
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.schema.ListPropertyType
import io.realm.kotlin.schema.RealmProperty
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.schema.SetPropertyType
import io.realm.kotlin.schema.ValuePropertyType
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class CopyFromRealmTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class) + embeddedSchema)
            .directory(tmpDir)
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
    fun primitiveValues() { // This also checks that any default values set in the class are being overridden correctly.
        val type = Sample::class
        val schemaProperties = type.realmObjectCompanionOrThrow().io_realm_kotlin_schema().properties
        val fields: Map<String, KProperty1<*, *>> = type.realmObjectCompanionOrThrow().io_realm_kotlin_fields

        // Dynamically set data on the Sample object
        val originalObject = Sample()
        schemaProperties.forEach { prop: RealmProperty ->
            if (prop.type is ValuePropertyType) {
                val accessor: KMutableProperty1<BaseRealmObject, Any?> = fields[prop.name] as KMutableProperty1<BaseRealmObject, Any?>
                val fieldValue: Any? = createPrimitiveValueData(accessor)
                accessor.set(originalObject, fieldValue)
            }
        }

        // Round-trip object through `copyToRealm` and `copyFromRealm`.
        val unmanagedCopy = realm.writeBlocking {
            copyToRealm(originalObject).copyFromRealm()
        }

        // Close Realm to ensure data is decoupled from Realm
        realm.close()

        // Validate that all primitive list fields were round-tripped correctly.
        schemaProperties.forEach { prop: RealmProperty ->
            if (prop.type is ValuePropertyType) {
                val accessor: KMutableProperty1<BaseRealmObject, Any?> = fields[prop.name] as KMutableProperty1<BaseRealmObject, Any?>
                val value: Any? = createPrimitiveValueData(accessor)

                if (prop.type.storageType == RealmStorageType.BINARY) {
                    val copiedValue = accessor.get(unmanagedCopy) as ByteArray?
                    assertContentEquals(value as ByteArray?, copiedValue, "${prop.name} failed")
                } else {
                    val copiedValue = accessor.get(unmanagedCopy) as Any?
                    assertEquals(value, copiedValue, "${prop.name} failed")
                }
            }
        }
    }

    @Test
    fun realmObjectReferences() {
        val innerSample = Sample().apply { stringField = "inner" }

        val insertedObj = realm.writeBlocking {
            copyToRealm(Sample().apply { nullableObject = innerSample })
        }
        val unmanagedObj: Sample = insertedObj.copyFromRealm()

        // Close Realm to ensure data is decoupled from Realm
        realm.close()

        assertNotSame(insertedObj, unmanagedObj)
        assertNotNull(unmanagedObj.nullableObject)
        val innerCopy = unmanagedObj.nullableObject!!
        assertFalse(innerCopy.isManaged())
        assertEquals("inner", innerCopy.stringField)
    }

    @Test
    fun embeddedObjectReferences() {
        val child = EmbeddedChild("inner")
        val parent = EmbeddedParent().apply {
            this.child = child
        }

        val insertedObj = realm.writeBlocking {
            copyToRealm(parent)
        }

        val unmanagedObj: EmbeddedParent = insertedObj.copyFromRealm()

        // Close Realm to ensure data is decoupled from Realm
        realm.close()

        assertFalse(unmanagedObj.isManaged())
        assertNotSame(insertedObj, unmanagedObj)
        assertNotNull(unmanagedObj.child)
        val innerCopy = unmanagedObj.child!!
        assertFalse(innerCopy.isManaged())
        assertEquals("inner", innerCopy.id)
    }

    @Test
    fun embeddedObjectWithoutParent() {
        val child = EmbeddedChild("inner")
        val parent = EmbeddedParent().apply {
            this.child = child
        }

        val insertedObj: EmbeddedParent = realm.writeBlocking {
            copyToRealm(parent)
        }

        val unmanagedObj: EmbeddedChild = insertedObj.child!!.copyFromRealm()

        assertFalse(unmanagedObj.isManaged())
        assertNotSame(insertedObj.child, unmanagedObj)
        assertNotNull(unmanagedObj)
        assertFalse(unmanagedObj.isManaged())
        assertEquals("inner", unmanagedObj.id)
    }

    @Test
    fun primitiveLists() {
        val type = Sample::class
        val schemaProperties = type.realmObjectCompanionOrThrow().io_realm_kotlin_schema().properties
        val fields: Map<String, KProperty1<*, *>> = type.realmObjectCompanionOrThrow().io_realm_kotlin_fields

        // Dynamically set data on the Sample object
        val originalObject = Sample()
        schemaProperties.forEach { prop: RealmProperty ->
            if (prop.type is ListPropertyType && !(prop.type as ListPropertyType).isComputed) {
                val accessor: KMutableProperty1<BaseRealmObject, Any?> = fields[prop.name] as KMutableProperty1<BaseRealmObject, Any?>
                val list: List<Any?> = createPrimitiveListData(prop, accessor)
                accessor.set(originalObject, list)
            }
        }

        // Round-trip object through `copyToRealm` and `copyFromRealm`.
        val unmanagedCopy = realm.writeBlocking {
            copyToRealm(originalObject).copyFromRealm()
        }

        // Close Realm to ensure data is decoupled from Realm
        realm.close()

        // Validate that all primitive list fields were round-tripped correctly.
        schemaProperties.forEach { prop: RealmProperty ->
            if (prop.type is ListPropertyType && !(prop.type as ListPropertyType).isComputed) {
                val accessor: KMutableProperty1<BaseRealmObject, Any?> = fields[prop.name] as KMutableProperty1<BaseRealmObject, Any?>
                val list: List<Any?> = createPrimitiveListData(prop, accessor)

                if (prop.type.storageType == RealmStorageType.BINARY) {
                    val copy = accessor.get(unmanagedCopy) as List<ByteArray?>
                    assertEquals(list.size, copy.size)
                    copy.forEachIndexed { i, el: ByteArray? ->
                        assertContentEquals(list[i] as ByteArray?, el, "$i failed")
                    }
                } else {
                    assertContentEquals(list, accessor.get(unmanagedCopy) as List<Any?>, "${prop.name} failed")
                }
            }
        }
    }

    @Test
    fun objectLists() {
        // Create object with list of 5 objects
        val sample = Sample().apply {
            objectListField = (1..5).map { i ->
                Sample().apply { stringField = i.toString() }
            }.toRealmList()
        }

        val insertedObj = realm.writeBlocking {
            copyToRealm(sample)
        }
        val unmanagedObj: Sample = insertedObj.copyFromRealm()
        assertNotSame(insertedObj, unmanagedObj)
        assertNotNull(unmanagedObj.objectListField)
        val copiedList = unmanagedObj.objectListField
        assertEquals(5, copiedList.size)
        copiedList.forEachIndexed { i, el ->
            assertFalse(el.isManaged())
            assertEquals((i + 1).toString(), el.stringField)
        }
    }

    @Test
    fun embeddedObjectLists() {
        // Create object with list of 5 objects
        val sample = EmbeddedParent().apply {
            children = (1..5).map { i ->
                EmbeddedChild(i.toString())
            }.toRealmList()
        }

        val insertedObj = realm.writeBlocking {
            copyToRealm(sample)
        }

        val unmanagedObj: EmbeddedParent = insertedObj.copyFromRealm()

        // Close Realm to ensure data is decoupled from Realm
        realm.close()

        assertNotSame(insertedObj, unmanagedObj)
        assertNotNull(unmanagedObj.children)
        val copiedList = unmanagedObj.children
        assertEquals(5, copiedList.size)
        copiedList.forEachIndexed { i, el ->
            assertFalse(el.isManaged())
            assertEquals((i + 1).toString(), el.id)
        }
    }

    @Test
    fun primitiveSets() {
        val type = Sample::class
        val schemaProperties = type.realmObjectCompanionOrThrow().io_realm_kotlin_schema().properties
        val fields: Map<String, KProperty1<*, *>> = type.realmObjectCompanionOrThrow().io_realm_kotlin_fields

        // Dynamically set data on the Sample object
        val originalObject = Sample()
        schemaProperties.forEach { prop: RealmProperty ->
            if (prop.type is SetPropertyType) {
                val accessor: KMutableProperty1<BaseRealmObject, Any?> = fields[prop.name] as KMutableProperty1<BaseRealmObject, Any?>
                val set: Set<Any?> = createPrimitiveSetData(prop, accessor)
                accessor.set(originalObject, set)
            }
        }

        // Round-trip object through `copyToRealm` and `copyFromRealm`.
        val unmanagedCopy = realm.writeBlocking {
            copyToRealm(originalObject).copyFromRealm()
        }

        // Close Realm to ensure data is decoupled from Realm
        realm.close()

        // Validate that all primitive list fields were round-tripped correctly.
        schemaProperties.forEach { prop: RealmProperty ->
            if (prop.type is SetPropertyType) {
                val accessor: KMutableProperty1<BaseRealmObject, Any?> = fields[prop.name] as KMutableProperty1<BaseRealmObject, Any?>
                val set: Set<Any?> = createPrimitiveSetData(prop, accessor)

                if (prop.type.storageType == RealmStorageType.BINARY) {
                    val copy = accessor.get(unmanagedCopy) as Set<ByteArray?>
                    assertEquals(set.size, copy.size)
                    copy.forEach { copiedValue: ByteArray? ->
                        // Order is not guaranteed in the set when round-tripped through Core.
                        // Also HashSets on JVM are rather annoying when it comes to byte arrays.
                        // ByteArray equals/hashcode only considers the memory address, and not
                        // the full content, so when copying byte arrays, the JVM does not consider
                        // them equals. So for this test, use `any` instead of `contains`.
                        if (copiedValue == null) {
                            assertTrue(set.contains(copiedValue))
                        } else {
                            assertTrue(
                                set.any {
                                    (it as ByteArray).contentEquals(copiedValue)
                                },
                                "${prop.name} failed: $copiedValue"
                            )
                        }
                    }
                } else {
                    val copiedSet = accessor.get(unmanagedCopy) as Set<Any?>
                    assertEquals(set.size, copiedSet.size)
                    copiedSet.forEach { copiedValue ->
                        // Order is not guaranteed in the set when round-tripped through Core.
                        assertTrue(set.contains(copiedValue), "${prop.name} failed: $copiedValue")
                    }
                }
            }
        }
    }

    @Test
    fun objectSet() {
        val sample = Sample().apply {
            objectSetField = (1..5).map { i ->
                Sample().apply { stringField = i.toString() }
            }.toRealmSet()
        }

        val insertedObj = realm.writeBlocking {
            copyToRealm(sample)
        }
        val unmanagedObj: Sample = insertedObj.copyFromRealm()

        // Close Realm to ensure data is decoupled from Realm
        realm.close()

        assertNotSame(insertedObj, unmanagedObj)
        assertNotNull(unmanagedObj.objectSetField)
        val copiedSet: RealmSet<Sample> = unmanagedObj.objectSetField
        assertEquals(5, copiedSet.size)
        copiedSet.forEach { copiedObject ->
            assertEquals(
                1,
                sample.objectSetField.filter {
                    copiedObject.stringField == it.stringField
                }.size
            )
        }
    }

    @Test
    fun realmResults() {
        realm.writeBlocking {
            copyToRealm(Sample().apply { stringField = "sample" })
        }

        val results = realm.query<Sample>().find()
        assertEquals(1, results.size)

        val unmanagedCopy: List<Sample> = results.copyFromRealm()

        // Close Realm to ensure data is decoupled from Realm
        realm.close()

        assertEquals(1, unmanagedCopy.size)
        assertEquals("sample", unmanagedCopy.first().stringField)
    }

    @Test
    fun closedObjectsAndCollections_throws() {
        val sample = Sample().apply {
            objectListField.add(Sample().apply { stringField = "listObject" })
            objectSetField.add(Sample().apply { stringField = "listObject" })
        }
        val managedObj = realm.writeBlocking {
            copyToRealm(sample)
        }

        // Copying collections from a closed Realm should fail
        val managedList: RealmList<Sample> = managedObj.objectListField
        val managedSet: RealmSet<Sample> = managedObj.objectSetField
        val results: RealmResults<Sample> = realm.query<Sample>().find()
        realm.close()
        assertFailsWith<IllegalArgumentException> {
            realm.copyFromRealm(managedObj)
        }
        assertFailsWith<IllegalArgumentException> {
            managedObj.copyFromRealm()
        }
        assertFailsWith<IllegalArgumentException> {
            realm.copyFromRealm(managedList)
        }
        assertFailsWith<IllegalArgumentException> {
            managedList.copyFromRealm()
        }
        assertFailsWith<IllegalArgumentException> {
            realm.copyFromRealm(managedSet)
        }
        assertFailsWith<IllegalArgumentException> {
            managedSet.copyFromRealm()
        }
        assertFailsWith<IllegalArgumentException> {
            realm.copyFromRealm(results)
        }
        assertFailsWith<IllegalArgumentException> {
            results.copyFromRealm()
        }
    }

    @Test
    fun deletedObjectsAndCollections_throws() {
        val sample = Sample().apply {
            objectListField.add(Sample().apply { stringField = "listObject" })
            objectSetField.add(Sample().apply { stringField = "listObject" })
        }
        realm.writeBlocking {
            val liveObj = copyToRealm(sample)
            val liveList = liveObj.objectListField
            val liveSet = liveObj.objectSetField
            delete(liveObj)

            // Copying deleted objects should fail
            assertFailsWith<IllegalArgumentException> {
                realm.copyFromRealm(liveObj)
            }
            assertFailsWith<IllegalArgumentException> {
                liveObj.copyFromRealm()
            }
            assertFailsWith<IllegalArgumentException> {
                realm.copyFromRealm(liveList)
            }
            assertFailsWith<IllegalArgumentException> {
                liveList.copyFromRealm()
            }
            assertFailsWith<IllegalArgumentException> {
                realm.copyFromRealm(liveSet)
            }
            assertFailsWith<IllegalArgumentException> {
                liveSet.copyFromRealm()
            }
        }
    }

    @Test
    fun unmanagedObjectsAndCollections_throws() {
        assertFailsWith<IllegalArgumentException> {
            realm.copyFromRealm(Sample())
        }
        assertFailsWith<IllegalArgumentException> {
            Sample().copyFromRealm()
        }
        assertFailsWith<IllegalArgumentException> {
            realmListOf(Sample()).copyFromRealm()
        }
        assertFailsWith<IllegalArgumentException> {
            realm.copyFromRealm(realmListOf(Sample()))
        }
        assertFailsWith<IllegalArgumentException> {
            realmSetOf(Sample()).copyFromRealm()
        }
        assertFailsWith<IllegalArgumentException> {
            realm.copyFromRealm(realmSetOf(Sample()))
        }
    }

    @Test
    fun emptyCollection() {
        var result = realm.copyFromRealm(listOf())
        assertEquals(0, result.size)

        result = realm.copyFromRealm(realmListOf())
        assertEquals(0, result.size)

        result = realm.copyFromRealm(realmSetOf())
        assertEquals(0, result.size)
    }

    @Test
    fun circularObjectGraph() {
        // Verify that circles are copied correctly. Circles can happen across all reference
        // types: Object reference, List, Set
        val sample = Sample().apply {
            val topLevelObject: Sample = this
            stringField = "top"
            nullableObject = topLevelObject
            objectListField = realmListOf(topLevelObject)
            objectSetField = realmSetOf(topLevelObject)
        }

        val unmanagedCopy = realm.writeBlocking {
            copyToRealm(sample).copyFromRealm()
        }

        assertSame(unmanagedCopy, unmanagedCopy.nullableObject)
        assertSame(unmanagedCopy, unmanagedCopy.objectListField.first())
        assertSame(unmanagedCopy, unmanagedCopy.objectSetField.first())
    }

    @Test
    fun objectsAtDifferentVersionsAreDifferentAfterCopy() {
        val sample = Sample().apply {
            stringField = "v1"
            intField = 3
        }

        val managedSample1 = realm.writeBlocking {
            copyToRealm(sample)
        }
        val managedSample2 = realm.writeBlocking {
            findLatest(managedSample1)!!.apply {
                intField = 42
            }
        }
        val o1p = (managedSample1 as RealmObjectInternal).io_realm_kotlin_objectReference!!.objectPointer
        val o2p = (managedSample2 as RealmObjectInternal).io_realm_kotlin_objectReference!!.objectPointer
        assertFalse(RealmInterop.realm_equals(o1p, o2p))

        val unmanagedObjects = realm.copyFromRealm(listOf(managedSample1, managedSample2))
        assertEquals(2, unmanagedObjects.size)
        val unmanagedSample1 = unmanagedObjects.first()
        val unmanagedSample2 = unmanagedObjects.last()

        assertNotSame(unmanagedSample1, unmanagedSample2)
        assertNotEquals(unmanagedSample1, unmanagedSample2)
        assertEquals("v1", unmanagedSample1.stringField)
        assertEquals(3, unmanagedSample1.intField)
        assertEquals(42, unmanagedSample2.intField)
    }

    @Test
    fun depth_nullAfterDepthIsReached() {
        val sample = Sample().apply {
            stringField = "obj-depth-0"
            nullableObject = Sample().apply {
                stringField = "obj-depth-1"
                nullableObject = Sample().apply {
                    stringField = "obj-depth-2"
                }
            }
            objectListField = realmListOf(
                Sample().apply {
                    stringField = "list-depth-1"
                    objectListField = realmListOf(
                        Sample().apply {
                            stringField = "list-depth-2"
                        }
                    )
                }
            )
            objectSetField = realmSetOf(
                Sample().apply {
                    stringField = "set-depth-1"
                    objectSetField = realmSetOf(
                        Sample().apply {
                            stringField = "set-depth-2"
                        }
                    )
                }
            )
        }

        val managedObj = realm.writeBlocking {
            copyToRealm(sample)
        }
        assertEquals("obj-depth-2", managedObj.nullableObject!!.nullableObject!!.stringField)
        assertEquals("list-depth-2", managedObj.objectListField.first().objectListField.first().stringField)
        assertEquals("set-depth-2", managedObj.objectSetField.first().objectSetField.first().stringField)

        val unmanagedCopy = managedObj.copyFromRealm(depth = 1u)
        assertEquals("obj-depth-1", unmanagedCopy.nullableObject!!.stringField)
        assertEquals("list-depth-1", unmanagedCopy.objectListField.first().stringField)
        assertEquals("set-depth-1", unmanagedCopy.objectSetField.first().stringField)
        assertNull(unmanagedCopy.nullableObject!!.nullableObject)
        assertEquals(0, unmanagedCopy.objectListField.first().objectListField.size)
        assertEquals(0, unmanagedCopy.objectSetField.first().objectSetField.size)
    }

    @Test
    fun depth_primitiveListsAndSetsWhenDepthIsReached() {
        val sample = Sample().apply {
            stringField = "obj-depth-0"
            stringListField = realmListOf("foo", "bar")
            objectListField = realmListOf(
                Sample().apply {
                    stringField = "list-depth-1"
                }
            )
            stringSetField = realmSetOf("foo", "bar")
            objectSetField = realmSetOf(
                Sample().apply {
                    stringField = "set-depth-1"
                }
            )
        }

        val managedObj = realm.writeBlocking {
            copyToRealm(sample)
        }
        val unmanagedCopy = managedObj.copyFromRealm(depth = 0u)
        assertNull(unmanagedCopy.nullableObject)
        assertEquals(0, unmanagedCopy.objectListField.size)
        assertEquals(0, unmanagedCopy.objectSetField.size)
        assertEquals(2, unmanagedCopy.stringListField.size)
        assertEquals(2, unmanagedCopy.stringSetField.size)
    }

    @Test
    fun linkingObjectsAreNotCopied() {
        val sample = Sample().apply {
            nullableObject = Sample()
        }
        val managedObj = realm.writeBlocking {
            copyToRealm(sample)
        }
        assertEquals(1, managedObj.nullableObject!!.objectBacklinks.size)
        val unmanagedCopy = managedObj.copyFromRealm()
        assertFailsWith<IllegalStateException> {
            unmanagedCopy.objectBacklinks // Empty RealmResults
        }
        assertFailsWith<IllegalStateException> {
            unmanagedCopy.nullableObject!!.objectBacklinks // Have 1 backlink
        }
    }

    // Create sample data for primitive values
    private fun createPrimitiveValueData(
        accessor: KMutableProperty1<BaseRealmObject, Any?>
    ): Any? {
        val type: KType = accessor.returnType
        type.classifier
        if (type.isMarkedNullable) {
            return null
        } else {
            return when (type.toString()) {
                // Make sure these values are different than default values in Sample class
                "kotlin.String" -> "foo"
                "kotlin.Byte" -> 0x5.toByte()
                "kotlin.Char" -> 'b'
                "kotlin.Short" -> 3.toShort()
                "kotlin.Int" -> 4
                "kotlin.Long" -> 7L
                "kotlin.Boolean" -> false
                "kotlin.Float" -> 1.23.toFloat()
                "kotlin.Double" -> 1.234
                "kotlin.ByteArray" -> byteArrayOf(43)
                "io.realm.kotlin.types.RealmInstant" -> RealmInstant.from(1, 100)
                "io.realm.kotlin.types.ObjectId" -> ObjectId.from("635a1a95184a200db8a07bfc")
                "io.realm.kotlin.types.RealmUUID" -> RealmUUID.from("defda04c-80ac-4ed9-86f5-334fef3dcf8a")
                "io.realm.kotlin.types.MutableRealmInt" -> MutableRealmInt.create(7)
                "org.mongodb.kbson.BsonObjectId" -> BsonObjectId("635a1a95184a200db8a07bfc")
                "io.realm.kotlin.entities.Sample" -> null // Object references are not part of this test, so just return null
                else -> fail("Missing support for $type")
            }
        }
    }

    // Create Sample data, for lists that can contain `null`, there is a `null` element in the middle.
    private fun createPrimitiveListData(
        prop: RealmProperty,
        accessor: KMutableProperty1<BaseRealmObject, Any?>
    ): List<Any?> {
        val type: KType = accessor.returnType
        val genericType: KType = type.arguments.first().type!! // This will only support a single explicit generic arguments.
        val list: MutableList<Any?> = when (genericType.toString().removeSuffix("?")) {
            "kotlin.String" -> realmListOf("foo", "bar")
            "kotlin.Byte" -> realmListOf(1.toByte(), 2.toByte())
            "kotlin.Char" -> realmListOf('a', 'b')
            "kotlin.Short" -> realmListOf(3.toShort(), 4.toShort())
            "kotlin.Int" -> realmListOf(5, 6)
            "kotlin.Long" -> realmListOf(7.toLong(), 8.toLong())
            "kotlin.Boolean" -> realmListOf(true, false)
            "kotlin.Float" -> realmListOf(1.23.toFloat(), 1.34.toFloat())
            "kotlin.Double" -> realmListOf(1.234, 1.345)
            "kotlin.ByteArray" -> realmListOf(byteArrayOf(42), byteArrayOf(43))
            "io.realm.kotlin.types.RealmInstant" -> realmListOf(RealmInstant.from(1, 0), RealmInstant.from(1, 1))
            "io.realm.kotlin.types.ObjectId" -> realmListOf(ObjectId.from("635a1a95184a200db8a07bfc"), ObjectId.from("735a1a95184a200db8a07bfc"))
            "io.realm.kotlin.types.RealmUUID" -> realmListOf(RealmUUID.from("defda04c-80ac-4ed9-86f5-334fef3dcf8a"), RealmUUID.from("eefda04c-80ac-4ed9-86f5-334fef3dcf8a"))
            "org.mongodb.kbson.BsonObjectId" -> realmListOf(BsonObjectId("635a1a95184a200db8a07bfc"), BsonObjectId("735a1a95184a200db8a07bfc"))
            "io.realm.kotlin.entities.Sample" -> realmListOf() // Object references are not part of this test
            else -> fail("Missing support for $genericType")
        }
        if (prop.isNullable) {
            list.add(1, null)
        }
        return list
    }

    // Create Sample data for set properties
    private fun createPrimitiveSetData(
        prop: RealmProperty,
        accessor: KMutableProperty1<BaseRealmObject, Any?>
    ): Set<Any?> {
        val type: KType = accessor.returnType
        val genericType: KType = type.arguments.first().type!!
        val set: MutableSet<Any?> = when (genericType.toString().removeSuffix("?")) {
            "kotlin.String" -> realmSetOf("foo", "bar")
            "kotlin.Byte" -> realmSetOf(1.toByte(), 2.toByte())
            "kotlin.Char" -> realmSetOf('a', 'b')
            "kotlin.Short" -> realmSetOf(3.toShort(), 4.toShort())
            "kotlin.Int" -> realmSetOf(5, 6)
            "kotlin.Long" -> realmSetOf(7.toLong(), 8.toLong())
            "kotlin.Boolean" -> realmSetOf(true, false)
            "kotlin.Float" -> realmSetOf(1.23.toFloat(), 1.34.toFloat())
            "kotlin.Double" -> realmSetOf(1.234, 1.345)
            "kotlin.ByteArray" -> realmSetOf(byteArrayOf(42), byteArrayOf(43))
            "io.realm.kotlin.types.RealmInstant" -> realmSetOf(RealmInstant.from(1, 0), RealmInstant.from(1, 1))
            "io.realm.kotlin.types.ObjectId" -> realmSetOf(ObjectId.from("635a1a95184a200db8a07bfc"), ObjectId.from("735a1a95184a200db8a07bfc"))
            "io.realm.kotlin.types.RealmUUID" -> realmSetOf(RealmUUID.from("defda04c-80ac-4ed9-86f5-334fef3dcf8a"), RealmUUID.from("eefda04c-80ac-4ed9-86f5-334fef3dcf8a"))
            "org.mongodb.kbson.BsonObjectId" -> realmSetOf(BsonObjectId("635a1a95184a200db8a07bfc"), BsonObjectId("735a1a95184a200db8a07bfc"))
            "io.realm.kotlin.entities.Sample" -> realmSetOf() // Object references are not part of this test
            else -> fail("Missing support for $genericType")
        }
        if (prop.isNullable) {
            set.add(null)
        }
        return set
    }
}
