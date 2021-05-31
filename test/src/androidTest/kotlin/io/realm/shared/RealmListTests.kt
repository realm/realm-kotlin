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

import io.realm.*
import io.realm.util.PlatformUtils
import io.realm.util.TypeDescriptor
import test.Sample
import test.link.Child
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.test.*

class RealmListTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration(
            path = "$tmpDir/default.realm",
            schema = setOf(Sample::class, Child::class)
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
    fun getter() {
        for (tester in unmanagedTesters) {
            tester.getter()
        }
        for (tester in managedTesters) {
            tester.getter()
        }
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

    // TODO consider using TypeDescriptor as "source of truth" for classifiers
    @Suppress("UNCHECKED_CAST")
    private fun <T> getDataSetForClassifier(classifier: KClassifier): List<T> = when (classifier) {
        Byte::class -> BYTE_VALUES
        Char::class -> CHAR_VALUES
        Short::class -> SHORT_VALUES
        Int::class -> INT_VALUES
        Long::class -> LONG_VALUES
        Boolean::class -> BOOLEAN_VALUES
        Float::class -> FLOAT_VALUES
        Double::class -> DOUBLE_VALUES
        String::class -> STRING_VALUES
        RealmObject::class -> OBJECT_VALUES
        else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
    } as List<T>

    @Suppress("UNCHECKED_CAST")
    private fun <T> getPropertyForClassifier(
        classifier: KClassifier
    ): KMutableProperty1<Sample, RealmList<T>?> = when (classifier) {
        Byte::class -> Sample::byteListField
        Char::class -> Sample::charListField
        Short::class -> Sample::shortListField
        Int::class -> Sample::intListField
        Long::class -> Sample::longListField
        Boolean::class -> Sample::booleanListField
        Float::class -> Sample::floatListField
        Double::class -> Sample::doubleListField
        String::class -> Sample::stringListField
        RealmObject::class -> Sample::objectListField
        else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
    } as KMutableProperty1<Sample, RealmList<T>?>

    private val unmanagedTesters: List<UnmanagedListGenericTester<Any?>> by lazy {
        TypeDescriptor.allListFieldTypes.map { listType ->
            listType.elementType.classifier.let { classifier ->
                UnmanagedListGenericTester(classifier, getDataSetForClassifier(classifier))
            }
        }
    }

    private val managedTesters: List<ManagedListTester<*>> by lazy {
        // TODO don't use the types directly from TypeDescriptor, but have a reference to it and modify from here
        TypeDescriptor.allListFieldTypes.map {
            when (val classifier = it.elementType.classifier) {
                RealmObject::class -> ManagedListRealmObjectTester(
                    classifier = classifier,
                    initialData = getDataSetForClassifier(classifier),
                    realm = realm,
                    property = getPropertyForClassifier(classifier)
                )
                else -> ManagedListGenericTester(
                    classifier = classifier,
                    initialData = getDataSetForClassifier<List<Any>>(classifier),
                    realm = realm,
                    property = getPropertyForClassifier(classifier)
                )
            }
        }
    }
}

/**
 * Tester for RealmLists.
 *
 * We iterate over all supported types instead of feeding the tests with parameterized tests. In
 * order to keep execution errors visible and to provide readable information as to why a particular
 * test might have failed, we use the [errorCatcher] function to capture potential assertion errors
 * and add information regarding the data type being tested.
 */
internal abstract class ListTester<T>(
    protected val classifier: KClassifier,
    protected val initialData: List<T>
) {

    abstract override fun toString(): String

    abstract fun getter()
    abstract fun add()

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
    protected fun errorCatcher(block: () -> Unit) {
        // TODO consider saving all exceptions and dump them at the end
        try {
            block()
        } catch (e: AssertionError) {
            throw AssertionError("'${toString()}' failed - ${e.message}")
        }
    }
}

/**
 * Tester for unmanaged types.
 */
internal class UnmanagedListGenericTester<T>(
    classifier: KClassifier,
    initialData: List<T>
) : ListTester<T>(classifier, initialData) {

    override fun toString(): String = "Unmanaged-$classifier"

    override fun getter() {
        errorCatcher {
            val list = RealmList<T>()
            initialData.forEachIndexed { index, e ->
                assertEquals(index, list.size)
                assertTrue(list.add(e))
                assertEquals(index + 1, list.size)
            }
        }
    }

    override fun add() {
        errorCatcher {
            val list = RealmList<T>()
            initialData.forEachIndexed { index, e ->
                assertEquals(index, list.size)
                assertTrue(list.add(e))
                assertEquals(index + 1, list.size)
            }

            // Uncomment this to make test fail
//            assertEquals(2, list.size)
        }
    }
}

/**
 * Tester for managed types. Subclasses represent the different data types supported by RealmLists.
 */
internal abstract class ManagedListTester<T>(
    classifier: KClassifier,
    initialData: List<T>,
    private val realm: Realm,
    private val property: KMutableProperty1<Sample, RealmList<T>?>
) : ListTester<T>(classifier, initialData) {

    override fun toString(): String = "Managed-$classifier"

    override fun getter() {
        errorCatcher {
            realm.writeBlocking {
                val sample = copyToRealm(Sample())
                val list = property.get(sample)

                assertNotNull(list)
                assertTrue(list.isEmpty())

                // TODO use addAll when ready
                initialData.forEachIndexed { index, e ->
                    assertAdd(this, list, index, e)
                }

                val sameList = property.get(sample)
                assertNotNull(sameList)
                assertFalse(list.isEmpty())
            }
        }
    }

    abstract fun assertAdd(realm: MutableRealm, list: RealmList<T>, index: Int, element: T)

    override fun add() {
        errorCatcher {
            realm.writeBlocking {
                val sample = copyToRealm(Sample())
                val list = property.get(sample)

                assertNotNull(list)
                assertTrue(list.isEmpty())

                initialData.forEachIndexed { index, e ->
                    assertEquals(index, list.size)
                    assertAdd(this, list, index, e)
                    assertEquals(index + 1, list.size)
                }
            }
        }
    }
}

/**
 * Tester for generic, primitive types.
 */
internal class ManagedListGenericTester<T>(
    classifier: KClassifier,
    initialData: List<T>,
    realm: Realm,
    property: KMutableProperty1<Sample, RealmList<T>?>
) : ManagedListTester<T>(classifier, initialData, realm, property) {

    override fun assertAdd(realm: MutableRealm, list: RealmList<T>, index: Int, element: T) {
        assertTrue(list.add(element))
    }
}

/**
 * Tester for [RealmObject]s.
 */
internal class ManagedListRealmObjectTester<T : RealmObject>(
    classifier: KClassifier,
    initialData: List<T>,
    realm: Realm,
    property: KMutableProperty1<Sample, RealmList<T>?>
) : ManagedListTester<T>(classifier, initialData, realm, property) {

    override fun assertAdd(realm: MutableRealm, list: RealmList<T>, index: Int, element: T) {
        // Ensure we copy the object to Realm before adding it
        assertTrue(list.add(realm.copyToRealm(element)))
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
internal val OBJECT_VALUES =
    listOf(Sample().apply { stringField = "A" }, Sample().apply { stringField = "B" })
