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
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TypeDescriptor
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealmSetTests {

    private val descriptors = TypeDescriptor.allSetFieldTypes

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private val managedTesters: List<SetApiTester<*, RealmSetContainer>> by lazy {
        descriptors.mapNotNull {
            val elementType = it.elementType
            when (val classifier = elementType.classifier) {
                String::class ->
                    GenericSetTester(realm, getTypeSafety(classifier, elementType.nullable))
                else -> null // TODO This shouldn't return null, it's to not have to deal with all supported types at once
            }
        }
    }

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(
            schema = setOf(RealmSetContainer::class)
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
    fun unmanaged() {
        // No need to be exhaustive here, just checking delegation works
        val set = realmSetOf<RealmSetContainer>()
        assertTrue(set.isEmpty())
        set.add(RealmSetContainer().apply { stringField = "Dummy" })
        assertEquals(1, set.size)
    }

    @Test
    fun copyToRealm() {
        for (tester in managedTesters) {
            tester.copyToRealm()
        }

        // val manager = NonNullableSet(RealmSetContainer::stringSetField, STRING_VALUES)
        // val tester = GenericSetTester(realm, manager)
        // tester.copyToRealm()
        //
        // val nullableManager = NullableSet(RealmSetContainer::nullableStringSetField, NULLABLE_STRING_VALUES)
        // val nullableTester = GenericSetTester(realm, nullableManager)
        // nullableTester.copyToRealm()
    }

    private fun getTypeSafety(classifier: KClassifier, nullable: Boolean): SetTypeSafetyManager<*> =
        when {
            nullable -> NullableSet(
                property = RealmSetContainer.nullableProperties[classifier]!!,
                dataSetToLoad = getDataSetForClassifier(classifier, true)
            )
            else -> NonNullableSet(
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
        RealmObject::class -> OBJECT_VALUES
        else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
    } as List<T>
}

/**
 * TODO
 */
internal interface SetApiTester<T, Container> {

    val realm: Realm

    override fun toString(): String
    fun copyToRealm()
    // fun add()
    // fun clear()

    /**
     * Asserts structural equality for two given objects. This is needed to evaluate the contents of
     * ByteArrays and RealmObjects.
     */
    fun assertElementsAreEqual(expected: T, actual: T)

    /**
     * TODO
     */
    fun assertContainerAndCleanup(assertion: (Container) -> Unit)

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

/**
 * TODO
 */
internal class GenericSetTester<T>(
    override val realm: Realm,
    private val typeSafetyManager: SetTypeSafetyManager<T>
) : SetApiTester<T, RealmSetContainer> {

    override fun toString(): String = "GenericSetTester"

    override fun copyToRealm() {
        val dataSet = typeSafetyManager.dataSetToLoad

        val assertions = { container: RealmSetContainer ->
            dataSet.containsAll(typeSafetyManager.getCollection(container))
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

    override fun assertElementsAreEqual(expected: T, actual: T) = assertEquals(expected, actual)

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
            delete(findLatest(container)!!)
        }
    }
}

/**
 * TODO
 */
internal interface GenericTypeSafetyManager<Type, Container, RealmCollection> {

    val property: KMutableProperty1<Container, RealmCollection>
    val dataSetToLoad: Collection<Type>

    override fun toString(): String // Default implementation not allowed as it comes from "Any"

    fun createContainerAndGetCollection(realm: MutableRealm? = null): RealmCollection
    fun createPrePopulatedContainer(): Container
    fun getCollection(container: Container): RealmCollection
}

/**
 * TODO
 */
internal interface SetTypeSafetyManager<T> :
    GenericTypeSafetyManager<T, RealmSetContainer, RealmSet<T>> {
    override fun getCollection(container: RealmSetContainer): RealmSet<T> = property.get(container)
}

/**
 * TODO
 */
internal class NullableSet<T>(
    override val property: KMutableProperty1<RealmSetContainer, RealmSet<T?>>,
    override val dataSetToLoad: Collection<T?>
) : SetTypeSafetyManager<T?> {

    override fun toString(): String = property.name

    override fun createContainerAndGetCollection(realm: MutableRealm?): RealmSet<T?> {
        val container = RealmSetContainer().let {
            realm?.copyToRealm(it) ?: it
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

/**
 * TODO
 */
internal class NonNullableSet<T>(
    override val property: KMutableProperty1<RealmSetContainer, RealmSet<T>>,
    override val dataSetToLoad: Collection<T>
) : SetTypeSafetyManager<T> {

    override fun toString(): String = property.name

    override fun createContainerAndGetCollection(realm: MutableRealm?): RealmSet<T> {
        val container = RealmSetContainer().let {
            realm?.copyToRealm(it) ?: it
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

/**
 * TODO
 */
class RealmSetContainer : RealmObject {
    var stringField: String = "Realm"

    var stringSetField: RealmSet<String> = realmSetOf()

    var nullableStringSetField: RealmSet<String?> = realmSetOf()

    companion object {
        @Suppress("UNCHECKED_CAST")
        val nonNullableProperties = listOf(
            String::class to RealmSetContainer::stringSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Byte::class to RealmSetContainer::byteSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Char::class to RealmSetContainer::charSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Short::class to RealmSetContainer::shortSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Int::class to RealmSetContainer::intSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Long::class to RealmSetContainer::longSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Boolean::class to RealmSetContainer::booleanSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Float::class to RealmSetContainer::floatSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Double::class to RealmSetContainer::doubleSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // RealmInstant::class to RealmSetContainer::timestampSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // ObjectId::class to RealmSetContainer::objectIdSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // RealmObject::class to RealmSetContainer::objectSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>
        ).toMap()

        @Suppress("UNCHECKED_CAST")
        val nullableProperties = listOf(
            String::class to RealmSetContainer::nullableStringSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            // Byte::class to RealmSetContainer::byteSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Char::class to RealmSetContainer::charSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Short::class to RealmSetContainer::shortSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Int::class to RealmSetContainer::intSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Long::class to RealmSetContainer::longSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Boolean::class to RealmSetContainer::booleanSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Float::class to RealmSetContainer::floatSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // Double::class to RealmSetContainer::doubleSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // RealmInstant::class to RealmSetContainer::timestampSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // ObjectId::class to RealmSetContainer::objectIdSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            // RealmObject::class to RealmSetContainer::objectSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>
        ).toMap()
    }
}
