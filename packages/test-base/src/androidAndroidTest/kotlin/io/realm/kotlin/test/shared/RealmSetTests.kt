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

package io.realm.kotlin.test.shared

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.entities.SampleWithPrimaryKey
import io.realm.kotlin.entities.set.RealmSetContainer
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.ext.toRealmSet
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.find
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.ErrorCatcher
import io.realm.kotlin.test.GenericTypeSafetyManager
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import org.mongodb.kbson.BsonObjectId
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class RealmSetTests {

    private val descriptors = TypeDescriptor.allSetFieldTypes

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private val managedTesters: List<SetApiTester<*, RealmSetContainer>> by lazy {
        descriptors.map {
            val elementType = it.elementType
            when (val classifier = elementType.classifier) {
                RealmObject::class -> RealmObjectSetTester(
                    realm,
                    getTypeSafety(
                        classifier,
                        false
                    ) as SetTypeSafetyManager<RealmSetContainer>,
                    classifier
                )
                ByteArray::class -> ByteArraySetTester(
                    realm,
                    getTypeSafety(
                        classifier,
                        elementType.nullable
                    ) as SetTypeSafetyManager<ByteArray>,
                )
                RealmAny::class -> RealmAnySetTester(
                    realm,
                    SetTypeSafetyManager(
                        RealmSetContainer.nullableProperties[classifier]!!,
                        getDataSetForClassifier(classifier, true)
                    ) as SetTypeSafetyManager<RealmAny?>,
                )
                else -> GenericSetTester(
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
            setOf(
                RealmSetContainer::class,
                Sample::class,
                SampleWithPrimaryKey::class,
                SetLevel1::class,
                SetLevel2::class,
                SetLevel3::class
            )
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
        val set = realmSetOf<RealmSetContainer>()
        assertTrue(set.isEmpty())
        set.add(RealmSetContainer().apply { stringField = "Dummy" })
        assertEquals(1, set.size)
    }

    @Test
    fun realmSetInitializer_realmSetOf() {
        // No need to be exhaustive here
        val realmSetFromArgsEmpty: RealmSet<String> = realmSetOf()
        assertTrue(realmSetFromArgsEmpty.isEmpty())

        val realmSetFromArgs: RealmSet<String> = realmSetOf("1", "2")
        assertContentEquals(listOf("1", "2"), realmSetFromArgs)
    }

    @Test
    fun realmSetInitializer_toRealmSet() {
        // No need to be exhaustive here
        val realmSetFromEmptyCollection = emptyList<String>().toRealmSet()
        assertTrue(realmSetFromEmptyCollection.isEmpty())

        val realmSetFromSingleElementList = listOf("1").toRealmSet()
        assertContentEquals(listOf("1"), realmSetFromSingleElementList)
        val realmSetFromSingleElementSet = setOf("1").toRealmSet()
        assertContentEquals(listOf("1"), realmSetFromSingleElementSet)

        val realmSetFromMultiElementCollection = setOf("1", "2").toRealmSet()
        assertContentEquals(listOf("1", "2"), realmSetFromMultiElementCollection)

        val realmSetFromIterator = (0..2).toRealmSet()
        assertContentEquals(listOf(0, 1, 2), realmSetFromIterator)
    }

    @Test
    @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth")
    fun nestedObjectTest() {
        realm.writeBlocking {
            val level1_1 = SetLevel1().apply { name = "l1_1" }
            val level1_2 = SetLevel1().apply { name = "l1_2" }
            val level2_1 = SetLevel2().apply { name = "l2_1" }
            val level2_2 = SetLevel2().apply { name = "l2_2" }
            val level3_1 = SetLevel3().apply { name = "l3_1" }
            val level3_2 = SetLevel3().apply { name = "l3_2" }

            level1_1.set.add(level2_1)
            level1_2.set.addAll(setOf(level2_1, level2_2))

            level2_1.set.add(level3_1)
            level2_2.set.addAll(setOf(level3_1, level3_2))

            level3_1.set.add(level1_1)
            level3_2.set.addAll(setOf(level1_1, level1_2))

            copyToRealm(level1_2) // this includes the graph of all 6 objects
        }

        val objectsL1: RealmResults<SetLevel1> = realm.query<SetLevel1>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()
        val objectsL2: RealmResults<SetLevel2> = realm.query<SetLevel2>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()
        val objectsL3: RealmResults<SetLevel3> = realm.query<SetLevel3>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()

        assertEquals(2, objectsL1.count())
        assertEquals(2, objectsL2.count())
        assertEquals(2, objectsL3.count())

        // Checking sets contain the expected object - insertion order is irrelevant here
        assertEquals("l1_1", objectsL1[0].name)
        assertEquals(1, objectsL1[0].set.size)

        assertNotNull(objectsL1[0].set.firstOrNull { it.name == "l2_1" })

        assertEquals("l1_2", objectsL1[1].name)
        assertEquals(2, objectsL1[1].set.size)
        assertNotNull(objectsL1[1].set.firstOrNull { it.name == "l2_1" })
        assertNotNull(objectsL1[1].set.firstOrNull { it.name == "l2_2" })

        assertEquals("l2_1", objectsL2[0].name)
        assertEquals(1, objectsL2[0].set.size)
        assertNotNull(objectsL2[0].set.firstOrNull { it.name == "l3_1" })

        assertEquals("l2_2", objectsL2[1].name)
        assertEquals(2, objectsL2[1].set.size)
        assertNotNull(objectsL2[1].set.firstOrNull { it.name == "l3_1" })
        assertNotNull(objectsL2[1].set.firstOrNull { it.name == "l3_2" })

        assertEquals("l3_1", objectsL3[0].name)
        assertEquals(1, objectsL3[0].set.size)
        assertNotNull(objectsL3[0].set.firstOrNull { it.name == "l1_1" })

        assertEquals("l3_2", objectsL3[1].name)
        assertEquals(2, objectsL3[1].set.size)
        assertNotNull(objectsL3[1].set.firstOrNull { it.name == "l1_1" })
        assertNotNull(objectsL3[1].set.firstOrNull { it.name == "l1_2" })

        // Following circular links
        assertEquals("l1_1", objectsL1[0].name)
        assertEquals(1, objectsL1[0].set.size)
        assertNotNull(objectsL1[0].set.firstOrNull { it.name == "l2_1" })
        assertNotNull(objectsL1[0].set.firstOrNull { it.set.size == 1 })
        assertNotNull(
            objectsL1[0].set.firstOrNull { l2: SetLevel2 ->
                l2.set.firstOrNull { l3: SetLevel3 ->
                    l3.name == "l3_1"
                } != null
            }
        )
        assertNotNull(
            objectsL1[0].set.firstOrNull { l2: SetLevel2 ->
                l2.set.firstOrNull { l3: SetLevel3 ->
                    l3.set.firstOrNull { l1: SetLevel1 ->
                        l1.name == "l1_1"
                    } != null
                } != null
            }
        )
    }

    @Test
    fun add() {
        for (tester in managedTesters) {
            tester.add()
        }
    }

    @Test
    fun clear() {
        for (tester in managedTesters) {
            tester.clear()
        }
    }

    @Test
    fun contains() {
        for (tester in managedTesters) {
            tester.contains()
        }
    }

    @Test
    fun iterator() {
        for (tester in managedTesters) {
            tester.iterator()
        }
    }

    @Test
    fun iterator_hasNext() {
        for (tester in managedTesters) {
            tester.iterator_hasNext()
        }
    }

    @Test
    fun iterator_next() {
        for (tester in managedTesters) {
            tester.iterator_next()
        }
    }

    @Test
    fun iterator_remove() {
        for (tester in managedTesters) {
            tester.iterator_remove()
        }
    }

    @Test
    fun iterator_failsIfRealmClosed() {
        // No need to be exhaustive
        managedTesters[0].iteratorFailsIfRealmClosed(getCloseableRealm())
    }

    @Test
    fun copyToRealm() {
        for (tester in managedTesters) {
            tester.copyToRealm()
        }
    }

    @Test
    fun add_detectsDuplicates() {
        val leaf = Sample().apply { intField = 1 }
        val child = Sample().apply {
            intField = 2
            nullableObject = leaf
            objectSetField = realmSetOf(leaf, leaf)
        }
        realm.writeBlocking {
            copyToRealm(Sample()).apply {
                objectSetField.add(child)
            }
        }
        val results = realm.query<Sample>().find()
        assertEquals(3, results.size)
    }

    @Test
    fun addAll_detectsDuplicates() {
        val child = RealmSetContainer()
        val parent = RealmSetContainer()
        realm.writeBlocking {
            copyToRealm(parent).apply {
                objectSetField.addAll(setOf(child, child))
            }
        }
        val results = realm.query<RealmSetContainer>().find()
        assertEquals(2, results.size)
    }

    @Test
    @Suppress("LongMethod")
    fun assign_updateExistingObjects() {
        val parent = realm.writeBlocking {
            copyToRealm(
                SampleWithPrimaryKey().apply {
                    primaryKey = 2
                    objectSetField = realmSetOf(
                        SampleWithPrimaryKey().apply {
                            primaryKey = 1
                            stringField = "INIT"
                        }
                    )
                }
            )
        }
        realm.query<SampleWithPrimaryKey>("primaryKey = 1")
            .find()
            .single()
            .run {
                assertEquals("INIT", stringField)
            }
        realm.query<SampleWithPrimaryKey>("primaryKey = 2")
            .find()
            .single()
            .run {
                assertEquals(1, objectSetField.size)
                objectSetField.iterator()
                    .next()
                    .run {
                        assertEquals("INIT", stringField)
                        assertEquals(1, primaryKey)
                    }
            }

        realm.writeBlocking {
            assertNotNull(findLatest(parent)).apply {
                objectSetField = realmSetOf(
                    SampleWithPrimaryKey().apply {
                        primaryKey = 1
                        stringField = "UPDATED"
                    }
                )
            }
        }
        realm.query<SampleWithPrimaryKey>("primaryKey = 1")
            .find()
            .single()
            .run {
                assertEquals("UPDATED", stringField)
            }
        realm.query<SampleWithPrimaryKey>("primaryKey = 2")
            .find()
            .single()
            .run {
                assertEquals(1, objectSetField.size)
                objectSetField.iterator()
                    .next()
                    .run {
                        assertEquals("UPDATED", stringField)
                        assertEquals(1, primaryKey)
                    }
            }
    }

    @Test
    fun setNotifications() = runBlocking {
        val container = realm.writeBlocking { copyToRealm(RealmSetContainer()) }
        val collect = async {
            container.objectSetField.asFlow()
                .takeWhile { it.set.size < 5 }
                .collect {
                    it.set.forEach {
                        // No-op ... just verifying that we can access each element. See https://github.com/realm/realm-kotlin/issues/827
                    }
                }
        }
        while (!collect.isCompleted) {
            realm.writeBlocking {
                assertNotNull(findLatest(container))
                    .objectSetField
                    .add(RealmSetContainer())
            }
        }
    }

    private fun getCloseableRealm(): Realm =
        RealmConfiguration.Builder(schema = setOf(RealmSetContainer::class))
            .directory(tmpDir)
            .name("closeable.realm")
            .build()
            .let {
                Realm.open(it)
            }

    private fun getTypeSafety(classifier: KClassifier, nullable: Boolean): SetTypeSafetyManager<*> =
        when {
            nullable -> SetTypeSafetyManager(
                property = RealmSetContainer.nullableProperties[classifier]!!,
                dataSetToLoad = getDataSetForClassifier(classifier, true)
            )
            else -> SetTypeSafetyManager(
                property = RealmSetContainer.nonNullableProperties[classifier]!!,
                dataSetToLoad = getDataSetForClassifier(classifier, false)
            )
        }

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
        BsonObjectId::class -> if (nullable) NULLABLE_BSON_OBJECT_ID_VALUES else BSON_OBJECT_ID_VALUES
        RealmUUID::class -> if (nullable) NULLABLE_UUID_VALUES else UUID_VALUES
        ByteArray::class -> if (nullable) NULLABLE_BINARY_VALUES else BINARY_VALUES
        RealmObject::class -> SET_OBJECT_VALUES // Don't use the one from RealmListTests!!!
        RealmAny::class -> SET_REALM_ANY_VALUES // RealmAny cannot be non-nullable
        else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
    } as List<T>
}

/**
 * Tester interface defining the operations that have to be tested exhaustively.
 */
internal interface SetApiTester<T, Container> : ErrorCatcher {

    val realm: Realm

    override fun toString(): String
    fun copyToRealm()
    fun add()
    fun clear()
    fun contains()
    fun iterator()
    fun iterator_hasNext()
    fun iterator_next()
    fun iterator_remove()
    fun iteratorFailsIfRealmClosed(realm: Realm)

    /**
     * Asserts structural equality for two given collections. This is needed to evaluate equality
     * contents of ByteArrays and RealmObjects.
     */
    fun assertStructuralEquality(expectedValues: Collection<T>, actualValues: Collection<T>)

    /**
     * Checks whether [actualElement] is contained in a [expectedCollection]. This comes in handy when checking
     * whether elements yielded by `iterator.next()` are contained in a specific `Collection` since
     * we need to do the equality assertion at a structural level.
     */
    fun structuralContains(expectedCollection: Collection<T>, actualElement: T?): Boolean

    /**
     * Assertions on the container outside the write transaction plus cleanup.
     */
    fun assertContainerAndCleanup(assertion: (Container) -> Unit)
}

/**
 * Tester for managed sets. Some operations need to be implemented further down the type hierarchy.
 */
internal abstract class ManagedSetTester<T>(
    override val realm: Realm,
    private val typeSafetyManager: SetTypeSafetyManager<T>,
    override val classifier: KClassifier
) : SetApiTester<T, RealmSetContainer> {

    override fun toString(): String = classifier.toString()

    override fun copyToRealm() {
        val dataSet = typeSafetyManager.dataSetToLoad

        val assertions = { container: RealmSetContainer ->
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

    override fun add() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)
                dataSet.forEachIndexed { index, t ->
                    assertEquals(index, set.size)
                    set.add(t)
                    assertEquals(index + 1, set.size)
                }
            }
        }

        assertContainerAndCleanup { container ->
            val set = typeSafetyManager.getCollection(container)
            assertStructuralEquality(dataSet, set)
        }
    }

    override fun clear() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)
                set.addAll(dataSet)
                assertEquals(dataSet.size, set.size)
                set.clear()
                assertTrue(set.isEmpty())
            }
        }

        assertContainerAndCleanup { container ->
            assertTrue(typeSafetyManager.getCollection(container).isEmpty())
        }
    }

    override fun contains() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)
                dataSet.forEach {
                    assertFalse(structuralContains(set, it))
                }
                set.addAll(dataSet)
                assertStructuralEquality(dataSet, set)
            }
        }

        assertContainerAndCleanup { container ->
            val set = typeSafetyManager.getCollection(container)
            assertStructuralEquality(dataSet, set)
        }
    }

    override fun iterator() {
        errorCatcher {
            realm.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)
                assertNotNull(set.iterator())
            }
        }

        assertContainerAndCleanup { container ->
            val set = typeSafetyManager.getCollection(container)
            assertNotNull(set.iterator())
        }
    }

    override fun iterator_hasNext() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)
                val iterator = set.iterator()

                assertFalse(iterator.hasNext())
                set.addAll(dataSet)
                assertTrue(iterator.hasNext())
            }
        }

        assertContainerAndCleanup { container ->
            val set = typeSafetyManager.getCollection(container)
            assertTrue(set.iterator().hasNext())
        }
    }

    override fun iterator_next() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)
                val iterator = set.iterator()

                assertFailsWith<NoSuchElementException> { (iterator.next()) }
                set.addAll(dataSet)
                while (iterator.hasNext()) {
                    val element = iterator.next()
                    assertTrue(structuralContains(dataSet, element))
                }
            }
        }

        assertContainerAndCleanup { container ->
            val set = typeSafetyManager.getCollection(container)
            val iterator = set.iterator()
            assertTrue(iterator.hasNext())
        }
    }

    override fun iterator_remove() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            realm.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)

                // Fails when calling remove before calling next
                assertFailsWith<NoSuchElementException> { set.iterator().remove() }

                set.addAll(dataSet)

                val iterator = set.iterator()

                // Still fails when calling remove before calling next
                assertFailsWith<NoSuchElementException> { iterator.remove() }
                assertTrue(iterator.hasNext())
                val next = iterator.next()

                assertTrue(structuralContains(dataSet, next))

                iterator.remove() // Calling remove should run correctly now
                assertEquals(dataSet.size - 1, set.size)
            }
        }

        assertContainerAndCleanup { container ->
            val set = typeSafetyManager.getCollection(container)

            // The set has one fewer element as we removed one in the previous assertions
            assertEquals(dataSet.size - 1, set.size)
        }
    }

    override fun iteratorFailsIfRealmClosed(realm: Realm) {
        errorCatcher {
            val dataSet = typeSafetyManager.dataSetToLoad
            realm.writeBlocking {
                typeSafetyManager.createContainerAndGetCollection(this)
                    .addAll(dataSet)
            }

            val set = realm.query<RealmSetContainer>()
                .first()
                .find { setContainer ->
                    assertNotNull(setContainer)
                    typeSafetyManager.getCollection(setContainer)
                }

            realm.close()

            assertFailsWith<IllegalStateException> {
                set.iterator()
            }
            assertFailsWith<IllegalStateException> {
                set.iterator().hasNext()
            }
            assertFailsWith<IllegalStateException> {
                set.iterator().next()
            }
            assertFailsWith<IllegalStateException> {
                set.iterator().remove()
            }
        }
    }

    override fun assertContainerAndCleanup(assertion: (RealmSetContainer) -> Unit) {
        val container = realm.query<RealmSetContainer>()
            .first()
            .find()
        assertNotNull(container)

        // Assert
        errorCatcher {
            assertion(container)
        }

        // Clean up
        realm.writeBlocking {
            delete(query<RealmSetContainer>())
        }
    }
}

