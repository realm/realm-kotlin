/*
 * Copyright 2023 Realm Inc.
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

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.dictionary.RealmDictionaryContainer
import io.realm.kotlin.exceptions.RealmException
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmDictionaryEntryOf
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.toRealmDictionary
import io.realm.kotlin.query.find
import io.realm.kotlin.test.ErrorCatcher
import io.realm.kotlin.test.GenericTypeSafetyManager
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmDictionaryEntrySet
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealmDictionaryTests {

    private val dictionarySchema = setOf(RealmDictionaryContainer::class)
    private val descriptors = TypeDescriptor.allDictionaryFieldTypes

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private val managedTesters: List<DictionaryApiTester<*, RealmDictionaryContainer>> by lazy {
        descriptors.map {
            val elementType = it.elementType
            when (val classifier = elementType.classifier) {
                RealmObject::class -> RealmObjectDictionaryTester(
                    realm,
                    getTypeSafety(
                        classifier,
                        elementType.nullable
                    ) as DictionaryTypeSafetyManager<RealmDictionaryContainer>,
                    classifier
                )
                ByteArray::class -> ByteArrayDictionaryTester(
                    realm,
                    getTypeSafety(
                        classifier,
                        elementType.nullable
                    ) as DictionaryTypeSafetyManager<ByteArray>,
                    classifier
                )
                RealmAny::class -> RealmAnyDictionaryTester(
                    realm,
                    getTypeSafety(
                        classifier,
                        elementType.nullable
                    ) as DictionaryTypeSafetyManager<RealmAny?>,
                    classifier
                )
                else -> GenericDictionaryTester(
                    realm,
                    getTypeSafety(classifier, elementType.nullable),
                    classifier
                )
            }
        }
    }

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = dictionarySchema)
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
    fun unmanaged() {
        // No need to be exhaustive here, just checking delegation works
        val dictionary = realmDictionaryOf<RealmDictionaryContainer>()
        val entry = Pair("A", RealmDictionaryContainer().apply { stringField = "Dummy" })
        assertTrue(dictionary.isEmpty())
        dictionary["A"] = entry.second
        assertEquals(1, dictionary.size)
        assertEquals(entry.second.stringField, assertNotNull(dictionary[entry.first]).stringField)
    }

    @Test
    fun realmMapEntryOf() {
        val elem = ("A" to 1)

        // Instantiate from individual parameters
        val fromIndividualValues = realmDictionaryEntryOf(elem.first, elem.second)
        assertEquals(elem.first, fromIndividualValues.key)
        assertEquals(elem.second, fromIndividualValues.value)

        // Instantiate from a Pair<K, V>
        val fromPair = realmDictionaryEntryOf(elem)
        assertEquals(elem.first, fromPair.key)
        assertEquals(elem.second, fromPair.value)

        // Instantiate from a Map.Entry<K, V>
        val fromMapEntry = realmDictionaryEntryOf(realmDictionaryEntryOf(elem))
        assertEquals(elem.first, fromMapEntry.key)
        assertEquals(elem.second, fromMapEntry.value)
    }

    @Test
    fun realmDictionaryInitializer_realmDictionaryOf_fromVarargs() {
        // No need to be exhaustive here
        val realmDictionaryFromArgsEmpty: RealmDictionary<String> = realmDictionaryOf()
        assertTrue(realmDictionaryFromArgsEmpty.isEmpty())

        val args = listOf("A" to "1", "B" to "2").toTypedArray()
        val realmDictionaryFromArgs = realmDictionaryOf(*args)

        assertEquals(args.size, realmDictionaryFromArgs.size)
        realmDictionaryFromArgs.forEach {
            assertContains(args, Pair(it.key, it.value))
        }
    }

    @Test
    fun realmDictionaryInitializer_realmDictionaryOf_fromCollection() {
        // No need to be exhaustive here
        val realmDictionaryFromEmptyList: RealmDictionary<String> = realmDictionaryOf(listOf())
        assertTrue(realmDictionaryFromEmptyList.isEmpty())

        val args = listOf("A" to "1", "B" to "2")
        val realmDictionaryFromList = realmDictionaryOf(args)

        assertEquals(args.size, realmDictionaryFromList.size)
        realmDictionaryFromList.forEach {
            assertContains(args, Pair(it.key, it.value))
        }
    }

    @Test
    fun realmDictionaryInitializer_toRealmDictionary() {
        // No need to be exhaustive here
        val emptyDictionary = emptyList<Pair<String, Int>>().toRealmDictionary()
        assertTrue(emptyDictionary.isEmpty())

        val elem1 = "A" to 1
        val elem2 = "B" to 2

        // We can create a dictionary from a list of one element...
        val oneElementList = listOf(elem1)
        oneElementList.toRealmDictionary()
            .also { dictionaryFromSingleElementList ->
                assertEquals(elem1.second, dictionaryFromSingleElementList[elem1.first])
            }

        // ... or from a list with many elements...
        val multipleElementList = listOf(elem1, elem2)
        multipleElementList.toRealmDictionary()
            .forEach { assertTrue(multipleElementList.contains(Pair(it.key, it.value))) }

        // ... or from a RealmMapEntrySet
        val mapEntrySet: RealmDictionaryEntrySet<Int> = multipleElementList.map {
            realmDictionaryEntryOf(it.first, it.second)
        }.toTypedArray().let {
            mutableSetOf(*it)
        }
        mapEntrySet.toRealmDictionary()
            .forEach { assertTrue(multipleElementList.contains(Pair(it.key, it.value))) }

        // ... or from a dictionary represented by another dictionary, i.e. Map<String, T>
        val dictionary = mapOf("A" to 1, "B" to 2)
        dictionary.toRealmDictionary()
            .let {
                assertEquals(dictionary.keys.size, it.keys.size)
                assertTrue(it.keys.containsAll(dictionary.keys))
                assertEquals(dictionary.values.size, it.values.size)
                assertTrue(it.values.containsAll(dictionary.values))
            }
    }

    @Test
    fun accessors_getter() {
        // No need to be exhaustive here. First test with a dictionary of any primitive type
        realmDictionaryOf(
            "A" to 1.toByte()
        ).also { dictionary ->
            val container = RealmDictionaryContainer()
                .apply { byteDictionaryField = dictionary }
            realm.writeBlocking {
                val managedContainer = copyToRealm(container)
                val managedDictionary = managedContainer.byteDictionaryField
                assertEquals(dictionary.size, managedDictionary.size)
                assertEquals(dictionary["A"], managedDictionary["A"])
            }

            // Repeat outside transaction
            realm.query<RealmDictionaryContainer>()
                .first()
                .find {
                    val managedDictionary = assertNotNull(it).byteDictionaryField
                    assertEquals(dictionary.size, managedDictionary.size)
                    assertEquals(dictionary["A"], managedDictionary["A"])
                }
        }

        // Cleanup between assertions for convenience
        realm.writeBlocking {
            delete(query<RealmDictionaryContainer>())
        }

        // Test with a dictionary of objects
        realmDictionaryOf<RealmDictionaryContainer?>(
            "A" to DICTIONARY_OBJECT_VALUES[0]
        ).also { dictionary ->
            val container = RealmDictionaryContainer()
                .apply { nullableObjectDictionaryField = dictionary }

            realm.writeBlocking {
                val managedContainer = copyToRealm(container)
                val managedDictionary = managedContainer.nullableObjectDictionaryField
                assertEquals(dictionary.size, managedDictionary.size)
                val expected = assertNotNull(dictionary["A"])
                val actual = assertNotNull(managedDictionary["A"])
                assertEquals(expected.stringField, actual.stringField)
            }

            // Repeat outside transaction
            realm.query<RealmDictionaryContainer>()
                .first()
                .find {
                    val managedDictionary = assertNotNull(it).nullableObjectDictionaryField
                    assertEquals(dictionary.size, managedDictionary.size)
                    val expected = assertNotNull(dictionary["A"])
                    val actual = assertNotNull(managedDictionary["A"])
                    assertEquals(expected.stringField, actual.stringField)
                }
        }

        // Cleanup between assertions for convenience
        realm.writeBlocking {
            delete(query<RealmDictionaryContainer>())
        }

        // Test with a dictionary of RealmAny containing a primitive value
        realmDictionaryOf(
            "A" to REALM_ANY_PRIMITIVE_VALUES[0]
        ).also { dictionary ->
            val container = RealmDictionaryContainer()
                .apply { nullableRealmAnyDictionaryField = dictionary }

            realm.writeBlocking {
                val managedContainer = copyToRealm(container)
                val managedDictionary = managedContainer.nullableRealmAnyDictionaryField
                assertEquals(dictionary.size, managedDictionary.size)
                val expected = assertNotNull(dictionary["A"])
                val actual = assertNotNull(managedDictionary["A"])
                assertEquals(expected, actual)
            }

            // Repeat outside transaction
            realm.query<RealmDictionaryContainer>()
                .first()
                .find {
                    val managedDictionary = assertNotNull(it).nullableRealmAnyDictionaryField
                    assertEquals(dictionary.size, managedDictionary.size)
                    val expected = assertNotNull(dictionary["A"])
                    val actual = assertNotNull(managedDictionary["A"])
                    assertEquals(expected, actual)
                }
        }

        // Cleanup between assertions for convenience
        realm.writeBlocking {
            delete(query<RealmDictionaryContainer>())
        }

        // Test with a dictionary of RealmAny containing an object
        realmDictionaryOf<RealmAny?>(
            "A" to REALM_ANY_REALM_OBJECT
        ).also { dictionary ->
            val container = RealmDictionaryContainer()
                .apply { nullableRealmAnyDictionaryField = dictionary }

            realm.writeBlocking {
                val managedContainer = copyToRealm(container)
                val managedDictionary = managedContainer.nullableRealmAnyDictionaryField
                assertEquals(dictionary.size, managedDictionary.size)
                val expectedAny = assertNotNull(dictionary["A"])
                val expected = expectedAny.asRealmObject<RealmDictionaryContainer>()
                val actualAny = assertNotNull(managedDictionary["A"])
                val actual = actualAny.asRealmObject<RealmDictionaryContainer>()
                assertEquals(RealmAny.Type.OBJECT, actualAny.type)
                assertEquals(expected.stringField, actual.stringField)
            }

            // Repeat outside transaction
            realm.query<RealmDictionaryContainer>()
                .first()
                .find {
                    val managedDictionary = assertNotNull(it).nullableRealmAnyDictionaryField
                    assertEquals(dictionary.size, managedDictionary.size)
                    val expectedAny = assertNotNull(dictionary["A"])
                    val expected = expectedAny.asRealmObject<RealmDictionaryContainer>()
                    val actualAny = assertNotNull(managedDictionary["A"])
                    val actual = actualAny.asRealmObject<RealmDictionaryContainer>()
                    assertEquals(RealmAny.Type.OBJECT, actualAny.type)
                    assertEquals(expected.stringField, actual.stringField)
                }
        }
    }

    @Test
    fun accessors_setter() {
        // No need to be exhaustive here. First test with a dictionary of any primitive type
        realm.writeBlocking {
            val unmanagedDictionary1 = realmDictionaryOf(
                "RANDOM1" to 13.toByte(),
                "RANDOM2" to 42.toByte()
            )
            val unmanagedDictionary2 = realmDictionaryOf("X" to 22.toByte())

            val managedContainer1 = copyToRealm(RealmDictionaryContainer())
            val managedContainer2 = copyToRealm(
                RealmDictionaryContainer().apply { byteDictionaryField = unmanagedDictionary2 }
            )

            // Assign an unmanaged dictionary: managedContainer1.dictionary <- unmanagedDictionary1
            managedContainer1.also {
                it.byteDictionaryField = unmanagedDictionary1
                assertEquals(unmanagedDictionary1.size, it.byteDictionaryField.size)
                assertEquals(unmanagedDictionary1["RANDOM1"], it.byteDictionaryField["RANDOM1"])
                assertEquals(unmanagedDictionary1["RANDOM2"], it.byteDictionaryField["RANDOM2"])
            }

            // Assign a managed dictionary: managedContainer1.dictionary <- managedDictionary2.dictionary
            managedContainer1.also {
                it.byteDictionaryField = managedContainer2.byteDictionaryField
                assertEquals(
                    managedContainer2.byteDictionaryField.size,
                    managedContainer1.byteDictionaryField.size
                )
                assertEquals(
                    managedContainer2.byteDictionaryField["X"],
                    managedContainer1.byteDictionaryField["X"],
                )
            }

            // Assign the same managed dictionary: managedContainer1.dictionary <- managedContainer1.dictionary
            managedContainer1.also {
                it.byteDictionaryField = it.byteDictionaryField
                assertEquals(unmanagedDictionary2.size, it.byteDictionaryField.size)
                assertEquals(unmanagedDictionary2["X"], it.byteDictionaryField["X"])
            }
        }

        // Cleanup between assertions for convenience
        realm.writeBlocking {
            delete(query<RealmDictionaryContainer>())
        }

        // Test with a dictionary of objects
        realm.writeBlocking {
            val unmanagedDictionary1 = realmDictionaryOf<RealmDictionaryContainer?>(
                "RANDOM1" to DICTIONARY_OBJECT_VALUES[0],
                "RANDOM2" to DICTIONARY_OBJECT_VALUES[1]
            )
            val unmanagedDictionary2 = realmDictionaryOf<RealmDictionaryContainer?>(
                "X" to RealmDictionaryContainer().apply { stringField = "hello" }
            )

            val managedContainer1 = copyToRealm(RealmDictionaryContainer())
            val managedContainer2 = copyToRealm(
                RealmDictionaryContainer().apply {
                    nullableObjectDictionaryField = unmanagedDictionary2
                }
            )

            // Assign an unmanaged dictionary: managedContainer1.dictionary <- unmanagedDictionary1
            managedContainer1.also {
                it.nullableObjectDictionaryField = unmanagedDictionary1
                assertEquals(unmanagedDictionary1.size, it.nullableObjectDictionaryField.size)
                val expected1 = assertNotNull(unmanagedDictionary1["RANDOM1"])
                val actual1 = assertNotNull(it.nullableObjectDictionaryField["RANDOM1"])
                assertEquals(expected1.stringField, actual1.stringField)
                val expected2 = assertNotNull(unmanagedDictionary1["RANDOM2"])
                val actual2 = assertNotNull(it.nullableObjectDictionaryField["RANDOM2"])
                assertEquals(expected2.stringField, actual2.stringField)
            }

            // Assign a managed dictionary: managedContainer1.dictionary <- managedDictionary2.dictionary
            managedContainer1.also {
                it.nullableObjectDictionaryField = managedContainer2.nullableObjectDictionaryField
                assertEquals(
                    managedContainer2.nullableObjectDictionaryField.size,
                    managedContainer1.nullableObjectDictionaryField.size
                )
                val expected = assertNotNull(managedContainer2.nullableObjectDictionaryField["X"])
                val actual = assertNotNull(managedContainer1.nullableObjectDictionaryField["X"])
                assertEquals(expected.stringField, actual.stringField)
            }

            // Assign the same managed dictionary: managedContainer1.dictionary <- managedContainer1.dictionary
            managedContainer1.also {
                it.nullableObjectDictionaryField = it.nullableObjectDictionaryField
                assertEquals(unmanagedDictionary2.size, it.nullableObjectDictionaryField.size)
                val expected = assertNotNull(unmanagedDictionary2["X"])
                val actual = assertNotNull(it.nullableObjectDictionaryField["X"])
                assertEquals(expected.stringField, actual.stringField)
            }
        }

        // Cleanup between assertions for convenience
        realm.writeBlocking {
            delete(query<RealmDictionaryContainer>())
        }

        // Test with a dictionary of RealmAny containing a primitive value
        realm.writeBlocking {
            val unmanagedDictionary1 = realmDictionaryOf(
                "RANDOM1" to DICTIONARY_REALM_ANY_VALUES[0],
                "RANDOM2" to DICTIONARY_REALM_ANY_VALUES[1]
            )
            val unmanagedDictionary2 = realmDictionaryOf("X" to DICTIONARY_REALM_ANY_VALUES[2])

            val managedContainer1 = copyToRealm(RealmDictionaryContainer())
            val managedContainer2 = copyToRealm(
                RealmDictionaryContainer().apply {
                    nullableRealmAnyDictionaryField = unmanagedDictionary2
                }
            )

            // Assign an unmanaged dictionary: managedContainer1.dictionary <- unmanagedDictionary1
            managedContainer1.also {
                it.nullableRealmAnyDictionaryField = unmanagedDictionary1
                assertEquals(unmanagedDictionary1.size, it.nullableRealmAnyDictionaryField.size)
                assertEquals(
                    unmanagedDictionary1["RANDOM1"],
                    it.nullableRealmAnyDictionaryField["RANDOM1"]
                )
                assertEquals(
                    unmanagedDictionary1["RANDOM2"],
                    it.nullableRealmAnyDictionaryField["RANDOM2"]
                )
            }

            // Assign a managed dictionary: managedContainer1.dictionary <- managedDictionary2.dictionary
            managedContainer1.also {
                it.nullableRealmAnyDictionaryField =
                    managedContainer2.nullableRealmAnyDictionaryField
                assertEquals(
                    managedContainer2.nullableRealmAnyDictionaryField.size,
                    managedContainer1.nullableRealmAnyDictionaryField.size
                )
                assertEquals(
                    managedContainer2.nullableRealmAnyDictionaryField["X"],
                    managedContainer1.nullableRealmAnyDictionaryField["X"],
                )
            }

            // Assign the same managed dictionary: managedContainer1.dictionary <- managedContainer1.dictionary
            managedContainer1.also {
                it.nullableRealmAnyDictionaryField = it.nullableRealmAnyDictionaryField
                assertEquals(unmanagedDictionary2.size, it.nullableRealmAnyDictionaryField.size)
                assertEquals(unmanagedDictionary2["X"], it.nullableRealmAnyDictionaryField["X"])
            }
        }

        // Cleanup between assertions for convenience
        realm.writeBlocking {
            delete(query<RealmDictionaryContainer>())
        }

        // Test with a dictionary of RealmAny containing objects
        realm.writeBlocking {
            val unmanagedDictionary1 = realmDictionaryOf<RealmAny?>(
                "RANDOM1" to REALM_ANY_REALM_OBJECT,
                "RANDOM2" to REALM_ANY_REALM_OBJECT_2
            )
            val unmanagedDictionary2 = realmDictionaryOf<RealmAny?>("X" to REALM_ANY_REALM_OBJECT_3)

            val managedContainer1 = copyToRealm(RealmDictionaryContainer())
            val managedContainer2 = copyToRealm(
                RealmDictionaryContainer().apply {
                    nullableRealmAnyDictionaryField = unmanagedDictionary2
                }
            )

            // Assign an unmanaged dictionary: managedContainer1.dictionary <- unmanagedDictionary1
            managedContainer1.also {
                it.nullableRealmAnyDictionaryField = unmanagedDictionary1
                assertEquals(unmanagedDictionary1.size, it.nullableRealmAnyDictionaryField.size)

                val expectedAny1 = assertNotNull(unmanagedDictionary1["RANDOM1"])
                val expected1 = expectedAny1.asRealmObject<RealmDictionaryContainer>()
                val actualAny1 = assertNotNull(it.nullableRealmAnyDictionaryField["RANDOM1"])
                assertEquals(RealmAny.Type.OBJECT, actualAny1.type)
                val actual1 = actualAny1.asRealmObject<RealmDictionaryContainer>()
                assertEquals(expected1.stringField, actual1.stringField)

                val expectedAny2 = assertNotNull(unmanagedDictionary1["RANDOM1"])
                val expected2 = expectedAny2.asRealmObject<RealmDictionaryContainer>()
                val actualAny2 = assertNotNull(it.nullableRealmAnyDictionaryField["RANDOM1"])
                assertEquals(RealmAny.Type.OBJECT, actualAny2.type)
                val actual2 = actualAny2.asRealmObject<RealmDictionaryContainer>()
                assertEquals(expected2.stringField, actual2.stringField)
            }

            // Assign a managed dictionary: managedContainer1.dictionary <- managedDictionary2.dictionary
            managedContainer1.also {
                it.nullableRealmAnyDictionaryField =
                    managedContainer2.nullableRealmAnyDictionaryField
                assertEquals(
                    managedContainer2.nullableRealmAnyDictionaryField.size,
                    managedContainer1.nullableRealmAnyDictionaryField.size
                )
                val expectedAny = assertNotNull(managedContainer2.nullableRealmAnyDictionaryField["X"])
                val expected = expectedAny.asRealmObject<RealmDictionaryContainer>()
                val actualAny = assertNotNull(managedContainer1.nullableRealmAnyDictionaryField["X"])
                assertEquals(RealmAny.Type.OBJECT, actualAny.type)
                val actual = actualAny.asRealmObject<RealmDictionaryContainer>()
                assertEquals(expected.stringField, actual.stringField)
            }

            // Assign the same managed dictionary: managedContainer1.dictionary <- managedContainer1.dictionary
            managedContainer1.also {
                it.nullableRealmAnyDictionaryField = it.nullableRealmAnyDictionaryField
                assertEquals(unmanagedDictionary2.size, it.nullableRealmAnyDictionaryField.size)
                val expectedAny = assertNotNull(unmanagedDictionary2["X"])
                val expected = expectedAny.asRealmObject<RealmDictionaryContainer>()
                val actualAny = assertNotNull(it.nullableRealmAnyDictionaryField["X"])
                assertEquals(RealmAny.Type.OBJECT, actualAny.type)
                val actual = actualAny.asRealmObject<RealmDictionaryContainer>()
                assertEquals(expected.stringField, actual.stringField)
            }
        }
    }

    @Test
    fun closedRealm_readFails() {
        val realm = getCloseableRealm()

        // No need to be exhaustive here
        realm.writeBlocking {
            copyToRealm(RealmDictionaryContainer())
        }

        val dictionary = realm.query<RealmDictionaryContainer>()
            .first()
            .find()
            ?.byteDictionaryField
        assertNotNull(dictionary)

        // Close the realm now
        realm.close()
        assertFailsWithMessage<IllegalStateException>("Realm has been closed") {
            dictionary.size
        }
        assertFailsWithMessage<IllegalStateException>("Realm has been closed") {
            dictionary.isEmpty()
        }
        assertFailsWithMessage<IllegalStateException>("Realm has been closed") {
            dictionary["SOMETHING"]
        }
        assertFailsWithMessage<IllegalStateException>("Realm has been closed") {
            dictionary.entries
        }
        // TODO add missing containsKey, containsValue, etc.
    }

    @Test
    fun nestedObjectTest() {
        // TODO
    }

    @Test
    fun copyToRealm() {
        for (tester in managedTesters) {
            tester.copyToRealm()
        }
    }

    @Test
    fun put() {
        for (tester in managedTesters) {
            tester.put()
        }
    }

    @Test
    fun get() {
        for (tester in managedTesters) {
            tester.get()
        }
    }

    @Test
    fun clear() {
        for (tester in managedTesters) {
            tester.clear()
        }
    }

    @Test
    fun entries_size() {
        for (tester in managedTesters) {
            tester.entries_size()
        }
    }

    @Test
    fun entries_add() {
        for (tester in managedTesters) {
            tester.entries_add()
        }
    }

    @Test
    fun entries_addAll() {
        for (tester in managedTesters) {
            tester.entries_addAll()
        }
    }

    @Test
    fun entries_clear() {
        for (tester in managedTesters) {
            tester.entries_clear()
        }
    }

    @Test
    fun entries_iteratorNext() {
        for (tester in managedTesters) {
            tester.entries_iteratorNext()
        }
    }

    @Test
    fun entries_iteratorNext_managedEntry_setValue() {
        for (tester in managedTesters) {
            tester.entries_iteratorNext_managedEntry_setValue()
        }
    }

    @Test
    fun entries_iteratorRemove() {
        for (tester in managedTesters) {
            tester.entries_iteratorRemove()
        }
    }

    @Test
    fun entries_iteratorConcurrentModification() {
        for (tester in managedTesters) {
            tester.entries_iteratorConcurrentModification()
        }
    }

    @Test
    fun entries_remove() {
        for (tester in managedTesters) {
            tester.entries_remove()
        }
    }

    @Test
    fun entries_removeAll() {
        for (tester in managedTesters) {
            tester.entries_removeAll()
        }
    }

    @Test
    fun entry_equals() {
        for (tester in managedTesters) {
            tester.entry_equals()
        }
    }

    @Test
    fun values_addThrows() {
        // No need to be exhaustive here
        managedTesters[0].values_addTrows()
    }

    @Test
    fun values_clear() {
        for (tester in managedTesters) {
            tester.values_clear()
        }
    }

    @Test
    fun values_iteratorNext() {
        for (tester in managedTesters) {
            tester.values_iteratorNext()
        }
    }

    @Test
    fun values_iteratorRemove() {
        for (tester in managedTesters) {
            tester.values_iteratorRemove()
        }
    }

    @Test
    fun values_iteratorConcurrentModification() {
        for (tester in managedTesters) {
            tester.values_iteratorConcurrentModification()
        }
    }

    @Test
    fun values_remove() {
        for (tester in managedTesters) {
            tester.values_remove()
        }
    }

    @Test
    fun values_removeAll() {
        for (tester in managedTesters) {
            tester.values_removeAll()
        }
    }

    @Test
    fun values_retainAll() {
        for (tester in managedTesters) {
            tester.values_retainAll()
        }
    }

    // TODO missing in the C-API: https://github.com/realm/realm-core/issues/6181
    @Test
    fun containsKey() {
        // TODO
    }

    // TODO missing in the C-API: https://github.com/realm/realm-core/issues/6181
    @Test
    fun containsValue() {
        // TODO
    }

    private fun getCloseableRealm(): Realm =
        RealmConfiguration.Builder(schema = dictionarySchema)
            .directory(tmpDir)
            .name("closeable.realm")
            .build()
            .let {
                Realm.open(it)
            }

    private fun getTypeSafety(
        classifier: KClassifier,
        nullable: Boolean
    ): DictionaryTypeSafetyManager<*> = when (nullable) {
        true -> DictionaryTypeSafetyManager(
            property = RealmDictionaryContainer.nullableProperties[classifier]!!,
            dataSetToLoad = getDataSetForClassifier(classifier, true)
        )
        false -> DictionaryTypeSafetyManager(
            property = RealmDictionaryContainer.nonNullableProperties[classifier]!!,
            dataSetToLoad = getDataSetForClassifier(classifier, false)
        )
    }

    @Suppress("UNCHECKED_CAST", "ComplexMethod")
    // TODO add support for Decimal128
    private fun <T> getDataSetForClassifier(
        classifier: KClassifier,
        nullable: Boolean
    ): List<T> = when (classifier) {
        Byte::class -> if (nullable) {
            NULLABLE_BYTE_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            BYTE_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        }
        Char::class -> if (nullable) {
            NULLABLE_CHAR_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            CHAR_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        }
        Short::class -> if (nullable) {
            NULLABLE_SHORT_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            SHORT_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        }
        Int::class -> if (nullable) {
            NULLABLE_INT_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            INT_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        }
        Long::class -> if (nullable) {
            NULLABLE_LONG_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            LONG_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
        }
        Boolean::class -> if (nullable) {
            NULLABLE_BOOLEAN_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            BOOLEAN_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
        }
        Float::class -> if (nullable) {
            NULLABLE_FLOAT_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            FLOAT_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
        }
        Double::class -> if (nullable) {
            NULLABLE_DOUBLE_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            DOUBLE_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
        }
        String::class -> if (nullable) {
            NULLABLE_STRING_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            STRING_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
        }
        RealmInstant::class -> if (nullable) {
            NULLABLE_TIMESTAMP_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            TIMESTAMP_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
        }
        ObjectId::class -> if (nullable) {
            NULLABLE_OBJECT_ID_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            OBJECT_ID_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
        }
        BsonObjectId::class -> if (nullable) {
            NULLABLE_BSON_OBJECT_ID_VALUES.mapIndexed { i, value ->
                Pair(KEYS_FOR_NULLABLE[i], value)
            }
        } else {
            BSON_OBJECT_ID_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
        }
        RealmUUID::class -> if (nullable) {
            NULLABLE_UUID_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            UUID_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
        }
        ByteArray::class -> if (nullable) {
            NULLABLE_BINARY_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            BINARY_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
        }
        RealmObject::class -> if (nullable) {
            DICTIONARY_OBJECT_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            NULLABLE_DICTIONARY_OBJECT_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
        }
        Decimal128::class -> if (nullable) {
            NULLABLE_DECIMAL128_VALUES.mapIndexed { i, value -> Pair(KEYS_FOR_NULLABLE[i], value) }
        } else {
            DECIMAL128_VALUES.mapIndexed { i, value -> Pair(KEYS[i], value) }
        }
        RealmAny::class -> {
            val anyValues = REALM_ANY_PRIMITIVE_VALUES + REALM_ANY_REALM_OBJECT

            // Generate as many keys as RealmAny values
            var key = 'A'
            val keys = anyValues.map { key.also { key += 1 } }

            // Now create pairs of key-RealmAny for the dataset
            anyValues.mapIndexed { i, value -> Pair(keys[i].toString(), value) }
        }
        else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
    } as List<T>
}

/**
 * Tester interface defining the operations that have to be tested exhaustively.
 */
