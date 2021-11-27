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

package io.realm.test.shared

import io.realm.MutableRealm
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.entities.list.Level1
import io.realm.entities.list.Level2
import io.realm.entities.list.Level3
import io.realm.entities.list.RealmListContainer
import io.realm.objects
import io.realm.realmListOf
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.TypeDescriptor
import io.realm.toRealmList
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
        val configuration = RealmConfiguration.Builder(
            schema = setOf(RealmListContainer::class, Level1::class, Level2::class, Level3::class)
        ).path("$tmpDir/default.realm").build()
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
    fun realmListInitializer_realmListOf() {
        val realmListFromArgsEmpty: RealmList<String> = realmListOf<String>()
        assertTrue(realmListFromArgsEmpty.isEmpty())

        val realmListFromArgs: RealmList<String> = realmListOf("1", "2")
        assertContentEquals(listOf("1", "2"), realmListFromArgs)
    }

    @Test
    fun realmListInitializer_toRealmList() {
        val realmListFromEmptyCollection = emptyList<String>().toRealmList()
        assertTrue(realmListFromEmptyCollection.isEmpty())

        val realmListFromSingleElementList = listOf<String>("1").toRealmList()
        assertContentEquals(listOf("1"), realmListFromSingleElementList)
        val realmListFromSingleElementSet = setOf<String>("1").toRealmList()
        assertContentEquals(listOf("1"), realmListFromSingleElementList)

        val realmListFromMultiElementCollection = setOf<String>("1", "2").toRealmList()
        assertContentEquals(listOf("1", "2"), realmListFromMultiElementCollection)

        val realmListFromIterator = IntRange(0, 2).toRealmList()
        assertContentEquals(listOf(0, 1, 2), realmListFromIterator)
    }

    @Test
    fun nestedObjectTest() {
        realm.writeBlocking {
            val level1_1 = Level1().apply { name = "l1_1" }
            val level1_2 = Level1().apply { name = "l1_2" }
            val level2_1 = Level2().apply { name = "l2_1" }
            val level2_2 = Level2().apply { name = "l2_2" }
            val level3_1 = Level3().apply { name = "l3_1" }
            val level3_2 = Level3().apply { name = "l3_2" }

            level1_1.list.add(level2_1)
            level1_2.list.addAll(listOf(level2_1, level2_2))

            level2_1.list.add(level3_1)
            level2_2.list.addAll(listOf(level3_1, level3_2))

            level3_1.list.add(level1_1)
            level3_2.list.addAll(listOf(level1_1, level1_2))

            copyToRealm(level1_2) // this includes the graph of all 6 objects
        }

        val objectsL1: RealmResults<Level1> =
            realm.objects<Level1>().query("name BEGINSWITH \"l\" SORT(name ASC)")
        val objectsL2: RealmResults<Level2> =
            realm.objects<Level2>().query("name BEGINSWITH \"l\" SORT(name ASC)")
        val objectsL3: RealmResults<Level3> =
            realm.objects<Level3>().query("name BEGINSWITH \"l\" SORT(name ASC)")

        assertEquals(2, objectsL1.count())
        assertEquals(2, objectsL2.count())
        assertEquals(2, objectsL3.count())

        // Checking insertion order is honoured
        assertEquals("l1_1", objectsL1[0].name)
        assertEquals(1, objectsL1[0].list.size)
        assertEquals("l2_1", objectsL1[0].list[0].name)

        assertEquals("l1_2", objectsL1[1].name)
        assertEquals(2, objectsL1[1].list.size)
        assertEquals("l2_1", objectsL1[1].list[0].name)
        assertEquals("l2_2", objectsL1[1].list[1].name)

        assertEquals("l2_1", objectsL2[0].name)
        assertEquals(1, objectsL2[0].list.size)
        assertEquals("l3_1", objectsL2[0].list[0].name)

        assertEquals("l2_2", objectsL2[1].name)
        assertEquals(2, objectsL2[1].list.size)
        assertEquals("l3_1", objectsL2[1].list[0].name)
        assertEquals("l3_2", objectsL2[1].list[1].name)

        assertEquals("l3_1", objectsL3[0].name)
        assertEquals(1, objectsL3[0].list.size)
        assertEquals("l1_1", objectsL3[0].list[0].name)

        assertEquals("l3_2", objectsL3[1].name)
        assertEquals(2, objectsL3[1].list.size)
        assertEquals("l1_1", objectsL3[1].list[0].name)
        assertEquals("l1_2", objectsL3[1].list[1].name)

        // Following circular links
        assertEquals("l1_1", objectsL1[0].name)
        assertEquals(1, objectsL1[0].list.size)
        assertEquals("l2_1", objectsL1[0].list[0].name)
        assertEquals(1, objectsL1[0].list[0].list.size)
        assertEquals("l3_1", objectsL1[0].list[0].list[0].name)
        assertEquals("l1_1", objectsL1[0].list[0].list[0].list[0].name)
    }

    @Test
    fun copyToRealm() {
        for (tester in managedTesters) {
            tester.copyToRealm()
        }
    }

    @Test
    fun get() {
        for (tester in managedTesters) {
            tester.get()
        }
    }

    @Test
    fun getFailsIfClosed() {
        // No need to be exhaustive
        managedTesters[0].getFailsIfClosed(getCloseableRealm())
    }

    @Test
    fun addWithIndex() {
        for (tester in managedTesters) {
            tester.addWithIndex()
        }
    }

    @Test
    @Ignore // FIXME Realm cannot be closed inside a write. Rewrite once we can pass a List out again
    fun addWithIndexFailsIfClosed() {
        // No need to be exhaustive
        managedTesters[0].addWithIndexFailsIfClosed(getCloseableRealm())
    }

    @Test
    fun addAllWithIndex() {
        for (tester in managedTesters) {
            tester.addAllWithIndex()
        }
    }

    @Test
    @Ignore // FIXME Realm cannot be closed inside a write. Rewrite once we can pass a List out again
    fun addAllWithIndexFailsIfClosed() {
        // No need to be exhaustive
        managedTesters[0].addAllWithIndexFailsIfClosed(getCloseableRealm())
    }

    @Test
    fun clear() {
        for (tester in managedTesters) {
            tester.clear()
        }
    }

    @Test
    @Ignore // FIXME Realm cannot be closed inside a write. Rewrite once we can pass a List out again
    fun clearFailsIfClosed() {
        // No need to be exhaustive
        managedTesters[0].clearFailsIfClosed(getCloseableRealm())
    }

    @Test
    fun removeAt() {
        for (tester in managedTesters) {
            tester.removeAt()
        }
    }

    @Test
    @Ignore // FIXME Realm cannot be closed inside a write. Rewrite once we can pass a List out again
    fun removeAtFailsIfClosed() {
        // No need to be exhaustive
        managedTesters[0].removeAtFailsIfClosed(getCloseableRealm())
    }

    @Test
    fun set() {
        for (tester in managedTesters) {
            tester.set()
        }
    }

    @Test
    @Ignore // FIXME Realm cannot be closed inside a write. Rewrite once we can pass a List out again
    fun setFailsIfClosed() {
        // No need to be exhaustive
        managedTesters[0].setFailsIfClosed(getCloseableRealm())
    }

    @Test
    fun unmanaged() {
        // No need to be exhaustive here, just checking delegation works
        val list = realmListOf<RealmListContainer>()
        assertTrue(list.isEmpty())
        list.add(RealmListContainer().apply { stringField = "Dummy" })
        assertEquals(1, list.size)
    }

    private fun getCloseableRealm(): Realm =
        RealmConfiguration.Builder(schema = setOf(RealmListContainer::class))
            .path("$tmpDir/closeable.realm").build().let { Realm.open(it) }

    // TODO investigate how to add properties/values directly so that it works for multiplatform
    @Suppress("UNCHECKED_CAST", "ComplexMethod")
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
    fun copyToRealm()
    fun get()
    fun getFailsIfClosed(realm: Realm)
    fun addWithIndex()
    fun addWithIndexFailsIfClosed(realm: Realm)
    fun addAllWithIndex()
    fun addAllWithIndexFailsIfClosed(realm: Realm)
    fun clear()
    fun clearFailsIfClosed(realm: Realm)
    fun removeAt()
    fun removeAtFailsIfClosed(realm: Realm)
    fun set()
    fun setFailsIfClosed(realm: Realm)

    // All the other functions are not tested since we rely on implementations from parent classes.

    /**
     * This method acts as an assertion error catcher in case one of the classifiers we use for
     * testing fails, ensuring the error message can easily be identified in the log.
     *
     * Assertions should be wrapped around this function, e.g.:
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
        try {
            block()
        } catch (e: AssertionError) {
            throw AssertionError("'${toString()}' failed - ${e.message}")
        }
    }
}

// ----------------------------------------------------------
// Type safety (nullability and dataset matching) dimension
// ----------------------------------------------------------

internal interface TypeSafetyManager<T> {
    val classifier: KClassifier
    val property: KMutableProperty1<RealmListContainer, RealmList<T>>
    val dataSet: List<T>

    override fun toString(): String // Default implementation not allowed as it comes from "Any"
    fun createContainerAndGetList(realm: MutableRealm? = null): RealmList<T>
    fun getInitialDataSet(): List<T>

    fun createPrePopulatedContainer(dataSet: List<T>): RealmListContainer =
        RealmListContainer().also {
            property.get(it).apply { addAll(dataSet) }
        }

    fun getList(container: RealmListContainer): RealmList<T> = property.get(container)
}

internal class NullableList<T>(
    override val classifier: KClassifier,
    override val property: KMutableProperty1<RealmListContainer, RealmList<T?>>,
    override val dataSet: List<T?>
) : TypeSafetyManager<T?> {

    override fun toString(): String = property.name

    override fun createContainerAndGetList(realm: MutableRealm?): RealmList<T?> {
        val container = RealmListContainer().let {
            realm?.copyToRealm(it) ?: it
        }
        return property.get(container).also { list ->
            assertNotNull(list)
            assertTrue(list.isEmpty())
        }
    }

    override fun getInitialDataSet(): List<T?> = dataSet
}

internal class NonNullableList<T>(
    override val classifier: KClassifier,
    override val property: KMutableProperty1<RealmListContainer, RealmList<T>>,
    override val dataSet: List<T>
) : TypeSafetyManager<T> {

    override fun toString(): String = property.name

    override fun createContainerAndGetList(realm: MutableRealm?): RealmList<T> {
        val container = RealmListContainer().let {
            realm?.copyToRealm(it) ?: it
        }
        return property.get(container).also { list ->
            assertNotNull(list)
            assertTrue(list.isEmpty())
        }
    }

    override fun getInitialDataSet(): List<T> = dataSet
}

// ----------------------------------------------
// Mode dimension
// ----------------------------------------------

internal interface ManagedList {
    val realm: Realm
}

// ----------------------------------------------
// RealmList - managed
// ----------------------------------------------

/**
 * An API test's flow is as follows:
 * 1 - Create a managed RealmListContainer.
 * 2 - Add data to the container's specific RealmList<T> that is being processed.
 * 3 - Assert stuff inside the write transaction during the population process.
 * 4 - Assert stuff outside the write transaction, launch a query and check all is good.
 * 5 - Cleanup.
 *
 * A typical implementation would look like:
 *
 *  override fun yourApiMethod() {
 *      val dataSet = typeSafetyManager.getInitialDataSet()
 *
 *      // Abstract assertions that can be repeated inside and outside the transaction
 *      val assertions = { list: RealmList<T> ->
 *          // ...
 *      }
 *
 *      // Create container and populate list
 *      errorCatcher {
 *          realm.writeBlocking {
 *              val list = ...
 *
 *              // Assertions after population
 *              assertions(list)
 *          }
 *      }
 *
 *      // Assert again outside the transaction and cleanup
 *      assertListAndCleanup { list -> assertions(list) }
 *  }
 */