/**
 * Tester for generic types.
 */
internal class GenericSetTester<T>(
    realm: Realm,
    typeSafetyManager: SetTypeSafetyManager<T>,
    classifier: KClassifier
) : ManagedSetTester<T>(realm, typeSafetyManager, classifier) {

    override fun assertStructuralEquality(
        expectedValues: Collection<T>,
        actualValues: Collection<T>
    ) {
        assertEquals(expectedValues.size, actualValues.size)
        actualValues.forEach {
            assertTrue(expectedValues.contains(it))
        }
    }

    override fun structuralContains(expectedCollection: Collection<T>, actualElement: T?): Boolean =
        expectedCollection.contains(actualElement)
}

/**
 * Tester for RealmAny.
 */
internal class RealmAnySetTester(
    realm: Realm,
    typeSafetyManager: SetTypeSafetyManager<RealmAny?>,
) : ManagedSetTester<RealmAny?>(realm, typeSafetyManager, RealmAny::class) {

    override fun assertStructuralEquality(
        expectedValues: Collection<RealmAny?>,
        actualValues: Collection<RealmAny?>
    ) {
        assertEquals(expectedValues.size, actualValues.size)
        actualValues.forEach { actual ->
            when (actual) {
                null -> assertTrue(expectedValues.contains(null))
                else -> when (actual.type) {
                    RealmAny.Type.OBJECT -> {
                        val stringsFromObjects = expectedValues.filter {
                            it != null && it.type == RealmAny.Type.OBJECT
                        }.map {
                            it?.asRealmObject<RealmSetContainer>()
                                ?.stringField
                        }
                        val stringFromRealmAny =
                            actual.asRealmObject<RealmSetContainer>().stringField
                        stringsFromObjects.contains(stringFromRealmAny)
                    }
                    RealmAny.Type.BINARY -> {
                        val binaryValues = expectedValues.filter {
                            it != null && it.type == RealmAny.Type.BINARY
                        }.map {
                            it!!.asByteArray()
                        }
                        val binaryFromRealmAny = actual.asByteArray()
                        binaryContains(binaryValues, binaryFromRealmAny)
                    }
                    else -> assertTrue(expectedValues.contains(actual))
                }
            }
        }
    }

    override fun structuralContains(
        expectedCollection: Collection<RealmAny?>,
        actualElement: RealmAny?
    ): Boolean {
        return when (actualElement) {
            null -> expectedCollection.contains(null)
            else -> when (actualElement.type) {
                RealmAny.Type.OBJECT -> {
                    val stringsFromObjects = expectedCollection.filter {
                        it != null && it.type == RealmAny.Type.OBJECT
                    }.map {
                        it?.asRealmObject<RealmSetContainer>()
                            ?.stringField
                    }
                    val stringFromRealmAny =
                        actualElement.asRealmObject<RealmSetContainer>().stringField
                    stringsFromObjects.contains(stringFromRealmAny)
                }
                RealmAny.Type.BINARY -> {
                    val binaryValues = expectedCollection.filter {
                        it != null && it.type == RealmAny.Type.BINARY
                    }.map {
                        it?.asByteArray()
                    }
                    val binaryFromRealmAny = actualElement.asByteArray()
                    binaryContains(binaryValues, binaryFromRealmAny)
                }
                else -> expectedCollection.contains(actualElement)
            }
        }
    }
}

