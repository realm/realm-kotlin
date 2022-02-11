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
