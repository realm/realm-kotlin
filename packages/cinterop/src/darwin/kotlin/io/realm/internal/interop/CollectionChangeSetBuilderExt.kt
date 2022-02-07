package io.realm.internal.interop

import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.get

private fun CArrayPointer<ULongVar>.asAccessor(): ArrayAccessor = { index -> this[index].toInt() }

private fun CArrayPointer<realm_wrapper.realm_collection_move_t>.asFromAccessor(): ArrayAccessor = { index -> this[index].from.toInt() }
private fun CArrayPointer<realm_wrapper.realm_collection_move_t>.asToAccessor(): ArrayAccessor = { index -> this[index].to.toInt() }

private fun CArrayPointer<realm_wrapper.realm_index_range_t>.asFromAccessor(): ArrayAccessor = { index -> this[index].from.toInt() }
private fun CArrayPointer<realm_wrapper.realm_index_range_t>.asToAccessor(): ArrayAccessor = { index -> this[index].to.toInt() }

private fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initIndicesArray(size: CArrayPointer<ULongVar>, indices: CArrayPointer<ULongVar>) =
    initIndicesArray(size[0].toInt(), indices.asAccessor())

private fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initRangesArray(size: CArrayPointer<ULongVar>, ranges: CArrayPointer<realm_wrapper.realm_index_range_t>) =
    initRangesArray(size[0].toInt(), ranges.asFromAccessor(), ranges.asToAccessor())

private fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initMovesArray(size: CArrayPointer<ULongVar>, moves: CArrayPointer<realm_wrapper.realm_collection_move_t>) =
    initMovesArray(size[0].toInt(), moves.asFromAccessor(), moves.asToAccessor())

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.insertionIndices(size: CArrayPointer<ULongVar>, indices: CArrayPointer<ULongVar>) {
    insertionIndices = initIndicesArray(size, indices)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.deletionIndices(size: CArrayPointer<ULongVar>, indices: CArrayPointer<ULongVar>) {
    deletionIndices = initIndicesArray(size, indices)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.modificationIndices(size: CArrayPointer<ULongVar>, indices: CArrayPointer<ULongVar>) {
    modificationIndices = initIndicesArray(size, indices)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.modificationIndicesAfter(size: CArrayPointer<ULongVar>, indices: CArrayPointer<ULongVar>) {
    modificationIndicesAfter = initIndicesArray(size, indices)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.deletionRanges(size: CArrayPointer<ULongVar>, ranges: CArrayPointer<realm_wrapper.realm_index_range_t>) {
    deletionRanges = initRangesArray(size, ranges)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.insertionRanges(size: CArrayPointer<ULongVar>, ranges: CArrayPointer<realm_wrapper.realm_index_range_t>) {
    insertionRanges = initRangesArray(size, ranges)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.modificationRanges(size: CArrayPointer<ULongVar>, ranges: CArrayPointer<realm_wrapper.realm_index_range_t>) {
    modificationRanges = initRangesArray(size, ranges)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.modificationRangesAfter(size: CArrayPointer<ULongVar>, ranges: CArrayPointer<realm_wrapper.realm_index_range_t>) {
    modificationRangesAfter = initRangesArray(size, ranges)
}

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.moves(size: CArrayPointer<ULongVar>, moves: CArrayPointer<realm_wrapper.realm_collection_move_t>) {
    this.moves = initMovesArray(size, moves)
}
