package io.realm

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface RealmCollection<E> : Collection<E>, ManageableObject {
    fun deleteAllFromRealm(): Boolean
}