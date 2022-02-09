package io.realm.internal.interop

import kotlin.reflect.KMutableProperty0

private fun LongArray.asAccessor(): ArrayAccessor = { index -> this[index].toInt() }

private fun Array<LongArray>.asFromAccessor(): ArrayAccessor = { index -> this[index][0].toInt() }
private fun Array<LongArray>.asToAccessor(): ArrayAccessor = { index -> this[index][1].toInt() }

private fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initIndicesArray(indices: LongArray) =
    initIndicesArray(indices.size, indices.asAccessor())

private fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initRangesArray(ranges: Array<LongArray>) =
    initRangesArray(ranges.size, ranges.asFromAccessor(), ranges.asToAccessor())

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initIndicesArray(
    array: KMutableProperty0<IntArray>,
    indices: LongArray
) = array.set(initIndicesArray(indices))

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initRangesArray(
    array: KMutableProperty0<Array<R>>,
    ranges: Array<LongArray>
) = array.set(initRangesArray(ranges))

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initMovesArray(
    array: KMutableProperty0<Array<M>>,
    moves: Array<LongArray>
) = array.set(initMovesArray(moves.size, moves.asFromAccessor(), moves.asToAccessor()))
