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

package io.realm.kotlin.internal

import io.realm.kotlin.internal.interop.ArrayAccessor
import io.realm.kotlin.internal.interop.CollectionChangeSetBuilder
import io.realm.kotlin.internal.interop.RealmChangesPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.notifications.ListChangeSet
import io.realm.kotlin.notifications.ListChangeSet.Range
import io.realm.kotlin.notifications.SetChangeSet

internal abstract class CollectionChangeSetBuilderImpl<T>(
    change: RealmChangesPointer
) : CollectionChangeSetBuilder<T, Range>() {

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
    ): Array<Range> =
        Array(size) { index ->
            val from: Int = fromAccessor(index)
            val to: Int = toAccessor(index)
            Range(from, to - from)
        }
}

internal class ListChangeSetBuilderImpl(
    change: RealmChangesPointer
) : CollectionChangeSetBuilderImpl<ListChangeSet>(change) {

    override fun build(): ListChangeSet = object : ListChangeSet {
        override val deletions: IntArray =
            this@ListChangeSetBuilderImpl.deletionIndices

        override val insertions: IntArray =
            this@ListChangeSetBuilderImpl.insertionIndices

        override val changes: IntArray =
            this@ListChangeSetBuilderImpl.modificationIndicesAfter

        override val deletionRanges: Array<Range> =
            this@ListChangeSetBuilderImpl.deletionRanges

        override val insertionRanges: Array<Range> =
            this@ListChangeSetBuilderImpl.insertionRanges

        override val changeRanges: Array<Range> =
            this@ListChangeSetBuilderImpl.modificationRangesAfter
    }
}

internal class SetChangeSetBuilderImpl(
    change: RealmChangesPointer
) : CollectionChangeSetBuilderImpl<SetChangeSet>(change) {

    override fun build(): SetChangeSet = object : SetChangeSet {
        override val insertions: Int
            get() = this@SetChangeSetBuilderImpl.insertionIndices.size
        override val deletions: Int
            get() = this@SetChangeSetBuilderImpl.deletionIndices.size
    }
}
