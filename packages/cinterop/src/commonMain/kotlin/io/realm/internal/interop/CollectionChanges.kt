package io.realm.internal.interop

fun initIndicesArray(size: LongArray): LongArray = LongArray(size[0].toInt())
fun initRangeArray(size: LongArray): Array<LongArray> = Array(size[0].toInt()) { LongArray(2) }

class CollectionChanges(
    insertionCount: LongArray,
    modificationCount: LongArray,
    deletionCount: LongArray,
    movesCount: LongArray,
) {
    var insertionIndices: LongArray = initIndicesArray(insertionCount)
    var modificationIndices: LongArray = initIndicesArray(modificationCount)
    val modificationIndicesAfter: LongArray = initIndicesArray(modificationCount)
    val deletionIndices: LongArray = initIndicesArray(deletionCount)
    val moves: Array<LongArray> = initRangeArray(movesCount)

    fun isEmpty(): Boolean = insertionIndices.isEmpty() &&
            modificationIndices.isEmpty() &&
            deletionIndices.isEmpty() &&
            moves.isEmpty()
}

class CollectionRanges(
    insertRangesCount: LongArray,
    deleteRangesCount: LongArray,
    modificationRangesCount: LongArray,
    movesCount: LongArray
) {
    val insertionRanges: Array<LongArray> = initRangeArray(insertRangesCount)
    val modificationRanges: Array<LongArray> = initRangeArray(modificationRangesCount)
    val modificationRangesAfter: Array<LongArray> = initRangeArray(modificationRangesCount)
    val deletionRanges: Array<LongArray> = initRangeArray(deleteRangesCount)
    val moves: Array<LongArray> = initRangeArray(movesCount)
}