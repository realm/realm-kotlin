package io.realm.internal.interop

class CollectionChanges {
    lateinit var insertsIndices: LongArray
    lateinit var modificationsIndices: LongArray
    lateinit var modificationsAfterIndices: LongArray
    lateinit var deletesIndices: LongArray
    lateinit var moves: LongArray

    fun isEmpty(): Boolean = insertsIndices.isEmpty() &&
            modificationsIndices.isEmpty() &&
            deletesIndices.isEmpty() &&
            moves.isEmpty()
}

class CollectionRanges {
    lateinit var insertRanges: LongArray
    lateinit var modificationsRanges: LongArray
    lateinit var modificationsRangesAfter: LongArray
    lateinit var deleteRanges: LongArray
    lateinit var moves: LongArray
}