internal abstract class ManagedListTester<T>(
    override val realm: Realm,
    private val typeSafetyManager: TypeSafetyManager<T>
) : ManagedList, ListApiTester {

    /**
     * Asserts content equality for two given objects. This is needed to evaluate the contents of
     * two RealmObjects.
     */
    abstract fun assertElementsAreEqual(expected: T, actual: T)

    override fun toString(): String = "Managed-$typeSafetyManager"

    override fun copyToRealm() {
        val dataSet = typeSafetyManager.getInitialDataSet()

        val assertions = { container: RealmListContainer ->
            dataSet.forEachIndexed { index, t ->
                assertElementsAreEqual(t, typeSafetyManager.getList(container)[index])
            }
        }

        errorCatcher {
            val container = typeSafetyManager.createPrePopulatedContainer(dataSet)

            realm.writeBlocking {
                val managedContainer = copyToRealm(container)
                assertions(managedContainer)
            }
        }

        assertContainerAndCleanup { container -> assertions(container) }
    }

    override fun get() {
        val dataSet = typeSafetyManager.getInitialDataSet()
        val assertions = { list: RealmList<T> ->
            // Fails when using invalid indices
            assertFailsWith<IndexOutOfBoundsException> {
                list[-1]
            }
            assertFailsWith<IndexOutOfBoundsException> {
                list[123]
            }

            dataSet.forEachIndexed { index, t ->
                assertElementsAreEqual(t, list[index])
            }
        }

        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetList(this)
                list.addAll(dataSet)
                assertions(list)
            }
        }

        assertListAndCleanup { list -> assertions(list) }
    }

    override fun getFailsIfClosed(realm: Realm) {
        errorCatcher {
            val dataSet = typeSafetyManager.getInitialDataSet()
            realm.writeBlocking {
                typeSafetyManager.createContainerAndGetList(this)
                    .addAll(dataSet)
            }

            val list = realm.objects<RealmListContainer>()
                .first()
                .let { typeSafetyManager.getList(it) }

            realm.close()

            assertFailsWith<IllegalStateException> {
                list[0]
            }
        }
    }

    override fun addWithIndex() {
        val dataSet: List<T> = typeSafetyManager.getInitialDataSet()
        val assertions = { list: RealmList<T> ->
            // Iterate the reversed dataset since we added each element at the beginning
            dataSet.reversed().forEachIndexed { index, e ->
                assertElementsAreEqual(e, list[index])
            }
            // Fails when using invalid indices
            assertFailsWith<IndexOutOfBoundsException> {
                list.add(-1, typeSafetyManager.getInitialDataSet()[0])
            }
            assertFailsWith<IndexOutOfBoundsException> {
                list.add(123, typeSafetyManager.getInitialDataSet()[0])
            }
        }

        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetList(this)
                dataSet.forEachIndexed { index, e ->
                    assertEquals(index, list.size)
                    list.add(0, e)
                    assertEquals(index + 1, list.size)
                    assertElementsAreEqual(e, list[0])
                }

                assertions(list)
            }
        }

        assertListAndCleanup { list -> assertions(list) }
    }

    override fun addWithIndexFailsIfClosed(realm: Realm) {
        errorCatcher {
            val dataSet = typeSafetyManager.getInitialDataSet()
            realm.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetList(this)

                realm.close()

                assertFailsWith<IllegalStateException> {
                    list.add(0, dataSet[0])
                }
            }
        }
    }

    override fun addAllWithIndex() {
        val dataSet = typeSafetyManager.getInitialDataSet()
        val assertions = { list: RealmList<T> ->
            assertEquals(dataSet.size * 2, list.size)

            // Build a list that looks like "1, 1, 2, 3, 2, 3"
            val newDataSet = dataSet.let {
                it.toMutableList().apply { addAll(1, it) }
            }

            for (i in 0 until list.size) {
                assertElementsAreEqual(newDataSet[i], list[i])
            }

            // Fails when using invalid indices
            assertFailsWith<IndexOutOfBoundsException> {
                list.addAll(-1, dataSet)
            }
            assertFailsWith<IndexOutOfBoundsException> {
                list.addAll(123, dataSet)
            }
        }

        errorCatcher {
            realm.writeBlocking {
                mutableListOf<String>()
                val list = typeSafetyManager.createContainerAndGetList(this)

                // Fails when using wrong indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list.addAll(-1, listOf())
                }
                assertFailsWith<IndexOutOfBoundsException> {
                    list.addAll(123, listOf())
                }

                // Returns false when list does not change
                assertFalse(list.addAll(0, listOf()))

                // Returns true when list changes - first add produces "1, 2, 3"
                // Second add produces "1, 1, 2, 3, 2, 3"
                assertTrue(list.addAll(0, dataSet))
                assertTrue(list.addAll(1, dataSet))

                assertions(list)
            }
        }

        assertListAndCleanup { list -> assertions(list) }
    }

    override fun addAllWithIndexFailsIfClosed(realm: Realm) {
        errorCatcher {
            val dataSet = typeSafetyManager.getInitialDataSet()
            realm.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetList(this)

                realm.close()

                assertFailsWith<IllegalStateException> {
                    list.addAll(0, dataSet)
                }
            }
        }
    }

    override fun clear() {
        val dataSet = typeSafetyManager.getInitialDataSet()
        val assertions = { list: RealmList<T> ->
            assertTrue(list.isEmpty())
        }

        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetList(this)
                assertTrue(list.addAll(dataSet))

                assertEquals(dataSet.size, list.size)
                list.clear()

                assertions(list)
            }
        }

        assertListAndCleanup { list -> assertions(list) }
    }

    override fun clearFailsIfClosed(realm: Realm) {
        errorCatcher {
            val dataSet = typeSafetyManager.getInitialDataSet()
            realm.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetList(this)
                list.addAll(dataSet)

                realm.close()

                assertFailsWith<IllegalStateException> {
                    list.clear()
                }
            }
        }
    }

    override fun removeAt() {
        val dataSet = typeSafetyManager.getInitialDataSet()
        val assertions = { list: RealmList<T> ->
            assertTrue(list.isEmpty())
        }

        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetList(this)

                // Fails when using invalid indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list.removeAt(0)
                }

                list.add(dataSet[0])

                // Fails when using invalid indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list.removeAt(-1)
                }
                assertFailsWith<IndexOutOfBoundsException> {
                    list.removeAt(123)
                }

                assertElementsAreEqual(dataSet[0], list.removeAt(0))
                assertions(list)
            }
        }

        assertListAndCleanup { list -> assertions(list) }
    }

    override fun removeAtFailsIfClosed(realm: Realm) {
        errorCatcher {
            val dataSet = typeSafetyManager.getInitialDataSet()
            realm.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetList(this)
                list.addAll(dataSet)

                realm.close()

                assertFailsWith<IllegalStateException> {
                    list.removeAt(0)
                }
            }
        }
    }

    override fun set() {
        val dataSet = typeSafetyManager.getInitialDataSet()
        val assertions = { list: RealmList<T> ->
            assertEquals(1, list.size)
        }

        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetList(this)

                // Add something so that we can call set on an index
                list.add(dataSet[0])

                val previousElement = list.set(0, dataSet[1])
                assertEquals(1, list.size)
                assertElementsAreEqual(dataSet[0], previousElement)

                // Fails when using invalid indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list[-1] = dataSet[0]
                }
                assertFailsWith<IndexOutOfBoundsException> {
                    list[123] = dataSet[0]
                }

                assertions(list)
            }
        }

        assertListAndCleanup { list -> assertions(list) }
    }

    override fun setFailsIfClosed(realm: Realm) {
        errorCatcher {
            val dataSet = typeSafetyManager.getInitialDataSet()
            realm.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetList(this)
                list.addAll(dataSet)

                realm.close()

                assertFailsWith<IllegalStateException> {
                    list[0] = dataSet[0]
                }
            }
        }
    }

    // Retrieves the list again but this time from Realm to check the getter is called correctly
    private fun assertListAndCleanup(assertion: (RealmList<T>) -> Unit) {
        realm.writeBlocking {
            val container = this.objects<RealmListContainer>().first()
            val list = typeSafetyManager.getList(container)

            // Assert
            errorCatcher {
                assertion(list)
            }

            // Clean up
            delete(findLatest(container)!!)
        }
    }

    private fun assertContainerAndCleanup(assertion: (RealmListContainer) -> Unit) {
        val container = realm.objects<RealmListContainer>().first()

        // Assert
        errorCatcher {
            assertion(container)
        }

        // Clean up
        realm.writeBlocking {
            delete(findLatest(container)!!)
        }
    }
}

/**
 * No special needs for managed, generic testers. Elements can be compared painlessly and need not
 * be copied to Realm when calling RealmList API methods.
 */
internal class ManagedGenericListTester<T>(
    realm: Realm,
    typeSafetyManager: TypeSafetyManager<T>
) : ManagedListTester<T>(realm, typeSafetyManager) {
    override fun assertElementsAreEqual(expected: T, actual: T) = assertEquals(expected, actual)
}

/**
 * Managed and unmanaged RealmObjects cannot be compared directly. They also need to become managed
 * before we use them as input for RealmList API methods.
 */
internal class ManagedRealmObjectListTester(
    realm: Realm,
    typeSafetyManager: TypeSafetyManager<RealmListContainer>
) : ManagedListTester<RealmListContainer>(realm, typeSafetyManager) {
    override fun assertElementsAreEqual(expected: RealmListContainer, actual: RealmListContainer) =
        assertEquals(expected.stringField, actual.stringField)
}

// -----------------------------------
// Data used to initialize structures
// -----------------------------------

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
