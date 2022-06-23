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
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.realmSetOf
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealmSetTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

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
        val manager = NonNullableSet(RealmSetContainer::stringSetField, STRING_VALUES)
        val tester = GenericSetTester(realm, manager)
        tester.copyToRealm()

        val nullableManager = NullableSet(RealmSetContainer::nullableStringSetField, NULLABLE_STRING_VALUES)
        val nullableTester = GenericSetTester(realm, nullableManager)
        nullableTester.copyToRealm()
    }

    @Test
    fun test() {
        realm.writeBlocking {
            val container = RealmSetContainer()
            val managedContainer = copyToRealm(container)

            val stringSet = managedContainer.stringSetField

            assertEquals(0, stringSet.size)
            assertTrue(stringSet.isEmpty())

            assertTrue(stringSet.add("A"))
            assertFalse(stringSet.add("A"))
            assertEquals(1, stringSet.size)
            assertFalse(stringSet.isEmpty())
        }
    }
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
     * Asserts content equality for two given objects. This is needed to evaluate the contents of
     * two RealmObjects.
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
            typeSafetyManager.getCollection(container)
                .forEachIndexed { index, t ->
                    val valueFromDataSet = dataSet[index]
                    assertElementsAreEqual(valueFromDataSet, t)
                }
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
internal interface GenericTypeSafetyManager<Type, Container, Collection> {

    val property: KMutableProperty1<Container, Collection>
    val dataSetToLoad: List<Type>

    override fun toString(): String // Default implementation not allowed as it comes from "Any"

    fun createContainerAndGetCollection(realm: MutableRealm? = null): Collection
    fun createPrePopulatedContainer(): Container
    fun getCollection(container: Container): Collection
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
    override val dataSetToLoad: List<T?>
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

    override fun createPrePopulatedContainer(): RealmSetContainer = RealmSetContainer()
        .also {
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
    override val dataSetToLoad: List<T>
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

    override fun createPrePopulatedContainer(): RealmSetContainer {
        return RealmSetContainer().also {
            property.get(it)
                .apply {
                    addAll(dataSetToLoad)
                }
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
}