internal interface DictionaryApiTester<T, Container> : ErrorCatcher {

    val realm: Realm

    override fun toString(): String
    fun copyToRealm()
    fun put()
    fun get()
    fun clear()
    fun entries_size()
    fun entries_add()
    fun entries_addAll()
    fun entries_clear()
    fun entries_iteratorNext() // This tests also hasNext
    fun entries_iteratorNext_managedEntry_setValue()
    fun entries_iteratorRemove()
    fun entries_iteratorConcurrentModification()
    fun entries_remove()
    fun entries_removeAll()
    fun entry_equals()
    fun values_addTrows()
    fun values_clear()
    fun values_iteratorNext() // This tests also hasNext
    fun values_iteratorRemove() // This tests also hasNext
    fun values_iteratorConcurrentModification()
    fun values_remove()
    fun values_removeAll()
    fun values_retainAll()

    /**
     * Asserts structural equality for two given collections. This is needed to evaluate equality
     * contents of ByteArrays and RealmObjects.
     */
    fun assertStructuralEquality(
        expectedPairs: List<Pair<String, T>>,
        actualValues: Map<String, T>
    )

    /**
     * Asserts structural equality for two given values.
     */
    fun assertStructuralEquality(expectedValue: T?, actualValue: T?)

    /**
     * Assertions on the container outside the write transaction plus cleanup.
     */
    fun assertContainerAndCleanup(assertion: ((Container) -> Unit)? = null)
}

