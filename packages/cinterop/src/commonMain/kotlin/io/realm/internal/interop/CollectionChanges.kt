package io.realm.internal.interop

typealias ArrayAccessor = (index: Int) -> Int

abstract class CollectionChangeBuilder<T, R> {
    lateinit var insertionIndices: IntArray
    lateinit var deletionIndices: IntArray
    lateinit var modificationIndices: IntArray
    lateinit var modificationIndicesAfter: IntArray
    lateinit var moves: IntArray

    lateinit var deletionRanges: Array<R>
    lateinit var insertionRanges: Array<R>
    lateinit var modificationRanges: Array<R>
    lateinit var modificationRangesAfter: Array<R>

    fun insertionIndices(size: Int, indicesAccessor: ArrayAccessor) {
        insertionIndices = indices(size, indicesAccessor)
    }

    fun deletionIndices(size: Int, indicesAccessor: ArrayAccessor) {
        deletionIndices = indices(size, indicesAccessor)
    }

    fun modificationIndices(size: Int, indicesAccessor: ArrayAccessor) {
        modificationIndices = indices(size, indicesAccessor)
    }

    fun modificationIndicesAfter(size: Int, indicesAccessor: ArrayAccessor) {
        modificationIndicesAfter = indices(size, indicesAccessor)
    }

    fun deletionRanges(size: Int, fromAccessor: ArrayAccessor, toAccessor: ArrayAccessor) {
        deletionRanges = ranges(size, fromAccessor, toAccessor)
    }

    fun insertionRanges(size: Int, fromAccessor: ArrayAccessor, toAccessor: ArrayAccessor) {
        insertionRanges = ranges(size, fromAccessor, toAccessor)
    }

    fun modificationRanges(size: Int, fromAccessor: ArrayAccessor, toAccessor: ArrayAccessor) {
        modificationRanges = ranges(size, fromAccessor, toAccessor)
    }

    fun modificationRangesAfter(size: Int, fromAccessor: ArrayAccessor, toAccessor: ArrayAccessor) {
        modificationRangesAfter = ranges(size, fromAccessor, toAccessor)
    }

    fun moves(size: Int, fromAccessor: ArrayAccessor, toAccessor: ArrayAccessor) {
        // Only used to check if change set is empty
        moves = IntArray(size) { index -> fromAccessor(index) }
    }

    fun isEmpty(): Boolean = insertionIndices.isEmpty() &&
        modificationIndices.isEmpty() &&
        deletionIndices.isEmpty() &&
        moves.isEmpty()

    abstract fun indices(size: Int, indicesAccessor: ArrayAccessor): IntArray
    abstract fun ranges(size: Int, fromAccessor: ArrayAccessor, toAccessor: ArrayAccessor): Array<R>

    abstract fun build(): T
}