/**
 * Tester for ByteArray.
 */
internal class ByteArraySetTester(
    realm: Realm,
    typeSafetyManager: SetTypeSafetyManager<ByteArray>,
) : ManagedSetTester<ByteArray>(realm, typeSafetyManager, ByteArray::class) {

    override fun assertStructuralEquality(
        expectedValues: Collection<ByteArray>,
        actualValues: Collection<ByteArray>
    ) {
        assertEquals(expectedValues.size, actualValues.size)

        // We can't iterate by index on the set and the positions are not guaranteed to be the same
        // as in the dataset so to compare the values are the same we need to bend over backwards...
        var successfulAssertions = 0
        actualValues.forEach { actualByteArray ->
            expectedValues.forEach { expectedByteArray ->
                try {
                    assertContentEquals(expectedByteArray, actualByteArray)
                    successfulAssertions += 1
                } catch (e: AssertionError) {
                    // Do nothing, the byte arrays might be structurally equal in the next iteration
                }
            }
        }
        if (successfulAssertions != expectedValues.size) {
            fail("Not all the elements in the ByteArray were found in the expected dataset - there were only $successfulAssertions although ${expectedValues.size} were expected.")
        }
    }

    override fun structuralContains(
        expectedCollection: Collection<ByteArray>,
        actualElement: ByteArray?
    ): Boolean = binaryContains(expectedCollection, actualElement)
}

