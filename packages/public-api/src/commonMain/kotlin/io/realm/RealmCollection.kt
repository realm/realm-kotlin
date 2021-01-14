package io.realm

import kotlinx.datetime.Instant

interface RealmCollection<E> : MutableCollection<E>, ManageableObject {
    // fun filter(): RealmQuery<E>?
    fun min(fieldName: String?): Number?
    fun max(fieldName: String?): Number?
    fun sum(fieldName: String?): Number?
    fun average(fieldName: String?): Double
    fun maxDate(fieldName: String?): Instant?
    fun minDate(fieldName: String?): Instant?
    fun deleteAllFromRealm(): Boolean
}