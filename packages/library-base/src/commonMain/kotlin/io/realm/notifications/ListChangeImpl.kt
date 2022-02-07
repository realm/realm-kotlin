package io.realm.notifications

internal class InitialListImpl<T : List<*>>(override val list: T) : InitialList<T>

@Suppress("LongParameterList")
internal class UpdatedListImpl<T : List<*>>(
    override val list: T,
    override val deletions: IntArray,
    override val insertions: IntArray,
    override val changes: IntArray,
    override val deletionRanges: Array<ListChange.Range>,
    override val insertionRanges: Array<ListChange.Range>,
    override val changeRanges: Array<ListChange.Range>
) : UpdatedList<T>

internal class DeletedListImpl<T : List<*>> : DeletedList<T> {
    override val list: T?
        get() = null
}