internal abstract class ManagedDictionaryTester<T>(
    override val realm: Realm,
    private val typeSafetyManager: DictionaryTypeSafetyManager<T>,
    override val classifier: KClassifier
) : DictionaryApiTester<T, RealmDictionaryContainer> {

    override fun toString(): String = classifier.toString()

    override fun copyToRealm() {
        val dataSet = typeSafetyManager.dataSetToLoad

        val assertions = { container: RealmDictionaryContainer ->
            val actualValues = typeSafetyManager.getCollection(container)
            assertStructuralEquality(dataSet, actualValues)
        }

        errorCatcher {
            val container = typeSafetyManager.createPrePopulatedContainer()

            realm.writeBlocking {
                val managedContainer = copyToRealm(container)
                assertions(managedContainer)
            }
        }

        assertContainerAndCleanup { container -> assertions(container) }
    }

    override fun put() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dataSet.forEachIndexed { index, t ->
                    assertEquals(index, dictionary.size)
                    dictionary[t.first] = t.second
                    assertEquals(index + 1, dictionary.size)
                }
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            assertStructuralEquality(dataSet, dictionary)
        }
    }

    override fun get() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)

                dataSet.forEachIndexed { index, t ->
                    assertEquals(index, dictionary.size)
                    dictionary[t.first] = t.second
                    assertEquals(index + 1, dictionary.size)
                }
                // Check operation inside a transaction
                assertStructuralEquality(dataSet, dictionary)
            }

            // Also outside a transaction
            realm.query<RealmDictionaryContainer>()
                .find { results ->
                    val managedDictionary = typeSafetyManager.getCollection(results.first())
                    assertStructuralEquality(dataSet, managedDictionary)
                }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            assertStructuralEquality(dataSet, dictionary)
        }
    }

    override fun clear() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                assertEquals(dataSet.size, dictionary.size)
                dictionary.clear()
                assertTrue(dictionary.isEmpty())
            }
        }

        assertContainerAndCleanup { container ->
            assertTrue(typeSafetyManager.getCollection(container).isEmpty())
        }
    }

    override fun entries_size() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                assertTrue(dictionary.entries.isEmpty())

                dictionary.putAll(dataSet)

                val entries = dictionary.entries
                assertEquals(dictionary.size, dataSet.size)
                assertEquals(dictionary.size, entries.size)
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val entries = dictionary.entries
            assertEquals(dictionary.size, dataSet.size)
            assertEquals(dictionary.size, entries.size)
        }
    }

    override fun entries_add() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val entries = dictionary.entries
                assertTrue(entries.add(realmDictionaryEntryOf("REALM", dataSet[0].second)))
                assertEquals(dictionary.size, entries.size)

                // Adding the same element returns false
                assertFalse(entries.add(realmDictionaryEntryOf("REALM", dataSet[0].second)))
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val entries = dictionary.entries
            assertEquals(dictionary.size, entries.size)
        }
    }

    override fun entries_addAll() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val entries = dictionary.entries

                // Reuse same dataSet values, just use different keys. Then add it to the entry set
                val newDataSet = listOf(
                    realmDictionaryEntryOf("REALM-1" to dataSet[0].second),
                    realmDictionaryEntryOf("REALM-2" to dataSet[0].second),
                )
                assertTrue(entries.addAll(newDataSet))
                assertEquals(dictionary.size, entries.size)

                // Adding the same elements returns false
                assertFalse(entries.addAll(newDataSet))
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val entries = dictionary.entries
            assertEquals(dictionary.size, entries.size)
        }
    }

    override fun entries_clear() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val entries = dictionary.entries
                entries.clear()
                assertTrue(entries.isEmpty())
                assertTrue(dictionary.isEmpty())
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val entries = dictionary.entries
            assertTrue(dictionary.isEmpty())
            assertTrue(entries.isEmpty())
        }
    }

    override fun entries_iteratorNext() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val iterator = dictionary.entries.iterator()
                for (i in dataSet.indices) {
                    assertTrue(iterator.hasNext())
                    val next = iterator.next()
                    assertNotNull(next)
                    assertEquals(dataSet[i].first as T?, next.key as T?)
                    assertStructuralEquality(dataSet[i].second, next.value)
                }
                assertFalse(iterator.hasNext())
                assertFailsWithMessage<IndexOutOfBoundsException>("Cannot access index") {
                    iterator.next()
                }
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val iterator = dictionary.entries.iterator()
            for (i in dataSet.indices) {
                assertTrue(iterator.hasNext())
                val next = iterator.next()
                assertNotNull(next)
                assertEquals(dataSet[i].first as T?, next.key as T?)
                assertStructuralEquality(dataSet[i].second, next.value)
            }
            assertFalse(iterator.hasNext())
            assertFailsWithMessage<IndexOutOfBoundsException>("Cannot access index") {
                iterator.next()
            }
        }
    }

    override fun entries_iteratorNext_managedEntry_setValue() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val iterator = dictionary.entries.iterator()
                val nextEntry: MutableMap.MutableEntry<String, T> = iterator.next()
                val expectedPreviousValue = nextEntry.value
                val actualPreviousValue = nextEntry.setValue(dataSet[1].second)
                assertStructuralEquality(expectedPreviousValue, actualPreviousValue)

                val expected = dataSet[1].second
                val actual = dictionary[dataSet[1].first]
                assertStructuralEquality(expected, actual)
            }
        }

        // No need to test during cleanup since we can only modify a dictionary while running a
        // transaction and that has already been tested above
        assertContainerAndCleanup()
    }

    override fun entries_iteratorRemove() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val entries = dictionary.entries
                val iterator = entries.iterator()

                // Fails when calling remove before calling next
                assertTrue(iterator.hasNext())
                assertFailsWithMessage<IllegalStateException>("Could not remove last element returned by the iterator: iterator never returned an element.") {
                    iterator.remove()
                }
                assertTrue(iterator.hasNext())

                for (i in dataSet.indices) {
                    assertEquals(dataSet.size - i, entries.size)
                    val next = iterator.next()
                    assertNotNull(next)
                    iterator.remove()
                    assertEquals(dictionary.size, entries.size)
                }
                assertTrue(entries.isEmpty())
                assertTrue(dictionary.isEmpty())

                assertFailsWithMessage<NoSuchElementException>("Could not remove last element returned by the iterator: set is empty.") {
                    iterator.remove()
                }
            }
        }

        assertContainerAndCleanup { container ->
            typeSafetyManager.getCollection(container)
                .entries
                .iterator()
                .also { iterator ->
                    // Dictionary is empty
                    assertFalse(iterator.hasNext())
                    assertFailsWith<NoSuchElementException> {
                        iterator.remove()
                    }
                }

            // Add entries to the dictionary and check iterator().remove() outside transaction fails
            val latestContainer = realm.writeBlocking {
                val latestContainer = assertNotNull(findLatest(container))
                val dictionary = typeSafetyManager.getCollection(latestContainer)
                dictionary.putAll(dataSet)
                latestContainer
            }
            typeSafetyManager.getCollection(latestContainer)
                .entries
                .iterator()
                .also { iterator ->
                    iterator.next()

                    // TODO revisit exception assertion once unified error handling is merged
                    assertFailsWith<RealmException> {
                        iterator.remove()
                    }
                }
        }
    }

    override fun entries_iteratorConcurrentModification() {
        // Ignore ByteArray and RealmObject: structural equality cannot be assessed for these types
        // when removing entries from the entry set
        if (classifier != ByteArray::class && classifier != RealmObject::class) {
            val dataSet = typeSafetyManager.dataSetToLoad

            errorCatcher {
                realm.writeBlocking {
                    val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                    dictionary.putAll(dataSet)
                    dictionary.entries.also { entries ->
                        // Dictionary: add something to get a ConcurrentModificationException
                        val addIterator = entries.iterator()
                        addIterator.next()
                        dictionary["SOMETHING_NEW"] = dataSet[0].second
                        assertFailsWith<ConcurrentModificationException> {
                            addIterator.remove()
                        }

                        // Dictionary: remove something to get a ConcurrentModificationException
                        val removeIterator = entries.iterator()
                        removeIterator.next()
                        dictionary.remove("SOMETHING_NEW")
                        assertFailsWith<ConcurrentModificationException> {
                            removeIterator.remove()
                        }

                        // Dictionary: clear to get a ConcurrentModificationException
                        val clearIterator = entries.iterator()
                        clearIterator.next()
                        dictionary.clear()
                        assertFailsWith<ConcurrentModificationException> {
                            clearIterator.remove()
                        }
                    }

                    // putAll elements and test again with entry set
                    dictionary.putAll(dataSet)
                    dictionary.entries.also { entries ->
                        // Entries: add something to get a ConcurrentModificationException
                        val addIterator = entries.iterator()
                        addIterator.next()
                        entries.add(realmDictionaryEntryOf("SOMETHING" to dataSet[0].second))
                        assertFailsWith<ConcurrentModificationException> {
                            addIterator.remove()
                        }

                        // Entries: remove something to get a ConcurrentModificationException
                        val removeIterator = entries.iterator()
                        removeIterator.next()
                        entries.remove(realmDictionaryEntryOf("SOMETHING" to dataSet[0].second))
                        assertFailsWith<ConcurrentModificationException> {
                            removeIterator.remove()
                        }

                        // Entries: clear to get a ConcurrentModificationException
                        val clearIterator = entries.iterator()
                        clearIterator.next()
                        entries.clear()
                        assertFailsWith<ConcurrentModificationException> {
                            clearIterator.remove()
                        }
                    }
                }
            }

            // Makes no sense to test concurrent modifications outside the transaction
            assertContainerAndCleanup()
        }
    }

    override fun entries_remove() {
        // Ignore ByteArray and RealmObject: structural equality cannot be assessed for these types
        // when removing entries from the entry set
        if (classifier != ByteArray::class && classifier != RealmObject::class) {
            val dataSet = typeSafetyManager.dataSetToLoad

            errorCatcher {
                realm.writeBlocking {
                    val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                    dictionary.putAll(dataSet)
                    val entries = dictionary.entries

                    // This entry's value doesn't match what is in the dictionary
                    val bogusEntryToRemove =
                        realmDictionaryEntryOf(dataSet[0].first, dataSet[1].second)
                    assertFalse(entries.remove(bogusEntryToRemove))

                    // This entry is present in the dictionary and results in a deletion
                    val entryToRemove = realmDictionaryEntryOf(dataSet[0].first, dataSet[0].second)

                    // Check we get true after removing an element
                    assertTrue(entries.remove(entryToRemove))
                    assertEquals(dictionary.size, entries.size)

                    // Check we get false if we don't remove anything
                    assertFalse(entries.remove(entryToRemove))
                }
            }

            assertContainerAndCleanup { container ->
                val entries = typeSafetyManager.getCollection(container)
                    .entries

                // Removing something that isn't there won't throw an exception
                val alreadyDeleted = realmDictionaryEntryOf(dataSet[0].first, dataSet[0].second)
                assertFalse(entries.remove(alreadyDeleted))

                // Removing something that is the dictionary throws outside a transaction
                // TODO revisit exception assertion once unified error handling is merged
                assertFailsWith<RealmException> {
                    entries.remove(realmDictionaryEntryOf(dataSet[1].first, dataSet[1].second))
                }
            }
        }
    }

    override fun entries_removeAll() {
        // Ignore ByteArray and RealmObject: structural equality cannot be assessed for these types
        // when removing entries from the entry set
        if (classifier != ByteArray::class && classifier != RealmObject::class) {
            val dataSet = typeSafetyManager.dataSetToLoad

            errorCatcher {
                realm.writeBlocking {
                    val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                    dictionary.putAll(dataSet)
                    val entries = dictionary.entries

                    // This list of entries contains an entry whose value doesn't match what is in
                    // the dictionary
                    val bogusEntriesToRemove = listOf(
                        realmDictionaryEntryOf(dataSet[0].first, dataSet[1].second)
                    )
                    assertFalse(entries.removeAll(bogusEntriesToRemove))

                    // This list of entries contains an entry that is present in the dictionary and
                    // another one that isn't and it returns true anyway since something got removed
                    val entriesToRemove = listOf(
                        realmDictionaryEntryOf(dataSet[0].first, dataSet[0].second),
                        realmDictionaryEntryOf(dataSet[0].first, dataSet[1].second)
                    )
                    assertTrue(entries.removeAll(entriesToRemove))
                    assertEquals(dictionary.size, entries.size)

                    // Check we get false if we don't remove anything
                    assertFalse(entries.removeAll(entriesToRemove))
                }
            }

            assertContainerAndCleanup { container ->
                val entries = typeSafetyManager.getCollection(container)
                    .entries

                val alreadyDeleted = listOf(
                    realmDictionaryEntryOf(dataSet[0].first, dataSet[0].second)
                )
                assertFalse(entries.removeAll(alreadyDeleted))

                val notPresent = listOf(
                    realmDictionaryEntryOf(dataSet[0].first, dataSet[0].second)
                )
                assertFalse(entries.removeAll(notPresent))

                val shouldThrow = listOf(
                    realmDictionaryEntryOf(dataSet[1].first, dataSet[1].second),
                )
                // TODO revisit exception assertion once unified error handling is merged
                assertFailsWith<RealmException> {
                    entries.removeAll(shouldThrow)
                }
            }
        }
    }

    override fun entry_equals() {
        // Ignore ByteArray and RealmObject: structural equality cannot be assessed for these types
        if (classifier != ByteArray::class && classifier != RealmObject::class) {
            val dataSet = typeSafetyManager.dataSetToLoad

            errorCatcher {
                // Test unmanaged entry equals
                val entry1 = realmDictionaryEntryOf(dataSet[0].first, dataSet[0].second)
                val entry2 = realmDictionaryEntryOf(dataSet[0].first, dataSet[0].second)
                val entry3 = realmDictionaryEntryOf(dataSet[1].first, dataSet[1].second)
                assertEquals(entry1, entry2)
                assertNotEquals(entry1, entry3)

                realm.writeBlocking {
                    val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                    dictionary.putAll(dataSet)
                    val iterator = dictionary.entries.iterator()
                    val managedEntry1 = iterator.next()
                    val managedEntry2 = iterator.next()
                    assertEquals(managedEntry1, managedEntry1)
                    assertNotEquals(managedEntry1, managedEntry2)
                }
            }

            assertContainerAndCleanup { container ->
                val iterator = typeSafetyManager.getCollection(container)
                    .entries
                    .iterator()
                val managedEntry1 = iterator.next()
                val managedEntry2 = iterator.next()
                assertEquals(managedEntry1, managedEntry1)
                assertNotEquals(managedEntry1, managedEntry2)
            }
        }
    }

    override fun values_addTrows() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                val values = dictionary.values
                assertFailsWithMessage<UnsupportedOperationException>("Adding values to a dictionary through 'dictionary.values' is not allowed") {
                    values.add(dataSet[0].second)
                }
                assertFailsWithMessage<UnsupportedOperationException>("Adding values to a dictionary through 'dictionary.values' is not allowed") {
                    values.addAll(listOf(dataSet[0].second))
                }
                assertTrue(dictionary.isEmpty())
                assertTrue(values.isEmpty())
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val values = dictionary.values
            assertTrue(dictionary.isEmpty())
            assertTrue(values.isEmpty())
        }
    }

    override fun values_clear() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val values = dictionary.values
                values.clear()
                assertTrue(values.isEmpty())
                assertTrue(dictionary.isEmpty())
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val values = dictionary.values
            // TODO revisit exception assertion once unified error handling is merged
            assertFailsWith<RealmException> {
                values.clear()
            }
            assertTrue(values.isEmpty())
            assertTrue(dictionary.isEmpty())
        }
    }

    override fun values_iteratorNext() {
        val dataSet = typeSafetyManager.dataSetToLoad
        val assertions = { values: MutableCollection<T> ->
            val iterator = values.iterator()
            for (i in dataSet.indices) {
                assertTrue(iterator.hasNext())
                val next = iterator.next()
                assertStructuralEquality(dataSet[i].second, next)
            }
            assertFalse(iterator.hasNext())
            assertFailsWithMessage<IndexOutOfBoundsException>("Cannot access index") {
                iterator.next()
            }
        }
        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)

                // Test iterator on an empty dictionary
                val iterator = dictionary.values.iterator()
                assertFalse(iterator.hasNext())
                assertFailsWithMessage<IndexOutOfBoundsException>("Cannot access index") {
                    iterator.next()
                }

                dictionary.putAll(dataSet)
                assertions.invoke(dictionary.values)
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            assertions.invoke(dictionary.values)
        }
    }

    override fun values_iteratorRemove() {
        val dataSet = typeSafetyManager.dataSetToLoad
        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val values = dictionary.values
                val iterator = values.iterator()

                // Fails when calling remove before calling next
                assertTrue(iterator.hasNext())
                assertFailsWithMessage<IllegalStateException>("Could not remove last element returned by the iterator: iterator never returned an element.") {
                    iterator.remove()
                }
                assertTrue(iterator.hasNext())

                for (i in dataSet.indices) {
                    assertEquals(dataSet.size - i, values.size)
                    val next = iterator.next()
                    assertStructuralEquality(dataSet[i].second, next)
                    iterator.remove()
                    assertEquals(dictionary.size, values.size)
                }
                assertTrue(values.isEmpty())
                assertTrue(dictionary.isEmpty())

                assertFailsWithMessage<NoSuchElementException>("Could not remove last element returned by the iterator: set is empty.") {
                    iterator.remove()
                }
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val values = dictionary.values
            // TODO revisit exception assertion once unified error handling is merged
            assertFailsWith<RealmException> {
                values.clear()
            }
            assertTrue(values.isEmpty())
            assertTrue(dictionary.isEmpty())
        }
    }

    override fun values_iteratorConcurrentModification() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                dictionary.values.also { values ->
                    // Add something to the dictionary to trigger a ConcurrentModificationException
                    val addIterator = values.iterator()
                    addIterator.next()
                    dictionary["SOMETHING_NEW"] = dataSet[0].second
                    assertFailsWith<ConcurrentModificationException> {
                        addIterator.remove()
                    }

                    // Remove something from the dictionary to trigger a ConcurrentModificationException
                    val removeIterator = values.iterator()
                    removeIterator.next()
                    dictionary.remove("SOMETHING_NEW")
                    assertFailsWith<ConcurrentModificationException> {
                        removeIterator.remove()
                    }

                    // Clear the dictionary to trigger a ConcurrentModificationException
                    val clearIterator = values.iterator()
                    clearIterator.next()
                    dictionary.clear()
                    assertFailsWith<ConcurrentModificationException> {
                        clearIterator.remove()
                    }
                }

                // Dictionary is empty now, putAll elements and test again with values - remember additions are not allowed
                dictionary.putAll(dataSet)
                dictionary["SOMETHING_NEW"] = dataSet[0].second
                dictionary.values.also { values ->
                    // Ignore ByteArray and RealmObject: they cannot be removed using the remove API
                    if (classifier != ByteArray::class && classifier != RealmObject::class) {
                        // Remove something from the entry set to trigger a ConcurrentModificationException
                        val removeIterator = values.iterator()
                        removeIterator.next()
                        values.remove(dataSet[0].second)
                        assertFailsWith<ConcurrentModificationException> {
                            removeIterator.remove()
                        }
                    }

                    // Clear the entry set to trigger a ConcurrentModificationException
                    val clearIterator = values.iterator()
                    clearIterator.next()
                    values.clear()
                    assertFailsWith<ConcurrentModificationException> {
                        clearIterator.remove()
                    }
                }
            }
        }

        // Makes no sense to test concurrent modifications outside the transaction, so clean up only
        assertContainerAndCleanup()
    }

    override fun values_remove() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val values = dictionary.values
                val valueToRemove = dataSet[0].second

                // Ignore ByteArray and RealmObject: they cannot be removed using the remove API
                if (classifier != ByteArray::class && classifier != RealmObject::class) {
                    // Check we get true after removing an element
                    assertTrue(values.remove(valueToRemove))
                    assertEquals(dictionary.size, values.size)
                    assertEquals(dataSet.size - 1, values.size)
                    assertEquals(dataSet.size - 1, dictionary.size)

                    // Check we get false if we don't remove anything
                    assertFalse(values.remove(valueToRemove))
                }
            }
        }

        assertContainerAndCleanup { container ->
            val values = typeSafetyManager.getCollection(container)
                .values

            // Ignore ByteArray and RealmObject: they cannot be removed using the remove API
            if (classifier != ByteArray::class && classifier != RealmObject::class) {
                // TODO revisit exception assertion once unified error handling is merged
                assertFailsWith<RealmException> {
                    values.remove(dataSet[1].second)
                }
            }
        }
    }

    override fun values_removeAll() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val values = dictionary.values
                val valuesToRemove = listOf(dataSet[0].second)

                // Ignore ByteArray and RealmObject: they cannot be removed using the removeAll API
                if (classifier != ByteArray::class && classifier != RealmObject::class) {
                    // Check we get true after removing an element
                    assertTrue(values.removeAll(valuesToRemove))
                    assertEquals(dictionary.size, values.size)
                    assertEquals(dataSet.size - valuesToRemove.size, values.size)
                    assertEquals(dataSet.size - valuesToRemove.size, values.size)

                    // Check we get false if we don't remove anything
                    assertFalse(values.removeAll(valuesToRemove))
                }
            }
        }

        assertContainerAndCleanup { container ->
            val values = typeSafetyManager.getCollection(container)
                .values

            // Ignore ByteArray and RealmObject: they cannot be removed using the removeAll API
            if (classifier != ByteArray::class && classifier != RealmObject::class) {
                // TODO revisit exception assertion once unified error handling is merged
                assertFailsWith<RealmException> {
                    values.removeAll(values)
                }
            }
        }
    }

    override fun values_retainAll() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val values = dictionary.values
                val valuesToIntersect = listOf(dataSet[0].second)

                // Ignore ByteArray and RealmObject: they cannot be removed using the retainAll API
                if (classifier != ByteArray::class && classifier != RealmObject::class) {
                    // Check we get true after removing an element
                    assertTrue(values.retainAll(valuesToIntersect))
                    assertEquals(dictionary.size, values.size)
                    assertEquals(valuesToIntersect.size, values.size)

                    // Check we get false if we don't intersect anything
                    assertFalse(values.retainAll(valuesToIntersect))
                }
            }
        }

        assertContainerAndCleanup { container ->
            val values = typeSafetyManager.getCollection(container)
                .values

            // Ignore ByteArray and RealmObject: they cannot be removed using the retainAll API
            if (classifier != ByteArray::class && classifier != RealmObject::class) {
                assertFalse(values.retainAll(values))
            }
        }
    }

    override fun assertContainerAndCleanup(assertion: ((RealmDictionaryContainer) -> Unit)?) {
        val container = realm.query<RealmDictionaryContainer>()
            .first()
            .find()
        assertNotNull(container)

        // Assert
        errorCatcher {
            assertion?.invoke(container)
        }

        // Clean up
        realm.writeBlocking {
            delete(query<RealmDictionaryContainer>())
        }
    }
}

