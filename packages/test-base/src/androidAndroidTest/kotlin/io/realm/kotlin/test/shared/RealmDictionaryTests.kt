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
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.toRealmDictionary
import io.realm.kotlin.query.find
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.ErrorCatcher
import io.realm.kotlin.test.GenericTypeSafetyManager
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealmDictionaryTests {

    private val descriptors = TypeDescriptor.allDictionaryFieldTypes

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private val managedTesters: List<DictionaryApiTester<*, RealmDictionaryContainer>> by lazy {
        // TODO use mapNotNull to introduce each supported type progressively and return null for
        //  those types that remain unsupported - remove this hack when all types are supported
        descriptors.mapNotNull {
            val elementType = it.elementType
            when (val classifier = elementType.classifier) {
                RealmAny::class -> null
                RealmObject::class -> RealmObjectDictionaryTester(
                    realm,
                    getTypeSafety(
                        classifier,
                        elementType.nullable
                    ) as DictionaryTypeSafetyManager<RealmDictionaryContainer>,
                    classifier
                )
                ByteArray::class -> ByteArrayTester(
                    realm,
                    getTypeSafety(
                        classifier,
                        elementType.nullable
                    ) as DictionaryTypeSafetyManager<ByteArray>,
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
        val configuration = RealmConfiguration.Builder(
            schema = setOf(RealmDictionaryContainer::class)
        ).directory(tmpDir)
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
        // TODO will be added when support for dictionary.entries is added
    }

    @Test
    fun realmDictionaryInitializer_realmDictionaryOf_fromVarargs() {
        // No need to be exhaustive here
        val realmDictionaryFromArgsEmpty: RealmDictionary<String> = realmDictionaryOf()
        assertTrue(realmDictionaryFromArgsEmpty.isEmpty())

        val args = listOf("A" to "1", "B" to "2").toTypedArray()
        val realmDictionaryFromArgs: RealmDictionary<String> = realmDictionaryOf(*args)

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
        val realmDictionaryFromList: RealmDictionary<String> = realmDictionaryOf(args)

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
        // TODO will be added when support for dictionary.entries is added
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
        // TODO
    }

    @Test
    fun entries_add() {
        // TODO
    }

    @Test
    fun entries_addAll() {
        // TODO
    }

    @Test
    fun entries_clear() {
        // TODO
    }

    @Test
    fun entries_iteratorNext() {
        // TODO
    }

    @Test
    fun entries_iteratorRemove() {
        // TODO
    }

    @Test
    fun entries_remove() {
        // TODO
    }

    @Test
    fun entries_removeAll() {
        // TODO
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
                Pair(
                    KEYS_FOR_NULLABLE[i],
                    value
                )
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
//        RealmAny::class -> SET_REALM_ANY_VALUES // RealmAny cannot be non-nullable
//        else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
        else -> TODO("Missing classifier for '$classifier'")
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

    /**
     * Asserts structural equality for two given collections. This is needed to evaluate equality
     * contents of ByteArrays and RealmObjects.
     */
    fun assertStructuralEquality(
        expectedValues: List<Pair<String, T>>,
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
        expectedValues: List<Pair<String, T>>,
        actualValues: Map<String, T>
    ) {
        assertEquals(expectedValues.size, actualValues.size)
        expectedValues.forEach {
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
internal class ByteArrayTester(
    realm: Realm,
    typeSafetyManager: DictionaryTypeSafetyManager<ByteArray>,
    classifier: KClassifier
) : ManagedDictionaryTester<ByteArray>(realm, typeSafetyManager, classifier) {
    override fun assertStructuralEquality(
        expectedValues: List<Pair<String, ByteArray>>,
        actualValues: Map<String, ByteArray>
    ) {
        assertEquals(expectedValues.size, actualValues.size)
        expectedValues.forEach {
            assertContentEquals(it.second, actualValues[it.first])
        }
    }

    override fun assertStructuralEquality(expectedValue: ByteArray?, actualValue: ByteArray?) {
        assertContentEquals(expectedValue, actualValue)
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
        expectedValues: List<Pair<String, RealmDictionaryContainer>>,
        actualValues: Map<String, RealmDictionaryContainer>
    ) {
        assertEquals(expectedValues.size, actualValues.size)
        // TODO replace with the commented code below once map.entries is supported
        expectedValues.forEach {
            assertEquals(it.second.stringField, actualValues[it.first]?.stringField)
        }
//        assertContentEquals(
//            expectedValues.map { it.second.stringField },
//            actualValues.map { it.value.stringField }
//        )
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
internal class DictionaryTypeSafetyManager<T>(
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

internal val NULLABLE_DICTIONARY_OBJECT_VALUES = DICTIONARY_OBJECT_VALUES + null

// TODO add circular dependency data and tests
