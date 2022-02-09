package io.realm.internal.interop

import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.get
import kotlin.reflect.KMutableProperty0

private fun CArrayPointer<ULongVar>.asAccessor(): ArrayAccessor = { index -> this[index].toInt() }

private fun CArrayPointer<realm_wrapper.realm_collection_move_t>.asFromAccessor(): ArrayAccessor =
    { index -> this[index].from.toInt() }

private fun CArrayPointer<realm_wrapper.realm_collection_move_t>.asToAccessor(): ArrayAccessor =
    { index -> this[index].to.toInt() }

private fun CArrayPointer<realm_wrapper.realm_index_range_t>.asFromAccessor(): ArrayAccessor =
    { index -> this[index].from.toInt() }

private fun CArrayPointer<realm_wrapper.realm_index_range_t>.asToAccessor(): ArrayAccessor =
    { index -> this[index].to.toInt() }

private fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initIndicesArray(
    size: CArrayPointer<ULongVar>,
    indices: CArrayPointer<ULongVar>
) = initIndicesArray(size[0].toInt(), indices.asAccessor())

private fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initRangesArray(
    size: CArrayPointer<ULongVar>,
    ranges: CArrayPointer<realm_wrapper.realm_index_range_t>
) = initRangesArray(size[0].toInt(), ranges.asFromAccessor(), ranges.asToAccessor())

private fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initMovesArray(
    size: CArrayPointer<ULongVar>,
    moves: CArrayPointer<realm_wrapper.realm_collection_move_t>
) = initMovesArray(size[0].toInt(), moves.asFromAccessor(), moves.asToAccessor())

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initIndicesArray(
    array: KMutableProperty0<IntArray>,
    size: CArrayPointer<ULongVar>,
    indices: CArrayPointer<ULongVar>
) = array.set(initIndicesArray(size, indices))

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initRangesArray(
    array: KMutableProperty0<Array<R>>,
    size: CArrayPointer<ULongVar>,
    ranges: CArrayPointer<realm_wrapper.realm_index_range_t>
) = array.set(initRangesArray(size, ranges))

fun <T, R, M> CollectionChangeSetBuilder<T, R, M>.initMovesArray(
    array: KMutableProperty0<Array<M>>,
    size: CArrayPointer<ULongVar>,
    moves: CArrayPointer<realm_wrapper.realm_collection_move_t>
) = array.set(initMovesArray(size, moves))