/**
 * Tester for generic types.
 */
internal class GenericDictionaryTester<T>(
    realm: Realm,
    typeSafetyManager: DictionaryTypeSafetyManager<T>,
    classifier: KClassifier
) : ManagedDictionaryTester<T>(realm, typeSafetyManager, classifier) {
    override fun assertStructuralEquality(
        expectedPairs: List<Pair<String, T>>,
        actualValues: Map<String, T>
    ) {
        assertEquals(expectedPairs.size, actualValues.size)
        expectedPairs.forEach {
            assertEquals(it.second, actualValues[it.first])
        }
    }

    override fun assertStructuralEquality(expectedValue: T?, actualValue: T?) {
        assertEquals(expectedValue, actualValue)
    }
}

/**
 * Tester for ByteArray.
 */
internal class ByteArrayDictionaryTester(
    realm: Realm,
    typeSafetyManager: DictionaryTypeSafetyManager<ByteArray>,
    classifier: KClassifier
) : ManagedDictionaryTester<ByteArray>(realm, typeSafetyManager, classifier) {
    override fun assertStructuralEquality(
        expectedPairs: List<Pair<String, ByteArray>>,
        actualValues: Map<String, ByteArray>
    ) {
        assertEquals(expectedPairs.size, actualValues.size)
        expectedPairs.forEach {
            assertContentEquals(it.second, actualValues[it.first])
        }
    }

    override fun assertStructuralEquality(expectedValue: ByteArray?, actualValue: ByteArray?) {
        assertContentEquals(expectedValue, actualValue)
    }
}

