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

package io.realm.kotlin.test.shared

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.SampleWithPrimaryKey
import io.realm.kotlin.entities.list.Level1
import io.realm.kotlin.entities.list.Level2
import io.realm.kotlin.entities.list.Level3
import io.realm.kotlin.entities.list.RealmListContainer
import io.realm.kotlin.entities.list.listTestSchema
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.find
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
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
            schema = listTestSchema + setOf(
                Level1::class,
                Level2::class,
                Level3::class,
                Sample::class,
                SampleWithPrimaryKey::class
            )
        ).directory(tmpDir).build()
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
        val realmListFromArgsEmpty: RealmList<String> = realmListOf()
        assertTrue(realmListFromArgsEmpty.isEmpty())

        val realmListFromArgs: RealmList<String> = realmListOf("1", "2")
        assertContentEquals(listOf("1", "2"), realmListFromArgs)
    }

    @Test
    fun realmListInitializer_toRealmList() {
        val realmListFromEmptyCollection = emptyList<String>().toRealmList()
        assertTrue(realmListFromEmptyCollection.isEmpty())

        val realmListFromSingleElementList = listOf("1").toRealmList()
        assertContentEquals(listOf("1"), realmListFromSingleElementList)
        val realmListFromSingleElementSet = setOf("1").toRealmList()
        assertContentEquals(listOf("1"), realmListFromSingleElementSet)

        val realmListFromMultiElementCollection = setOf("1", "2").toRealmList()
        assertContentEquals(listOf("1", "2"), realmListFromMultiElementCollection)

        val realmListFromIterator = (0..2).toRealmList()
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

        val objectsL1: RealmResults<Level1> = realm.query<Level1>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()
        val objectsL2: RealmResults<Level2> = realm.query<Level2>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()
        val objectsL3: RealmResults<Level3> = realm.query<Level3>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()

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
    fun add() {
        for (tester in managedTesters) {
            tester.add()
        }
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
    fun assignField() {
        for (tester in managedTesters) {
            tester.assignField()
        }
    }

    @Test
    fun unmanaged() {
        // No need to be exhaustive here, just checking delegation works
        val list = realmListOf<RealmListContainer>()
        assertTrue(list.isEmpty())
        list.add(RealmListContainer().apply { stringField = "Dummy" })
        assertEquals(1, list.size)
    }

    @Test
    fun add_detectsDuplicates() {
        val leaf = Sample().apply { intField = 1 }
        val child = Sample().apply {
            intField = 2
            nullableObject = leaf
            objectListField = realmListOf(leaf, leaf)
        }
        realm.writeBlocking {
            copyToRealm(Sample()).apply {
                objectListField.add(child)
            }
        }
        assertEquals(3, realm.query<Sample>().find().size)
    }

    @Test
    fun addWithIndex_detectsDuplicates() {
        val leaf = Sample().apply { intField = 1 }
        val child = Sample().apply {
            intField = 2
            nullableObject = leaf
            objectListField = realmListOf(leaf, leaf)
        }
        realm.writeBlocking {
            copyToRealm(Sample()).apply {
                objectListField.add(0, child)
            }
        }
        assertEquals(3, realm.query<Sample>().find().size)
    }

    @Test
    fun addAll_detectsDuplicates() {
        val child = RealmListContainer()
        val parent = RealmListContainer()
        realm.writeBlocking {
            copyToRealm(parent).apply { objectListField.addAll(listOf(child, child)) }
        }
        assertEquals(2, realm.query<RealmListContainer>().find().size)
    }

    @Test
    fun assign_updateExistingObjects() {
        val parent = realm.writeBlocking {
            copyToRealm(
                SampleWithPrimaryKey().apply {
                    primaryKey = 2
                    objectListField = realmListOf(
                        SampleWithPrimaryKey().apply {
                            primaryKey = 1
                            stringField = "INIT"
                        }
                    )
                }
            )
        }
        realm.query<SampleWithPrimaryKey>("primaryKey = 1").find().single().run {
            assertEquals("INIT", stringField)
        }

        realm.writeBlocking {
            findLatest(parent)!!.apply {
                objectListField = realmListOf(
                    SampleWithPrimaryKey().apply {
                        primaryKey = 1
                        stringField = "UPDATED"
                    }
                )
            }
        }
        realm.query<SampleWithPrimaryKey>("primaryKey = 1").find().single().run {
            assertEquals("UPDATED", stringField)
        }
    }

    @Test
    fun set_detectsDuplicates() {
        val leaf = Sample().apply { intField = 1 }
        val child = Sample().apply {
            intField = 2
            nullableObject = leaf
            objectListField = realmListOf(leaf, leaf)
        }
        realm.writeBlocking {
            copyToRealm(Sample()).apply {
                // Need to insert an object to be able to update it with set
                objectListField.add(Sample())
                objectListField.set(0, child)
            }
        }
        assertEquals(4, realm.query<Sample>().find().size)
    }

    @Test
    fun listNotifications() = runBlocking {
        val container = realm.writeBlocking { copyToRealm(RealmListContainer()) }
        val collect = async {
            container.objectListField.asFlow()
                .takeWhile { it.list.size < 5 }
                .collect {
                    it.list.forEach {
                        // No-op ... just verifying that we can access each element. See https://github.com/realm/realm-kotlin/issues/827
                    }
                }
        }
        while (!collect.isCompleted) {
            realm.writeBlocking {
                findLatest(container)!!.objectListField.add(RealmListContainer())
            }
        }
    }

    private fun getCloseableRealm(): Realm =
        RealmConfiguration.Builder(schema = listTestSchema)
            .directory(tmpDir)
            .name("closeable.realm")
            .build().let {
                Realm.open(it)
            }

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
        RealmInstant::class -> if (nullable) NULLABLE_TIMESTAMP_VALUES else TIMESTAMP_VALUES
        ObjectId::class -> if (nullable) NULLABLE_OBJECT_ID_VALUES else OBJECT_ID_VALUES
        RealmUUID::class -> if (nullable) NULLABLE_UUID_VALUES else UUID_VALUES
        ByteArray::class -> if (nullable) NULLABLE_BINARY_VALUES else BINARY_VALUES
        RealmObject::class -> OBJECT_VALUES
        else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
    } as List<T>

    private fun getTypeSafety(classifier: KClassifier, nullable: Boolean): TypeSafetyManager<*> =
        when {
            nullable -> NullableList(
                classifier = classifier,
                property = RealmListContainer.nullableProperties[classifier]!!,
                dataSet = getDataSetForClassifier(classifier, true)
            )
            else -> NonNullableList(
                classifier = classifier,
                property = RealmListContainer.nonNullableProperties[classifier]!!,
                dataSet = getDataSetForClassifier(classifier, false)
            )
        }

    private val managedTesters: List<ListApiTester> by lazy {
        descriptors.filter {
            // Filter out MutableRealmInts
            it.elementType.classifier != MutableRealmInt::class
        }.map {
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
                ByteArray::class -> ManagedByteArrayListTester(
                    realm = realm,
                    typeSafetyManager = getTypeSafety(
                        classifier,
                        elementType.nullable
                    ) as TypeSafetyManager<ByteArray?>
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
    fun add()
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
    fun assignField()

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
            throw AssertionError("'${toString()}' failed - ${e.message}", e)
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
            dataSet.forEachIndexed { index, expected ->
                val list = typeSafetyManager.getList(container)
                val actual = list[index]
                assertElementsAreEqual(expected, actual)
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

    override fun add() {
        val dataSet: List<T> = typeSafetyManager.getInitialDataSet()

        val assertions = { container: RealmListContainer ->
            val list = typeSafetyManager.getList(container)
            dataSet.forEachIndexed { index, t ->
                assertElementsAreEqual(t, list[index])
            }
        }

        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetList(this)
                dataSet.forEachIndexed { index, e ->
                    assertEquals(index, list.size)
                    list.add(e)
                    assertEquals(index + 1, list.size)
                }
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

            val list = realm.query<RealmListContainer>()
                .first()
                .find { listContainer ->
                    assertNotNull(listContainer)
                    typeSafetyManager.getList(listContainer)
                }

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

    override fun assignField() {
        val dataSet = typeSafetyManager.getInitialDataSet()
        val reassignedDataSet = listOf(dataSet[1])

        val assertions = { list: RealmList<T> ->
            assertEquals(1, list.size)
            // We cannot assert equality on RealmObject lists as the object isn't equals to the
            // unmanaged object from before the assignment
            if (list[0] is ByteArray) {
                reassignedDataSet.zip(list)
                    .forEach { (expected, actual) ->
                        assertElementsAreEqual(expected, actual)
                    }
            } else if (list[0] is RealmObject) {
                reassignedDataSet.zip(list)
                    .forEach { (expected, actual) ->
                        assertEquals(
                            (expected as RealmListContainer).stringField,
                            (actual as RealmListContainer).stringField
                        )
                    }
            } else {
                assertContentEquals(reassignedDataSet, list)
            }
        }
        errorCatcher {
            realm.writeBlocking {
                val container = copyToRealm(RealmListContainer())
                val list = typeSafetyManager.property.get(container)
                list.addAll(dataSet)

                val value = reassignedDataSet.toRealmList()
                typeSafetyManager.property.set(container, value)
            }
        }
        assertListAndCleanup { list -> assertions(list) }
    }

    // Retrieves the list again but this time from Realm to check the getter is called correctly
    private fun assertListAndCleanup(assertion: (RealmList<T>) -> Unit) {
        realm.writeBlocking {
            val container = this.query<RealmListContainer>()
                .first()
                .find()
            assertNotNull(container)
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
        val container = realm.query<RealmListContainer>()
            .first()
            .find()
        assertNotNull(container)

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
    override fun assertElementsAreEqual(expected: T, actual: T) {
        if (expected is ByteArray) {
            assertContentEquals(expected, actual as ByteArray)
        } else {
            assertEquals(expected, actual)
        }
    }
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

/**
 * Check equality for ByteArrays at a structural level with `assertContentEquals`.
 */
internal class ManagedByteArrayListTester(
    realm: Realm,
    typeSafetyManager: TypeSafetyManager<ByteArray?>
) : ManagedListTester<ByteArray?>(realm, typeSafetyManager) {
    override fun assertElementsAreEqual(expected: ByteArray?, actual: ByteArray?) =
        assertContentEquals(expected, actual)
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
internal val TIMESTAMP_VALUES =
    listOf(RealmInstant.from(0, 0), RealmInstant.from(42, 420))
internal val OBJECT_ID_VALUES =
    listOf(ObjectId.create(), ObjectId.from("507f191e810c19729de860ea"))
internal val UUID_VALUES =
    listOf(RealmUUID.random(), RealmUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d76"))

internal val OBJECT_VALUES = listOf(
    RealmListContainer().apply { stringField = "A" },
    RealmListContainer().apply { stringField = "B" }
)
internal val OBJECT_VALUES2 = listOf(
    RealmListContainer().apply { stringField = "C" },
    RealmListContainer().apply { stringField = "D" },
    RealmListContainer().apply { stringField = "E" },
    RealmListContainer().apply { stringField = "F" },
)
internal val OBJECT_VALUES3 = listOf(
    RealmListContainer().apply { stringField = "G" },
    RealmListContainer().apply { stringField = "H" }
)
internal val BINARY_VALUES = listOf(Random.Default.nextBytes(2), Random.Default.nextBytes(2))

internal val NULLABLE_CHAR_VALUES = CHAR_VALUES + null
internal val NULLABLE_STRING_VALUES = STRING_VALUES + null
internal val NULLABLE_INT_VALUES = INT_VALUES + null
internal val NULLABLE_LONG_VALUES = LONG_VALUES + null
internal val NULLABLE_SHORT_VALUES = SHORT_VALUES + null
internal val NULLABLE_BYTE_VALUES = BYTE_VALUES + null
internal val NULLABLE_FLOAT_VALUES = FLOAT_VALUES + null
internal val NULLABLE_DOUBLE_VALUES = DOUBLE_VALUES + null
internal val NULLABLE_BOOLEAN_VALUES = BOOLEAN_VALUES + null
internal val NULLABLE_TIMESTAMP_VALUES = TIMESTAMP_VALUES + null
internal val NULLABLE_OBJECT_ID_VALUES = OBJECT_ID_VALUES + null
internal val NULLABLE_UUID_VALUES = UUID_VALUES + null
internal val NULLABLE_BINARY_VALUES = BINARY_VALUES + null
