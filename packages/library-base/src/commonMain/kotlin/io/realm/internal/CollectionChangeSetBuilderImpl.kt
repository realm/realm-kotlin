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

package io.realm.internal

import io.realm.internal.interop.ArrayAccessor
import io.realm.internal.interop.CollectionChangeSetBuilder
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.notifications.CollectionChangeSet

internal class CollectionChangeSetBuilderImpl(change: NativePointer) :
    CollectionChangeSetBuilder<CollectionChangeSet, CollectionChangeSet.Range>() {

    init {
        RealmInterop.realm_collection_changes_get_indices(change, this)
        RealmInterop.realm_collection_changes_get_ranges(change, this)
    }

    override fun initIndicesArray(size: Int, indicesAccessor: ArrayAccessor): IntArray =
        IntArray(size) { index -> indicesAccessor(index) }

    override fun initRangesArray(
        size: Int,
        fromAccessor: ArrayAccessor,
        toAccessor: ArrayAccessor
    ): Array<CollectionChangeSet.Range> =
        Array(size) { index ->
            val from: Int = fromAccessor(index)
            val to: Int = toAccessor(index)
            CollectionChangeSet.Range(from, to - from)
        }

    override fun build(): CollectionChangeSet = object : CollectionChangeSet {
        override val deletions: IntArray =
            this@CollectionChangeSetBuilderImpl.deletionIndices

        override val insertions: IntArray =
            this@CollectionChangeSetBuilderImpl.insertionIndices

        override val changes: IntArray =
            this@CollectionChangeSetBuilderImpl.modificationIndicesAfter

        override val deletionRanges: Array<CollectionChangeSet.Range> =
            this@CollectionChangeSetBuilderImpl.deletionRanges

        override val insertionRanges: Array<CollectionChangeSet.Range> =
            this@CollectionChangeSetBuilderImpl.insertionRanges

        override val changeRanges: Array<CollectionChangeSet.Range> =
            this@CollectionChangeSetBuilderImpl.modificationRangesAfter
    }
}
