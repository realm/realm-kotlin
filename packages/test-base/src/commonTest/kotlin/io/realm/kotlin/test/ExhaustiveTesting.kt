package io.realm.kotlin.test

import io.realm.kotlin.MutableRealm
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1

/**
 * Dataset container and helper operations. Given a [property] this manager returns the appropriate
 * [dataSetToLoad] for exhaustive type testing.
 *
 * TODO could also be used for RealmLists - https://github.com/realm/realm-kotlin/issues/941
 */
internal interface GenericTypeSafetyManager<Type, Container, RealmCollection> {

    /**
     * Property from the [Container] class containing a [RealmCollection] attribute.
     */
    val property: KMutableProperty1<Container, RealmCollection>

    /**
     * Dataset used to test the validity of the [RealmCollection] operations.
     *
     * See 'RealmListTests' for values used here.
     */
    val dataSetToLoad: List<Type>

    override fun toString(): String // Default implementation not allowed as it comes from "Any"

    /**
     * Creates a managed [Container] from which we can access the [property] pointing to an empty,
     * managed [RealmCollection].
     */
    fun createContainerAndGetCollection(realm: MutableRealm): RealmCollection

    /**
     * Creates a managed [Container] whose [property] contains a pre-populated [RealmCollection].
     */
    fun createPrePopulatedContainer(): Container

    /**
     * Convenience function that retrieves the given [property] for the provided [container].
     */
    fun getCollection(container: Container): RealmCollection
}

/**
 * Provides an execution block collection exhaustive tests. In case the test fails
 */
internal interface ErrorCatcher {

    val classifier: KClassifier

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
