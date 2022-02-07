package io.realm.internal.interop

typealias ArrayAccessor = (index: Int) -> Int

abstract class CollectionChangeSetBuilder<T, R, M> {
    lateinit var insertionIndices: IntArray
    lateinit var deletionIndices: IntArray
    lateinit var modificationIndices: IntArray
    lateinit var modificationIndicesAfter: IntArray
    lateinit var moves: Array<M>

    lateinit var deletionRanges: Array<R>
    lateinit var insertionRanges: Array<R>
    lateinit var modificationRanges: Array<R>
    lateinit var modificationRangesAfter: Array<R>

    fun isEmpty(): Boolean = insertionIndices.isEmpty() &&
        modificationIndices.isEmpty() &&
        deletionIndices.isEmpty() &&
        moves.isEmpty()

    abstract fun initIndicesArray(size: Int, indicesAccessor: ArrayAccessor): IntArray
    abstract fun initMovesArray(size: Int, fromAccessor: ArrayAccessor, toAccessor: ArrayAccessor): Array<M>
    abstract fun initRangesArray(size: Int, fromAccessor: ArrayAccessor, toAccessor: ArrayAccessor): Array<R>

    abstract fun build(): T
}
