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

package io.realm.kotlin.internal.interop

typealias ArrayAccessor = (index: Int) -> Int

abstract class CollectionChangeSetBuilder<T, R> {

    lateinit var insertionIndices: IntArray
    lateinit var deletionIndices: IntArray
    lateinit var modificationIndices: IntArray
    lateinit var modificationIndicesAfter: IntArray

    lateinit var deletionRanges: Array<R>
    lateinit var insertionRanges: Array<R>
    lateinit var modificationRanges: Array<R>
    lateinit var modificationRangesAfter: Array<R>

    var movesCount: Int = 0

    fun isEmpty(): Boolean = insertionIndices.isEmpty() &&
        modificationIndices.isEmpty() &&
        deletionIndices.isEmpty() &&
        movesCount == 0

    abstract fun initIndicesArray(size: Int, indicesAccessor: ArrayAccessor): IntArray
    abstract fun initRangesArray(size: Int, fromAccessor: ArrayAccessor, toAccessor: ArrayAccessor): Array<R>

    abstract fun build(): T
}
