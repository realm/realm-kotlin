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

    override val deletions: IntArray
        get() = TODO("Not yet implemented")
    override val insertions: IntArray
        get() = TODO("Not yet implemented")
    override val changes: IntArray
        get() = TODO("Not yet implemented")
    override val deletionRanges: Array<ListChange.Range>
        get() = TODO("Not yet implemented")
    override val insertionRanges: Array<ListChange.Range>
        get() = TODO("Not yet implemented")
    override val changeRanges: Array<ListChange.Range>
        get() = TODO("Not yet implemented")
}

internal class DeletedListImpl<T : List<*>> : DeletedList<T> {
    override val state: ListChange.State
        get() = ListChange.State.DELETED
    override val list: T?
        get() = null
}