/**
 * Tester for RealmAny.
 */
internal class RealmAnyDictionaryTester(
    realm: Realm,
    typeSafetyManager: DictionaryTypeSafetyManager<RealmAny?>,
    classifier: KClassifier
) : ManagedDictionaryTester<RealmAny?>(realm, typeSafetyManager, classifier) {
    override fun assertStructuralEquality(
        expectedPairs: List<Pair<String, RealmAny?>>,
        actualValues: Map<String, RealmAny?>
    ) {
        assertEquals(expectedPairs.size, actualValues.size)
        actualValues.forEach { (actualKey, actualValue) ->
            val expectedKeys = expectedPairs.map { it.first }
            val expectedValues = expectedPairs.map { it.second }
            when (actualValue?.type) {
                RealmAny.Type.OBJECT -> {
                    val expectedObject = expectedValues.find {
                        it?.type == RealmAny.Type.OBJECT
                    }?.asRealmObject<RealmDictionaryContainer>()
                    val actualObject = actualValue.asRealmObject<RealmDictionaryContainer>()
                    assertEquals(expectedObject?.stringField, actualObject.stringField)
                }
                RealmAny.Type.BINARY -> {
                    val expectedByteArray = expectedValues.find {
                        it?.type == RealmAny.Type.BINARY
                    }?.asByteArray()
                    val actualByteArray = actualValue.asByteArray()
                    assertContentEquals(expectedByteArray, actualByteArray)
                }
                else -> {
                    assertTrue(expectedKeys.contains(actualKey))
                    assertTrue(expectedValues.contains(actualValue))
                }
            }
        }
    }

    override fun assertStructuralEquality(
        expectedValue: RealmAny?,
        actualValue: RealmAny?
    ) {
        assertEquals(expectedValue?.type, actualValue?.type)
        when (expectedValue?.type) {
            RealmAny.Type.INT -> assertEquals(expectedValue.asInt(), actualValue?.asInt())
            RealmAny.Type.BOOL -> assertEquals(expectedValue.asBoolean(), actualValue?.asBoolean())
            RealmAny.Type.STRING -> assertEquals(expectedValue.asString(), actualValue?.asString())
            RealmAny.Type.BINARY -> assertContentEquals(expectedValue.asByteArray(), actualValue?.asByteArray())
            RealmAny.Type.TIMESTAMP -> assertEquals(expectedValue.asRealmInstant(), actualValue?.asRealmInstant())
            RealmAny.Type.FLOAT -> assertEquals(expectedValue.asFloat(), actualValue?.asFloat())
            RealmAny.Type.DOUBLE -> assertEquals(expectedValue.asDouble(), actualValue?.asDouble())
            RealmAny.Type.DECIMAL128 -> assertEquals(expectedValue.asDecimal128(), actualValue?.asDecimal128())
            RealmAny.Type.OBJECT_ID -> assertEquals(expectedValue.asObjectId(), actualValue?.asObjectId())
            RealmAny.Type.UUID -> assertEquals(expectedValue.asRealmUUID(), actualValue?.asRealmUUID())
            RealmAny.Type.OBJECT -> {
                val expectedObj = expectedValue.asRealmObject<RealmDictionaryContainer>()
                val actualObj = actualValue?.asRealmObject<RealmDictionaryContainer>()
                assertEquals(expectedObj.stringField, assertNotNull(actualObj).stringField)
            }
            null -> assertNull(actualValue)
        }
    }
}

