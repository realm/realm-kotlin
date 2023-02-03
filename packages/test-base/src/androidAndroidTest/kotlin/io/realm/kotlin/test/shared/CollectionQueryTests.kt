package io.realm.kotlin.test.shared

import kotlin.test.Test

/**
 * Tests for queryable collections. Lists, sets and dictionaries must implement these.
 */
abstract class CollectionQueryTests {
    @Test
    abstract fun collectionAsFlow_completesWhenParentIsDeleted()
    @Test
    abstract fun query_objectCollection()
    @Test
    abstract fun queryOnCollectionAsFlow_completesWhenParentIsDeleted()
    @Test
    abstract fun queryOnCollectionAsFlow_throwsOnInsufficientBuffers()
    @Test
    abstract fun queryOnCollectionAsFlow_backpressureStrategyDoesNotRuinInternalLogic()
    @Test
    abstract fun query_throwsOnSyntaxError()
    @Test
    abstract fun query_throwsOnUnmanagedCollection()
    @Test
    abstract fun query_throwsOnDeletedCollection()
    @Test
    abstract fun query_throwsOnClosedCollection()
}

/**
 * Similar to the class above but adds testing for embedded objects as well - applicable only to
 * lists and dictionaries.
 */
abstract class EmbeddedObjectCollectionQueryTests : CollectionQueryTests() {
    @Test
    abstract fun query_embeddedObjectCollection()
}
