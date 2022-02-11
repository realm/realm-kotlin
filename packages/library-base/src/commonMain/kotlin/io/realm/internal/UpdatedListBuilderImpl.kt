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
import io.realm.notifications.ListChange
import io.realm.notifications.UpdatedList
import io.realm.notifications.UpdatedListImpl

internal class UpdatedListBuilderImpl<T : List<*>>(val list: T, change: NativePointer) :
    CollectionChangeSetBuilder<UpdatedList<T>, ListChange.Range>() {

    init {
        RealmInterop.realm_collection_changes_get_changes(change, this)
        RealmInterop.realm_collection_changes_get_ranges(change, this)
    }

    override fun initIndicesArray(size: Int, indicesAccessor: ArrayAccessor): IntArray =
        IntArray(size) { index -> indicesAccessor(index) }

    override fun initRangesArray(
        size: Int,
        fromAccessor: ArrayAccessor,
        toAccessor: ArrayAccessor
    ): Array<ListChange.Range> =
        Array(size) { index ->
            val from: Int = fromAccessor(index)
            val to: Int = toAccessor(index)
            ListChange.Range(from, to - from)
        }

    override fun build(): UpdatedList<T> = UpdatedListImpl(
        list = list,
        deletions = deletionIndices,
        insertions = insertionIndices,
        changes = modificationIndicesAfter,
        deletionRanges = deletionRanges,
        insertionRanges = insertionRanges,
        changeRanges = modificationRangesAfter
    )
}
