package io.realm.notifications

import io.realm.internal.interop.CollectionRanges
import io.realm.internal.interop.CollectionChanges

internal class InitialListImpl<T : List<*>>(override val list: T) : InitialList<T> {
    override val state: ListChange.State
        get() = ListChange.State.INITIAL
}

internal class UpdatedListImpl<T : List<*>>(override val list: T, changes: CollectionChanges, ranges: CollectionRanges) : UpdatedList<T> {
    override val state: ListChange.State
        get() = ListChange.State.UPDATED

    override val deletions: IntArray = processIndices(changes.deletionIndices)
    override val insertions: IntArray = processIndices(changes.insertionIndices)
    override val changes: IntArray = processIndices(changes.modificationIndicesAfter)

    override val deletionRanges: Array<ListChange.Range> = processRanges(ranges.deletionRanges)
    override val insertionRanges: Array<ListChange.Range> = processRanges(ranges.insertionRanges)
    override val changeRanges: Array<ListChange.Range> = processRanges(ranges.modificationRangesAfter)

    private fun processIndices(array: LongArray): IntArray =
        IntArray(array.size) { index -> array[index].toInt() }

    private fun processRanges(array: Array<LongArray>): Array<ListChange.Range> =
        Array(array.size) { index ->
            ListChange.Range(array[index][0].toInt(), array[index][1].toInt())
        }
}

internal class DeletedListImpl<T : List<*>> : DeletedList<T> {
    override val state: ListChange.State
        get() = ListChange.State.DELETED
    override val list: T?
        get() = null
}