/**
 * Tester for RealmObject.
 */
internal class RealmObjectDictionaryTester(
    realm: Realm,
    typeSafetyManager: DictionaryTypeSafetyManager<RealmDictionaryContainer>,
    classifier: KClassifier
) : ManagedDictionaryTester<RealmDictionaryContainer>(realm, typeSafetyManager, classifier) {
    override fun assertStructuralEquality(
        expectedPairs: List<Pair<String, RealmDictionaryContainer>>,
        actualValues: Map<String, RealmDictionaryContainer>
    ) {
        assertEquals(expectedPairs.size, actualValues.size)
        assertContentEquals(
            expectedPairs.map { it.second.stringField },
            actualValues.map { it.value.stringField }
        )
    }

    override fun assertStructuralEquality(
        expectedValue: RealmDictionaryContainer?,
        actualValue: RealmDictionaryContainer?
    ) {
        assertEquals(expectedValue?.stringField, actualValue?.stringField)
    }
}

/**
 * Dataset container for RealmDictionary, can be either nullable or non-nullable.
 */
internal class DictionaryTypeSafetyManager<T> constructor(
    override val property: KMutableProperty1<RealmDictionaryContainer, RealmDictionary<T>>,
    override val dataSetToLoad: List<Pair<String, T>>
) : GenericTypeSafetyManager<Pair<String, T>, RealmDictionaryContainer, RealmDictionary<T>> {

    override fun toString(): String = property.name

    override fun getCollection(container: RealmDictionaryContainer): RealmDictionary<T> =
        property.get(container)

    override fun createContainerAndGetCollection(realm: MutableRealm): RealmDictionary<T> {
        val container = RealmDictionaryContainer().let {
            realm.copyToRealm(it)
        }
        return property.get(container)
            .also { dictionary ->
                assertNotNull(dictionary)
                assertTrue(dictionary.isEmpty())
            }
    }

    override fun createPrePopulatedContainer(): RealmDictionaryContainer {
        return RealmDictionaryContainer().also {
            property.get(it)
                .apply {
                    putAll(dataSetToLoad)
                }
        }
    }
}

val KEYS = listOf("A", "B")
val KEYS_FOR_NULLABLE = KEYS + "C"

internal val DICTIONARY_OBJECT_VALUES = listOf(
    RealmDictionaryContainer().apply { stringField = "A" },
    RealmDictionaryContainer().apply { stringField = "B" }
)
private val REALM_ANY_REALM_OBJECT = RealmAny.create(
    RealmDictionaryContainer().apply { stringField = "hello" },
    RealmDictionaryContainer::class
)
private val REALM_ANY_REALM_OBJECT_2 = RealmAny.create(
    RealmDictionaryContainer().apply { stringField = "hello_2" },
    RealmDictionaryContainer::class
)
private val REALM_ANY_REALM_OBJECT_3 = RealmAny.create(
    RealmDictionaryContainer().apply { stringField = "hello_3" },
    RealmDictionaryContainer::class
)

private val DICTIONARY_REALM_ANY_VALUES = REALM_ANY_PRIMITIVE_VALUES + REALM_ANY_REALM_OBJECT

internal val NULLABLE_DICTIONARY_OBJECT_VALUES = DICTIONARY_OBJECT_VALUES + null

// TODO add circular dependency data and tests
