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
            tester.test()
        }
        for (tester in managedTesters) {
            tester.test()
        }
    }

    // TODO consider using TypeDescriptor as "source of truth" for classifiers
    // TODO investigate how to add properties/values directly so that it works for multiplatform
    @Suppress("UNCHECKED_CAST")
    private fun <T> getDataSetForClassifier(
        classifier: KClassifier,
        nullable: Boolean = false
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

    @Suppress("UNCHECKED_CAST")
    private fun <T> getPropertyForClassifier(
        classifier: KClassifier,
        nullable: Boolean = false
    ): KMutableProperty1<RealmListContainer, RealmList<T>> = when (classifier) {
        Byte::class -> if (nullable) RealmListContainer::nullableByteListField else RealmListContainer::byteListField
        Char::class -> if (nullable) RealmListContainer::nullableCharListField else RealmListContainer::charListField
        Short::class -> if (nullable) RealmListContainer::nullableShortListField else RealmListContainer::shortListField
        Int::class -> if (nullable) RealmListContainer::nullableIntListField else RealmListContainer::intListField
        Long::class -> if (nullable) RealmListContainer::nullableLongListField else RealmListContainer::longListField
        Boolean::class -> if (nullable) RealmListContainer::nullableBooleanListField else RealmListContainer::booleanListField
        Float::class -> if (nullable) RealmListContainer::nullableFloatListField else RealmListContainer::floatListField
        Double::class -> if (nullable) RealmListContainer::nullableDoubleListField else RealmListContainer::doubleListField
        String::class -> if (nullable) RealmListContainer::nullableStringListField else RealmListContainer::stringListField
        RealmObject::class -> RealmListContainer::objectListField
        else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
    } as KMutableProperty1<RealmListContainer, RealmList<T>>

    private fun getTypeSafety(classifier: KClassifier, nullable: Boolean): TypeSafetyManager<*> {
        return if (nullable) {
            NullableList(
                property = getPropertyForClassifier<Any?>(classifier),
                dataSet = getDataSetForClassifier(classifier)
            )
        } else {
            NonNullableList(
                property = getPropertyForClassifier<Any>(classifier),
                dataSet = getDataSetForClassifier(classifier)
            )
        }
    }

    private val unmanagedTesters: List<ListTester> by lazy {
        descriptors.map {
            UnmanagedListApi(
                typeSafetyManager = getTypeSafety(
                    it.elementType.classifier,
                    it.elementType.nullable
                )
            )
        }
    }

    private val managedTesters: List<ListTester> by lazy {
        descriptors.map {
            val elementType = it.elementType
            when (val classifier = elementType.classifier) {
                RealmObject::class -> ManagedRealmObjectListApi(
                    realm = realm,
                    typeSafetyManager = NonNullableList(
                        property = RealmListContainer::objectListField,
                        dataSet = OBJECT_VALUES
                    )
                )
                else -> ManagedListApi(
                    realm = realm,
                    typeSafetyManager = getTypeSafety(classifier, elementType.nullable)
                )
            }
        }
    }
}

// ----------------------------------------------
// Entry point
// ----------------------------------------------

internal interface ListTester {

    fun test()

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
    val property: KMutableProperty1<RealmListContainer, RealmList<T>>
    val dataSet: List<T>

    fun getList(realm: MutableRealm? = null): RealmList<T>
    fun getInitialDataSet(): List<T>
}

internal class NullableList<T>(
    override val property: KMutableProperty1<RealmListContainer, RealmList<T?>>,
    override val dataSet: List<T?>
) : TypeSafetyManager<T?> {

    override fun getList(realm: MutableRealm?): RealmList<T?> {
        val container = RealmListContainer().let {
            realm?.copyToRealm(it) ?: it
        }
        return property.get(container)
    }

    override fun getInitialDataSet(): List<T?> = dataSet
}

internal class NonNullableList<T>(
    override val property: KMutableProperty1<RealmListContainer, RealmList<T>>,
    override val dataSet: List<T>
) : TypeSafetyManager<T> {

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
// API dimension
// ----------------------------------------------

internal interface ListApi<T> : ListTester {
    fun add()
}

// ----------------------------------------------
// RealmList - managed
// ----------------------------------------------

internal class ManagedListApi<T>(
    override val realm: Realm,
    private val typeSafetyManager: TypeSafetyManager<T>
) : ManagedList, ListApi<T> {

    override fun test() {
        add()
    }

    override fun add() {
        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.getList(this)

                assertNotNull(list)
                assertTrue(list.isEmpty())

                typeSafetyManager.getInitialDataSet()
                    .forEachIndexed { index, e ->
                        assertEquals(index, list.size)
                        assertTrue(list.add(e))
                        assertEquals(index + 1, list.size)
                    }
            }
        }
    }
}

internal class ManagedRealmObjectListApi(
    override val realm: Realm,
    private val typeSafetyManager: TypeSafetyManager<RealmListContainer>
) : ManagedList, ListApi<RealmListContainer> {

    override fun test() {
        add()
    }

    override fun add() {
        errorCatcher {
            realm.writeBlocking {
                val list = typeSafetyManager.getList(this)

                assertNotNull(list)
                assertTrue(list.isEmpty())

                typeSafetyManager.getInitialDataSet()
                    .forEachIndexed { index, e ->
                        assertEquals(index, list.size)
                        assertTrue(list.add(copyToRealm(e)))
                        assertEquals(index + 1, list.size)
                    }
            }
        }
    }
}

// ----------------------------------------------
// RealmList - unmanaged
// ----------------------------------------------

internal class UnmanagedListApi<T>(
    private val typeSafetyManager: TypeSafetyManager<T>
) : UnmanagedList, ListApi<T> {

    override fun test() {
        add()
    }

    override fun add() {
        errorCatcher {
            val list = typeSafetyManager.getList()

            assertNotNull(list)
            assertTrue(list.isEmpty())

            typeSafetyManager.getInitialDataSet()
                .forEachIndexed { index, e ->
                    assertEquals(index, list.size)
                    assertTrue(list.add(e))
                    assertEquals(index + 1, list.size)
                }
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
