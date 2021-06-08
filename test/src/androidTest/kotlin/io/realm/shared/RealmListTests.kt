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

import io.realm.MutableRealm
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.util.PlatformUtils
import io.realm.util.TypeDescriptor
import test.list.RealmListContainer
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealmListTests {

    private val descriptors = TypeDescriptor.allListFieldTypes

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration(
            path = "$tmpDir/default.realm",
            schema = setOf(RealmListContainer::class)
        )
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun add() {
        for (tester in unmanagedTesters) {
            tester.add()
        }
        for (tester in managedTesters) {
            tester.add()
        }
    }

    @Test
    fun addWithIndex() {
        for (tester in unmanagedTesters) {
            tester.addWithIndex()
        }
        for (tester in managedTesters) {
            tester.addWithIndex()
        }
    }

    @Test
    fun addAllWithIndex() {
        for (tester in unmanagedTesters) {
            tester.addAllWithIndex()
        }
        for (tester in managedTesters) {
            tester.addAllWithIndex()
        }
    }

    @Test
    fun addAll() {
        for (tester in unmanagedTesters) {
            tester.addAll()
        }
        for (tester in managedTesters) {
            tester.addAll()
        }
    }

    @Test
    fun set() {
        for (tester in unmanagedTesters) {
            tester.set()
        }
        for (tester in managedTesters) {
            tester.set()
        }
    }

    // TODO consider using TypeDescriptor as "source of truth" for classifiers
    // TODO investigate how to add properties/values directly so that it works for multiplatform
    @Suppress("UNCHECKED_CAST")
    private fun <T> getDataSetForClassifier(
        classifier: KClassifier,
        nullable: Boolean
    ): List<T> = when (classifier) {
        Byte::class -> if (nullable) NULLABLE_BYTE_VALUES else BYTE_VALUES
        Char::class -> if (nullable) NULLABLE_CHAR_VALUES else CHAR_VALUES
        Short::class -> if (nullable) NULLABLE_SHORT_VALUES else SHORT_VALUES
        Int::class -> if (nullable) NULLABLE_INT_VALUES else INT_VALUES
        Long::class -> if (nullable) NULLABLE_LONG_VALUES else LONG_VALUES
        Boolean::class -> if (nullable) NULLABLE_BOOLEAN_VALUES else BOOLEAN_VALUES
        Float::class -> if (nullable) NULLABLE_FLOAT_VALUES else FLOAT_VALUES
        Double::class -> if (nullable) NULLABLE_DOUBLE_VALUES else DOUBLE_VALUES
        String::class -> if (nullable) NULLABLE_STRING_VALUES else STRING_VALUES
        RealmObject::class -> OBJECT_VALUES
        else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
    } as List<T>

    private fun getTypeSafety(classifier: KClassifier, nullable: Boolean): TypeSafetyManager<*> {
        return if (nullable) {
            NullableList(
                classifier = classifier,
                property = RealmListContainer.nullableProperties[classifier]!!,
                dataSet = getDataSetForClassifier(classifier, true)
            )
        } else {
            NonNullableList(
                classifier = classifier,
                property = RealmListContainer.nonNullableProperties[classifier]!!,
                dataSet = getDataSetForClassifier(classifier, false)
            )
        }
    }

    private val unmanagedTesters: List<ListApiTester> by lazy {
        descriptors.map {
            UnmanagedListTester(
                typeSafetyManager = getTypeSafety(
                    it.elementType.classifier,
                    it.elementType.nullable
                )
            )
        }
    }

    private val managedTesters: List<ListApiTester> by lazy {
        descriptors.map {
            val elementType = it.elementType
            when (val classifier = elementType.classifier) {
                RealmObject::class -> ManagedRealmObjectListTester(
                    realm = realm,
                    typeSafetyManager = NonNullableList(
                        classifier = classifier,
                        property = RealmListContainer::objectListField,
                        dataSet = OBJECT_VALUES
                    )
                )
                else -> ManagedGenericListTester(
                    realm = realm,
                    typeSafetyManager = getTypeSafety(classifier, elementType.nullable)
                )
            }
        }
    }
}

// ----------------------------------------------
// API dimension
// ----------------------------------------------

internal interface ListApiTester {