private fun binaryContains(
    collection: Collection<ByteArray?>,
    element: ByteArray?
): Boolean {
    // We need to iterate over the collection and check IF ONE AND ONLY ONE of the byte arrays
    // contained in it matches the contents of the given 'element' byte array.
    var successfulAssertions = 0
    collection.forEach { expectedByteArray ->
        try {
            assertContentEquals(expectedByteArray, element)
            successfulAssertions += 1
        } catch (e: AssertionError) {
            // Do nothing, the byte arrays might be structurally equal in the next iteration
        }
    }
    return successfulAssertions == 1
}

/**
 * Tester for RealmObject.
 */
internal class RealmObjectSetTester(
    realm: Realm,
    typeSafetyManager: SetTypeSafetyManager<RealmSetContainer>,
    classifier: KClassifier
) : ManagedSetTester<RealmSetContainer>(realm, typeSafetyManager, classifier) {

    override fun assertStructuralEquality(
        expectedValues: Collection<RealmSetContainer>,
        actualValues: Collection<RealmSetContainer>
    ) {
        assertEquals(expectedValues.size, actualValues.size)
        assertContentEquals(
            expectedValues.map { it.stringField },
            actualValues.map { it.stringField }
        )
    }

    override fun structuralContains(
        expectedCollection: Collection<RealmSetContainer>,
        actualElement: RealmSetContainer?
    ): Boolean {
        assertNotNull(actualElement)

        // Map 'stringField' properties from the original dataset and check whether
        // 'element.stringField' is present - if so, both objects are equal
        return expectedCollection.map { it.stringField }
            .contains(actualElement.stringField)
    }
}

