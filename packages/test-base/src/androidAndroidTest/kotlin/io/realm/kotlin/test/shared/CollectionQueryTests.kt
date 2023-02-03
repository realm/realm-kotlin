package io.realm.kotlin.test.shared

import kotlin.test.Test

/**
 * Tests for queryable collections. Lists, sets and dictionaries must implement these.
 */
interface CollectionQueryTests {
    @Test
    fun collectionAsFlow_completesWhenParentIsDeleted()
    @Test
    fun query_objectCollection()
    @Test
    fun queryOnCollectionAsFlow_completesWhenParentIsDeleted()
    @Test
    fun queryOnCollectionAsFlow_throwsOnInsufficientBuffers()
    @Test
    fun queryOnCollectionAsFlow_backpressureStrategyDoesNotRuinInternalLogic()
    @Test
    fun query_throwsOnSyntaxError()
    @Test
    fun query_throwsOnUnmanagedCollection()
    @Test
    fun query_throwsOnDeletedCollection()
    @Test
    fun query_throwsOnClosedCollection()
}

/**
 * Similar to the interface above but adds testing for embedded objects as well - applicable only to
 * lists and dictionaries.
 */
interface EmbeddedObjectCollectionQueryTests : CollectionQueryTests {
    @Test
    fun query_embeddedObjectCollection()
}
