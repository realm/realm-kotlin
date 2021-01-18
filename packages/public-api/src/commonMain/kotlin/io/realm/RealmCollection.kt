package io.realm

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface RealmCollection<E> : MutableCollection<E>, ManageableObject {
    // Ideally these would be provided by `Queryable<
    fun deleteAllFromRealm(): Boolean
}