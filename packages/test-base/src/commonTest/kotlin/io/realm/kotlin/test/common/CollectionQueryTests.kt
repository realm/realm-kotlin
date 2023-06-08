/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.test.common

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
