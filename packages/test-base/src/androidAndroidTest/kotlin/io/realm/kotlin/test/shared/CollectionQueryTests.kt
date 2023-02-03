package io.realm.kotlin.test.shared

import kotlin.test.Test

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

abstract class EmbeddedObjectCollectionQueryTests : CollectionQueryTests() {
    @Test
    abstract fun query_embeddedObjectCollection()
}