/**
 * Dataset container for RealmSets, can be either nullable or non-nullable.
 */
internal class SetTypeSafetyManager<T>(
    override val property: KMutableProperty1<RealmSetContainer, RealmSet<T>>,
    override val dataSetToLoad: List<T>
) : GenericTypeSafetyManager<T, RealmSetContainer, RealmSet<T>> {

    override fun toString(): String = property.name

    override fun getCollection(container: RealmSetContainer): RealmSet<T> = property.get(container)

    override fun createContainerAndGetCollection(realm: MutableRealm): RealmSet<T> {
        val container = RealmSetContainer().let {
            realm.copyToRealm(it)
        }
        return property.get(container)
            .also { set ->
                assertNotNull(set)
                assertTrue(set.isEmpty())
            }
    }

    override fun createPrePopulatedContainer(): RealmSetContainer =
        RealmSetContainer().also {
            property.get(it)
                .apply {
                    addAll(dataSetToLoad)
                }
        }
}

// Circular dependencies with sets
class SetLevel1 : RealmObject {
    var name: String = ""
    var set: RealmSet<SetLevel2> = realmSetOf()
}

class SetLevel2 : RealmObject {
    var name: String = ""
    var set: RealmSet<SetLevel3> = realmSetOf()
}

class SetLevel3 : RealmObject {
    var name: String = ""
    var set: RealmSet<SetLevel1> = realmSetOf()
}

// We can't reuse RealmListContainer until we align both test suites
internal val SET_OBJECT_VALUES = listOf(
    RealmSetContainer().apply { stringField = "A" },
    RealmSetContainer().apply { stringField = "B" }
)

internal val SET_OBJECT_VALUES2 = listOf(
    RealmSetContainer().apply { stringField = "C" },
    RealmSetContainer().apply { stringField = "D" },
    RealmSetContainer().apply { stringField = "E" },
    RealmSetContainer().apply { stringField = "F" },
)
internal val SET_OBJECT_VALUES3 = listOf(
    RealmSetContainer().apply { stringField = "G" },
    RealmSetContainer().apply { stringField = "H" }
)

// Use this for SET tests as this file does exhaustive testing on all RealmAny types
internal val SET_REALM_ANY_VALUES = REALM_ANY_PRIMITIVE_VALUES + RealmAny.create(
    RealmSetContainer().apply { stringField = "hello" },
    RealmSetContainer::class
)
