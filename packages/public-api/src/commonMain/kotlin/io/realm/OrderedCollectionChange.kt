package io.realm

import io.realm.base.BaseRealmModel

// QUESTION: It looks like Sets will also have an implicit order so should they also use this class?
//  Otherwise we might need seperate change classes for List/Results, Sets and Maps.
interface OrderedCollectionChange<E, T: OrderedRealmCollection<E>> {
    val collection: T
    fun getDeletions(): IntArray { TODO() }
    fun getInsertions(): IntArray { TODO() }
    fun getChanges(): IntArray { TODO() }
    fun getDeletionRanges(): Array<Range> { TODO() }
    fun getInsertionRanges(): Array<Range> { TODO() }
    fun getChangeRanges(): Array<Range> { TODO() }

    class Range(val startIndex: Int, val length: Int)
}