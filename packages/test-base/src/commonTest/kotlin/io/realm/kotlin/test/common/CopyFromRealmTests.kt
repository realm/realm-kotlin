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
package io.realm.kotlin.test.common

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.embedded.EmbeddedChild
import io.realm.kotlin.entities.embedded.EmbeddedParent
import io.realm.kotlin.entities.embedded.embeddedSchema
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.ext.toRealmDictionary
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.ext.toRealmSet
import io.realm.kotlin.internal.RealmObjectInternal
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.realmObjectCompanionOrThrow
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.schema.ListPropertyType
import io.realm.kotlin.schema.MapPropertyType
import io.realm.kotlin.schema.RealmProperty
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.schema.SetPropertyType
import io.realm.kotlin.schema.ValuePropertyType
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
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
    fun realmAny_realmObjectReferences() {
        val inner = Sample().apply { stringField = "inner" }

        val insertedObj = realm.writeBlocking {
            copyToRealm(Sample().apply { nullableRealmAnyField = RealmAny.create(inner) })
        }
        val unmanagedObj = insertedObj.copyFromRealm()

        // Close Realm to ensure data is decoupled from Realm
        realm.close()

        assertNotSame(insertedObj, unmanagedObj)
        val realmAnyField = unmanagedObj.nullableRealmAnyField
        assertNotNull(realmAnyField)
        val innerObjectInsideRealmAny = realmAnyField.asRealmObject<Sample>()
        assertNotNull(innerObjectInsideRealmAny)
        assertFalse(innerObjectInsideRealmAny.isManaged())
        assertEquals(inner.stringField, innerObjectInsideRealmAny.stringField)
    }

    @Test
    fun realmAny_list_realmObjectReferences() {
        val inner = Sample().apply { stringField = "inner" }

        val insertedObj = realm.writeBlocking {
            copyToRealm(Sample().apply { nullableRealmAnyListField = realmListOf(RealmAny.create(inner)) })
        }
        val unmanagedObj = insertedObj.copyFromRealm()

        // Close Realm to ensure data is decoupled from Realm
        realm.close()

        assertNotSame(insertedObj, unmanagedObj)
        val realmAnyListField = unmanagedObj.nullableRealmAnyListField
        assertNotNull(realmAnyListField)
        assertEquals(1, realmAnyListField.size)
        val realmAny = assertNotNull(realmAnyListField[0])
        val innerObjectInsideRealmAny = realmAny.asRealmObject<Sample>()
        assertNotNull(innerObjectInsideRealmAny)
        assertFalse(innerObjectInsideRealmAny.isManaged())
        assertEquals(inner.stringField, innerObjectInsideRealmAny.stringField)
    }

    @Test
    fun realmAny_set_realmObjectReferences() {
        val inner = Sample().apply { stringField = "inner" }

        val insertedObj = realm.writeBlocking {
            copyToRealm(Sample().apply { nullableRealmAnySetField = realmSetOf(RealmAny.create(inner)) })
        }
        val unmanagedObj = insertedObj.copyFromRealm()

        // Close Realm to ensure data is decoupled from Realm
        realm.close()

        assertNotSame(insertedObj, unmanagedObj)
        val realmAnySetField = unmanagedObj.nullableRealmAnySetField
        assertNotNull(realmAnySetField)
        assertEquals(1, realmAnySetField.size)
        val realmAny = assertNotNull(realmAnySetField.iterator().next())
        val innerObjectInsideRealmAny = realmAny.asRealmObject<Sample>()
        assertNotNull(innerObjectInsideRealmAny)
        assertFalse(innerObjectInsideRealmAny.isManaged())
        assertEquals(inner.stringField, innerObjectInsideRealmAny.stringField)
    }

    @Test
    fun realmAny_dictionary_realmObjectReferences() {
        val inner = Sample().apply { stringField = "inner" }

        val expectedEntry = "A" to RealmAny.create(inner)
        val insertedObj = realm.writeBlocking {
            copyToRealm(
                Sample().apply {
                    nullableRealmAnyDictionaryField =
                        realmDictionaryOf(expectedEntry)
                }
            )
        }
        val unmanagedObj = insertedObj.copyFromRealm()

        // Close Realm to ensure data is decoupled from Realm
        realm.close()

        assertNotSame(insertedObj, unmanagedObj)
        val realmAnyDictionaryField = unmanagedObj.nullableRealmAnyDictionaryField
        assertNotNull(realmAnyDictionaryField)
        assertEquals(1, realmAnyDictionaryField.size)
        val entry = assertNotNull(realmAnyDictionaryField.iterator().next())
        assertEquals(expectedEntry.first, entry.key)
        val value = assertNotNull(entry.value)
        val innerObjectInsideRealmAny = value.asRealmObject<Sample>()
        assertNotNull(innerObjectInsideRealmAny)
        assertFalse(innerObjectInsideRealmAny.isManaged())
        assertEquals(inner.stringField, innerObjectInsideRealmAny.stringField)
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
            childrenList = (1..5).map { i ->
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
        assertNotNull(unmanagedObj.childrenList)
        val copiedList = unmanagedObj.childrenList
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
    fun primitiveDictionaries() {
        val type = Sample::class
        val schemaProperties = type.realmObjectCompanionOrThrow().io_realm_kotlin_schema().properties
        val fields: Map<String, KProperty1<*, *>> = type.realmObjectCompanionOrThrow().io_realm_kotlin_fields

        // Dynamically set data on the Sample object
        val originalObject = Sample()
        schemaProperties.forEach { prop: RealmProperty ->
            if (prop.type is MapPropertyType) {
                val accessor: KMutableProperty1<BaseRealmObject, Any?> = fields[prop.name] as KMutableProperty1<BaseRealmObject, Any?>
                val dictionary: RealmDictionary<Any?> = createPrimitiveDictionaryData(prop, accessor)
                accessor.set(originalObject, dictionary)
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
            if (prop.type is MapPropertyType) {
                val accessor: KMutableProperty1<BaseRealmObject, Any?> = fields[prop.name] as KMutableProperty1<BaseRealmObject, Any?>
                val dictionary: RealmDictionary<Any?> = createPrimitiveDictionaryData(prop, accessor)

                if (prop.type.storageType == RealmStorageType.BINARY) {
                    val copy = accessor.get(unmanagedCopy) as RealmDictionary<ByteArray?>
                    assertEquals(dictionary.size, copy.size)
                    assertTrue(dictionary.keys.containsAll(copy.keys))
                    copy.forEach { entry ->
                        // Weird approach but makes it easier to iterate over the elements of the
                        // dictionary similarly to the set test above
                        val copiedValue = entry.value
                        if (copiedValue == null) {
                            assertTrue(dictionary.containsValue(null))
                        } else {
                            assertTrue(
                                dictionary.any {
                                    (it.value as ByteArray).contentEquals(copiedValue)
                                },
                                "${prop.name} failed: $copiedValue"
                            )
                        }
                    }
                } else {
                    val copiedDictionary = accessor.get(unmanagedCopy) as RealmDictionary<Any?>
                    assertEquals(dictionary.size, copiedDictionary.size)
                    assertTrue(dictionary.keys.containsAll(copiedDictionary.keys))
                    copiedDictionary.forEach { entry ->
                        // Order is not guaranteed in the set when round-tripped through Core.
                        assertTrue(dictionary.containsKey(entry.key), "${prop.name} failed key: $entry")
                        assertTrue(dictionary.containsValue(entry.value), "${prop.name} failed value: $entry")
                    }
                }
            }
        }
    }

    @Test
    fun objectDictionary() {
        val sample = Sample().apply {
            nullableObjectDictionaryFieldNotNull = (1..5).map { i ->
                val key = i.toString()
                val value = Sample().apply { stringField = i.toString() }
                key to value
            }.toRealmDictionary()
        }

        val insertedObj = realm.writeBlocking {
            copyToRealm(sample)
        }
        val unmanagedObj: Sample = insertedObj.copyFromRealm()

        // Close Realm to ensure data is decoupled from Realm
        realm.close()

        assertNotSame(insertedObj, unmanagedObj)
        assertNotNull(unmanagedObj.nullableObjectDictionaryFieldNotNull)
        val copiedDictionary: RealmDictionary<Sample?> = unmanagedObj.nullableObjectDictionaryFieldNotNull
        assertEquals(5, copiedDictionary.size)
        copiedDictionary.forEach { copiedEntry ->
            val actual = sample.nullableObjectDictionaryFieldNotNull.filter { expectedEntry ->
                assertNotNull(copiedEntry.value).stringField == expectedEntry.value?.stringField
            }.size

            assertEquals(1, actual)
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
        val managedDictionary: RealmDictionary<Sample?> = managedObj.nullableObjectDictionaryFieldNotNull
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
            realm.copyFromRealm(managedDictionary)
        }
        assertFailsWith<IllegalArgumentException> {
            managedDictionary.copyFromRealm()
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
            nullableObjectDictionaryFieldNotNull["A"] = Sample().apply { stringField = "listObject" }
        }
        realm.writeBlocking {
            val liveObj = copyToRealm(sample)
            val liveList = liveObj.objectListField
            val liveSet = liveObj.objectSetField
            val liveDictionary = liveObj.nullableObjectDictionaryFieldNotNull
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
            assertFailsWith<IllegalArgumentException> {
                realm.copyFromRealm(liveDictionary)
            }
            assertFailsWith<IllegalArgumentException> {
                liveDictionary.copyFromRealm()
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
        assertFailsWith<IllegalArgumentException> {
            realmDictionaryOf<Sample?>("A" to Sample()).copyFromRealm()
        }
        assertFailsWith<IllegalArgumentException> {
            realm.copyFromRealm(realmDictionaryOf("A" to Sample()))
        }
    }

    @Test
    fun emptyCollection() {
        val listResult = realm.copyFromRealm(listOf())
        assertEquals(0, listResult.size)

        val realmListResult = realm.copyFromRealm(realmListOf())
        assertEquals(0, realmListResult.size)

        val realmSetResult = realm.copyFromRealm(realmSetOf())
        assertEquals(0, realmSetResult.size)

        val realmDictionaryResult = realm.copyFromRealm(realmDictionaryOf())
        assertEquals(0, realmDictionaryResult.size)
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
            nullableObjectDictionaryFieldNotNull = realmDictionaryOf("A" to topLevelObject)
        }

        val unmanagedCopy = realm.writeBlocking {
            copyToRealm(sample).copyFromRealm()
        }

        assertSame(unmanagedCopy, unmanagedCopy.nullableObject)
        assertSame(unmanagedCopy, unmanagedCopy.objectListField.first())
        assertSame(unmanagedCopy, unmanagedCopy.objectSetField.first())
        assertSame(unmanagedCopy, unmanagedCopy.nullableObjectDictionaryFieldNotNull["A"])
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
            nullableObjectDictionaryFieldNotNull = realmDictionaryOf(
                "A" to Sample().apply {
                    stringField = "dictionary-depth-1"
                    nullableObjectDictionaryFieldNotNull = realmDictionaryOf(
                        "B" to Sample().apply {
                            stringField = "dictionary-depth-2"
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
        assertEquals(
            "dictionary-depth-2",
            assertNotNull(managedObj.nullableObjectDictionaryFieldNotNull["A"]).let { objLevel1 ->
                assertNotNull(objLevel1.nullableObjectDictionaryFieldNotNull["B"]).stringField
            }
        )

        val unmanagedCopy = managedObj.copyFromRealm(depth = 1u)
        assertEquals("obj-depth-1", unmanagedCopy.nullableObject!!.stringField)
        assertEquals("list-depth-1", unmanagedCopy.objectListField.first().stringField)
        assertEquals("set-depth-1", unmanagedCopy.objectSetField.first().stringField)
        assertEquals(
            "dictionary-depth-1",
            assertNotNull(unmanagedCopy.nullableObjectDictionaryFieldNotNull["A"]).stringField
        )
        assertNull(unmanagedCopy.nullableObject!!.nullableObject)
        assertEquals(0, unmanagedCopy.objectListField.first().objectListField.size)
        assertEquals(0, unmanagedCopy.objectSetField.first().objectSetField.size)
        assertEquals(
            0,
            assertNotNull(unmanagedCopy.nullableObjectDictionaryFieldNotNull["A"]).objectSetField.size
        )
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
            stringDictionaryField = realmDictionaryOf("A" to "foo", "B" to "bar")
            nullableObjectDictionaryFieldNotNull = realmDictionaryOf(
                "A" to Sample().apply {
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
        assertEquals(0, unmanagedCopy.nullableObjectDictionaryFieldNotNull.size)
        assertEquals(2, unmanagedCopy.stringListField.size)
        assertEquals(2, unmanagedCopy.stringSetField.size)
        assertEquals(2, unmanagedCopy.stringDictionaryField.size)
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
        if (type.isMarkedNullable) {
            return null
        } else {
            return when (type.classifier) {
                // Make sure these values are different than default values in Sample class
                String::class -> "foo"
                Byte::class -> 0x5.toByte()
                Char::class -> 'b'
                Short::class -> 3.toShort()
                Int::class -> 4
                Long::class -> 7L
                Boolean::class -> false
                Float::class -> 1.23.toFloat()
                Double::class -> 1.234
                ByteArray::class -> byteArrayOf(43)
                RealmInstant::class -> RealmInstant.from(1, 100)
                ObjectId::class -> ObjectId.from("635a1a95184a200db8a07bfc")
                RealmUUID::class -> RealmUUID.from("defda04c-80ac-4ed9-86f5-334fef3dcf8a")
                MutableRealmInt::class -> MutableRealmInt.create(7)
                RealmAny::class -> RealmAny.create(1)
                BsonObjectId::class -> BsonObjectId("635a1a95184a200db8a07bfc")
                Decimal128::class -> Decimal128("1.8446744073709551618E-615")
                Sample::class -> null // Object references are not part of this test, so just return null

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
        val list: MutableList<Any?> = when (genericType.classifier) {
            String::class -> realmListOf("foo", "bar")
            Byte::class -> realmListOf(1.toByte(), 2.toByte())
            Char::class -> realmListOf('a', 'b')
            Short::class -> realmListOf(3.toShort(), 4.toShort())
            Int::class -> realmListOf(5, 6)
            Long::class -> realmListOf(7.toLong(), 8.toLong())
            Boolean::class -> realmListOf(true, false)
            Float::class -> realmListOf(1.23.toFloat(), 1.34.toFloat())
            Double::class -> realmListOf(1.234, 1.345)
            ByteArray::class -> realmListOf(byteArrayOf(42), byteArrayOf(43))
            RealmInstant::class -> realmListOf(RealmInstant.from(1, 0), RealmInstant.from(1, 1))
            ObjectId::class -> realmListOf(ObjectId.from("635a1a95184a200db8a07bfc"), ObjectId.from("735a1a95184a200db8a07bfc"))
            RealmUUID::class -> realmListOf(RealmUUID.from("defda04c-80ac-4ed9-86f5-334fef3dcf8a"), RealmUUID.from("eefda04c-80ac-4ed9-86f5-334fef3dcf8a"))
            BsonObjectId::class -> realmListOf(BsonObjectId("635a1a95184a200db8a07bfc"), BsonObjectId("735a1a95184a200db8a07bfc"))
            Decimal128::class -> realmListOf(Decimal128("1.8446744073709551618E-615"), Decimal128("2.8446744073709551618E-6151"))
            RealmAny::class -> realmListOf(RealmAny.create(1))
            Sample::class -> realmListOf() // Object references are not part of this test
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
        val set: MutableSet<Any?> = when (genericType.classifier) {
            String::class -> realmSetOf("foo", "bar")
            Byte::class -> realmSetOf(1.toByte(), 2.toByte())
            Char::class -> realmSetOf('a', 'b')
            Short::class -> realmSetOf(3.toShort(), 4.toShort())
            Int::class -> realmSetOf(5, 6)
            Long::class -> realmSetOf(7.toLong(), 8.toLong())
            Boolean::class -> realmSetOf(true, false)
            Float::class -> realmSetOf(1.23.toFloat(), 1.34.toFloat())
            Double::class -> realmSetOf(1.234, 1.345)
            ByteArray::class -> realmSetOf(byteArrayOf(42), byteArrayOf(43))
            RealmInstant::class -> realmSetOf(RealmInstant.from(1, 0), RealmInstant.from(1, 1))
            ObjectId::class -> realmSetOf(ObjectId.from("635a1a95184a200db8a07bfc"), ObjectId.from("735a1a95184a200db8a07bfc"))
            RealmUUID::class -> realmSetOf(RealmUUID.from("defda04c-80ac-4ed9-86f5-334fef3dcf8a"), RealmUUID.from("eefda04c-80ac-4ed9-86f5-334fef3dcf8a"))
            BsonObjectId::class -> realmSetOf(BsonObjectId("635a1a95184a200db8a07bfc"), BsonObjectId("735a1a95184a200db8a07bfc"))
            Decimal128::class -> realmSetOf(Decimal128("1.8446744073709551618E-615"), Decimal128("2.8446744073709551618E-6151"))
            RealmAny::class -> realmSetOf(RealmAny.create(1))
            Sample::class -> realmSetOf() // Object references are not part of this test
            else -> fail("Missing support for $genericType")
        }
        if (prop.isNullable) {
            set.add(null)
        }
        return set
    }

    // Create Sample data for dictionary properties
    private fun createPrimitiveDictionaryData(
        prop: RealmProperty,
        accessor: KMutableProperty1<BaseRealmObject, Any?>
    ): RealmDictionary<Any?> {
        val type: KType = accessor.returnType
        val genericType: KType = type.arguments.first().type!!
        val dictionary: RealmDictionary<Any?> = when (genericType.classifier) {
            String::class -> {
                realmDictionaryOf("A" to "foo", "B" to "bar")
            }
            Byte::class -> realmDictionaryOf("A" to 1.toByte(), "B" to 2.toByte())
            Char::class -> realmDictionaryOf("A" to 'a', "B" to 'b')
            Short::class -> realmDictionaryOf("A" to 3.toShort(), "B" to 4.toShort())
            Int::class -> realmDictionaryOf("A" to 5, "B" to 6)
            Long::class -> realmDictionaryOf("A" to 7.toLong(), "B" to 8.toLong())
            Boolean::class -> realmDictionaryOf("A" to true, "B" to false)
            Float::class -> realmDictionaryOf("A" to 1.23.toFloat(), "B" to 1.34.toFloat())
            Double::class -> realmDictionaryOf("A" to 1.234, "B" to 1.345)
            ByteArray::class -> realmDictionaryOf("A" to byteArrayOf(42), "B" to byteArrayOf(43))
            RealmInstant::class -> realmDictionaryOf("A" to RealmInstant.from(1, 0), "B" to RealmInstant.from(1, 1))
            ObjectId::class -> realmDictionaryOf("A" to ObjectId.from("635a1a95184a200db8a07bfc"), "B" to ObjectId.from("735a1a95184a200db8a07bfc"))
            RealmUUID::class -> realmDictionaryOf("A" to RealmUUID.from("defda04c-80ac-4ed9-86f5-334fef3dcf8a"), "B" to RealmUUID.from("eefda04c-80ac-4ed9-86f5-334fef3dcf8a"))
            BsonObjectId::class -> realmDictionaryOf("A" to BsonObjectId("635a1a95184a200db8a07bfc"), "B" to BsonObjectId("735a1a95184a200db8a07bfc"))
            Decimal128::class -> realmDictionaryOf("A" to Decimal128("1.8446744073709551618E-615"), "B" to Decimal128("2.8446744073709551618E-6151"))
            RealmAny::class -> realmDictionaryOf("A" to RealmAny.create(1))
            Sample::class -> realmDictionaryOf() // Object references are not part of this test
            else -> fail("Missing support for $genericType")
        }
        if (prop.isNullable) {
            dictionary["C"] = null
        }
        return dictionary
    }
}