    override fun toString(): String
    fun add()
    fun get()
    fun addWithIndex()
    fun addAllWithIndex()
    fun addAll()
    fun set()
    fun removeAt()

    /**
     * This method acts as an assertion error catcher in case one of the classifiers we use for
     * testing fails, ensuring the error message can easily be identified in the log.
     *
     * All tests should be wrapped around this function, e.g.:
     * ```
     * override fun specificTest() {
     *     errorCatcher {
     *         // Write your test logic here
     *     }
     * }
     * ```
     *
     * @param block lambda with the actual test logic to be run
     */
    fun errorCatcher(block: () -> Unit) {
        // TODO consider saving all exceptions and dump them at the end
        try {
            block()
        } catch (e: AssertionError) {
            throw AssertionError("'${toString()}' failed - ${e.message}")
        }
    }
}

// ----------------------------------------------
// Property nullability dimension
// ----------------------------------------------

internal interface TypeSafetyManager<T> {
    val classifier: KClassifier
    val property: KMutableProperty1<RealmListContainer, RealmList<T>>
    val dataSet: List<T>

    override fun toString(): String     // Default implementation not allowed, it comes from "Any"
    fun getList(realm: MutableRealm? = null): RealmList<T>
    fun getInitialDataSet(): List<T>
}

internal class NullableList<T>(
    override val classifier: KClassifier,
    override val property: KMutableProperty1<RealmListContainer, RealmList<T?>>,
    override val dataSet: List<T?>
) : TypeSafetyManager<T?> {

    override fun toString(): String = property.name

    override fun getList(realm: MutableRealm?): RealmList<T?> {
        val container = RealmListContainer().let {
            realm?.copyToRealm(it) ?: it
        }
        return property.get(container)
    }

    override fun getInitialDataSet(): List<T?> = dataSet
}

internal class NonNullableList<T>(
    override val classifier: KClassifier,
    override val property: KMutableProperty1<RealmListContainer, RealmList<T>>,
    override val dataSet: List<T>
) : TypeSafetyManager<T> {

    override fun toString(): String = property.name

    override fun getList(realm: MutableRealm?): RealmList<T> {
        val container = RealmListContainer().let {
            realm?.copyToRealm(it) ?: it
        }
        return property.get(container)
    }

    override fun getInitialDataSet(): List<T> = dataSet
}

// ----------------------------------------------
// Mode dimension
// ----------------------------------------------

internal interface UnmanagedList

internal interface ManagedList {
    val realm: Realm
}

// ----------------------------------------------
// RealmList - managed
// ----------------------------------------------

