package io.realm.internal.interop

private fun LongArray.asAccessor(): ArrayAccessor = { index -> this[index].toInt() }

private fun Array<LongArray>.asFromAccessor(): ArrayAccessor = { index -> this[index][0].toInt() }
private fun Array<LongArray>.asToAccessor(): ArrayAccessor = { index -> this[index][1].toInt() }

private fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initIndicesArray(indices: LongArray) =
    initIndicesArray(indices.size, indices.asAccessor())

private fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initRangesArray(ranges: Array<LongArray>) =
    initRangesArray(ranges.size, ranges.asFromAccessor(), ranges.asToAccessor())

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.insertionIndices(indices: LongArray) {
    insertionIndices = initIndicesArray(indices)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.deletionIndices(indices: LongArray) {
    deletionIndices = initIndicesArray(indices)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.modificationIndices(indices: LongArray) {
    modificationIndices = initIndicesArray(indices)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.modificationIndicesAfter(indices: LongArray) {
    modificationIndicesAfter = initIndicesArray(indices)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.deletionRanges(ranges: Array<LongArray>) {
    deletionRanges = initRangesArray(ranges)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.insertionRanges(ranges: Array<LongArray>) {
    insertionRanges = initRangesArray(ranges)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.modificationRanges(ranges: Array<LongArray>) {
    modificationRanges = initRangesArray(ranges)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.modificationRangesAfter(ranges: Array<LongArray>) {
    modificationRangesAfter = initRangesArray(ranges)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.moves(moves: Array<LongArray>) {
    this.moves = initMovesArray(moves.size, moves.asFromAccessor(), moves.asToAccessor())
}