internal abstract class ManagedListTester<T>(
    override val realm: Realm,
    private val typeSafetyManager: TypeSafetyManager<T>
) : ManagedList, ListApiTester {

    /**
     * We have to make sure we copy RealmObjects to the Realm before asserting anything. For generic
     * types this is not needed.
     */
    abstract fun MutableRealm.copyToRealmIfNeeded(element: T): T

    abstract fun MutableRealm.copyToRealmIfNeeded(elements: Collection<T>): Collection<T>

    abstract fun assertEquality(expected: T, actual: T)

    override fun toString(): String = "Managed-$typeSafetyManager"

    override fun add() {
        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.getList(this)

                assertNotNull(list)
                assertTrue(list.isEmpty())

                typeSafetyManager.getInitialDataSet()
                    .forEachIndexed { index, e ->
                        assertEquals(index, list.size)
                        assertTrue(list.add(copyToRealmIfNeeded(e)))
                        assertEquals(index + 1, list.size)
                        assertEquality(e, list[index])
                    }
            }
        }
    }

    override fun get() {
        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.getList(this)

                assertNotNull(list)
                assertTrue(list.isEmpty())

                val dataSet = typeSafetyManager.getInitialDataSet()
                list.addAll(copyToRealmIfNeeded(dataSet))

                // Fails when using invalid indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list[-1]
                }
                assertFailsWith<IndexOutOfBoundsException> {
                    list[666]
                }

                dataSet.forEachIndexed { index, t ->
                    assertEquality(t, list[index])
                }
            }
        }
    }

    override fun addWithIndex() {
        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.getList(this)

                assertNotNull(list)
                assertTrue(list.isEmpty())

                typeSafetyManager.getInitialDataSet()
                    .forEachIndexed { index, e ->
                        assertEquals(index, list.size)
                        list.add(0, copyToRealmIfNeeded(e))
                        assertEquals(index + 1, list.size)
                        assertEquality(e, list[0])
                    }

                // Fails when using invalid indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list.add(-1, typeSafetyManager.getInitialDataSet()[0])
                }
                assertFailsWith<IndexOutOfBoundsException> {
                    list.add(666, typeSafetyManager.getInitialDataSet()[0])
                }
            }
        }
    }

    override fun addAllWithIndex() {
        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.getList(this)

                assertNotNull(list)
                assertTrue(list.isEmpty())

                // Fails when using wrong indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list.addAll(-1, listOf())
                }
                assertFailsWith<IndexOutOfBoundsException> {
                    list.addAll(666, listOf())
                }

                // Returns false when list does not change
                assertFalse(list.addAll(0, listOf()))

                val originalDataSet = typeSafetyManager.getInitialDataSet()

                // Returns true when list changes - first add produces "1, 2, 3"
                // Second add produces "1, 1, 2, 3, 2, 3"
                assertTrue(list.addAll(0, copyToRealmIfNeeded(originalDataSet)))
                assertTrue(list.addAll(1, copyToRealmIfNeeded(originalDataSet)))
                assertEquals(originalDataSet.size * 2, list.size)

                // Build a list that looks like "1, 1, 2, 3, 2, 3"
                val dataSet = originalDataSet.let {
                    it.toMutableList().apply { addAll(1, it) }
                }

                for (i in 0 until list.size) {
                    assertEquality(dataSet[i], list[i])
                }

                // Fails when using invalid indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list.addAll(-1, originalDataSet)
                }
                assertFailsWith<IndexOutOfBoundsException> {
                    list.addAll(666, originalDataSet)
                }
            }
        }
    }

    override fun addAll() {
        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.getList(this)

                assertNotNull(list)
                assertTrue(list.isEmpty())

                // Returns false when list does not change
                assertFalse(list.addAll(listOf()))

                // Returns true when list changes
                val dataSet = typeSafetyManager.getInitialDataSet()
                assertTrue(list.addAll(copyToRealmIfNeeded(dataSet)))
                assertTrue(list.addAll(copyToRealmIfNeeded(dataSet)))
                assertEquals(dataSet.size * 2, list.size)

                val newDataSet = dataSet.plus(dataSet)
                for (i in 0 until list.size) {
                    assertEquality(newDataSet[i], list[i])
                }
            }
        }
    }

    override fun set() {
        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.getList(this)

                assertNotNull(list)
                assertTrue(list.isEmpty())

                // Add something so that we can call set on an index
                val originalDataSet = typeSafetyManager.getInitialDataSet()
                list.add(copyToRealmIfNeeded(originalDataSet[0]))

                val previousElement = list.set(0, copyToRealmIfNeeded(originalDataSet[1]))
                assertEquals(1, list.size)
                assertEquality(originalDataSet[0], previousElement)

                // Fails when using invalid indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list[-1] = copyToRealmIfNeeded(originalDataSet[0])
                }
                assertFailsWith<IndexOutOfBoundsException> {
                    list[666] = copyToRealmIfNeeded(originalDataSet[0])
                }
            }
        }
    }

    override fun removeAt() {
        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.getList(this)

                assertNotNull(list)
                assertTrue(list.isEmpty())

                // Fails when using invalid indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list.removeAt(0)
                }

                val originalDataSet = typeSafetyManager.getInitialDataSet()
                list.add(copyToRealmIfNeeded(originalDataSet[0]))

                // Fails when using invalid indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list.removeAt(-1)
                }
                assertFailsWith<IndexOutOfBoundsException> {
                    list.removeAt(666)
                }

                assertEquality(originalDataSet[0], list.removeAt(0))
            }
        }
    }
}

internal class ManagedGenericListTester<T>(
    override val realm: Realm,
    typeSafetyManager: TypeSafetyManager<T>
) : ManagedListTester<T>(realm, typeSafetyManager) {

    override fun MutableRealm.copyToRealmIfNeeded(element: T): T = element
    override fun MutableRealm.copyToRealmIfNeeded(elements: Collection<T>): Collection<T> = elements
    override fun assertEquality(expected: T, actual: T) = assertEquals(expected, actual)
}

internal class ManagedRealmObjectListTester(
    override val realm: Realm,
    typeSafetyManager: TypeSafetyManager<RealmListContainer>
) : ManagedListTester<RealmListContainer>(realm, typeSafetyManager) {

    override fun MutableRealm.copyToRealmIfNeeded(element: RealmListContainer): RealmListContainer =
        copyToRealm(element)

    override fun MutableRealm.copyToRealmIfNeeded(
        elements: Collection<RealmListContainer>
    ): Collection<RealmListContainer> = elements.map { copyToRealm(it) }

    override fun assertEquality(expected: RealmListContainer, actual: RealmListContainer) =
        assertEquals(expected.stringField, actual.stringField)
}

// ----------------------------------------------
// RealmList - unmanaged
// ----------------------------------------------

internal class UnmanagedListTester<T>(
    private val typeSafetyManager: TypeSafetyManager<T>
) : UnmanagedList, ListApiTester {

    override fun toString(): String {
        return "Unmanaged-$typeSafetyManager"
    }

    override fun add() {
        // No need to assert anything, just checking the delegate works
        typeSafetyManager.getList()
            .add(typeSafetyManager.getInitialDataSet()[0])
    }

    override fun get() {
        // No need to assert anything, just checking the delegate works
        typeSafetyManager.getList()
            .also {
                it.addAll(typeSafetyManager.getInitialDataSet())
            }.also {
                it[0]
            }
    }

    override fun addWithIndex() {
        // No need to assert anything, just checking the delegate works
        typeSafetyManager.getList()
            .add(0, typeSafetyManager.getInitialDataSet()[0])
    }

    override fun addAllWithIndex() {
        // No need to assert anything, just checking the delegate works
        typeSafetyManager.getList()
            .addAll(0, typeSafetyManager.getInitialDataSet())
    }

    override fun addAll() {
        // No need to assert anything, just checking the delegate works
        typeSafetyManager.getList()
            .addAll(typeSafetyManager.getInitialDataSet())
    }

    override fun set() {
        // No need to assert anything, just checking the delegate works
        typeSafetyManager.getList()
            .also {
                // Add something so that we can call set on an index
                it.add(0, typeSafetyManager.getInitialDataSet()[0])
            }.also {
                // Actual call to set
                it[0] = typeSafetyManager.getInitialDataSet()[0]
            }
    }

    override fun removeAt() {
        // No need to assert anything, just checking the delegate works
        typeSafetyManager.getList()
            .also {
                it.add(0, typeSafetyManager.getInitialDataSet()[0])
            }.also {
                it.removeAt(0)
            }
    }
}

//-----------------------------------
// Data used to initialize structures
//-----------------------------------

internal val CHAR_VALUES = listOf('a', 'b')
internal val STRING_VALUES = listOf("ABC", "BCD")
internal val INT_VALUES = listOf(1, 2)
internal val LONG_VALUES = listOf<Long>(1, 2)
internal val SHORT_VALUES = listOf<Short>(1, 2)
internal val BYTE_VALUES = listOf<Byte>(1, 2)
internal val FLOAT_VALUES = listOf(1F, 2F)
internal val DOUBLE_VALUES = listOf(1.0, 2.0)
internal val BOOLEAN_VALUES = listOf(true, false)
internal val OBJECT_VALUES = listOf(
    RealmListContainer().apply { stringField = "A" },
    RealmListContainer().apply { stringField = "B" }
)

internal val NULLABLE_CHAR_VALUES = listOf('a', 'b', null)
internal val NULLABLE_STRING_VALUES = listOf("ABC", "BCD", null)
internal val NULLABLE_INT_VALUES = listOf(1, 2, null)
internal val NULLABLE_LONG_VALUES = listOf<Long?>(1, 2, null)
internal val NULLABLE_SHORT_VALUES = listOf<Short?>(1, 2, null)
internal val NULLABLE_BYTE_VALUES = listOf<Byte?>(1, 2, null)
internal val NULLABLE_FLOAT_VALUES = listOf(1F, 2F, null)
internal val NULLABLE_DOUBLE_VALUES = listOf(1.0, 2.0, null)
internal val NULLABLE_BOOLEAN_VALUES = listOf(true, false, null)
