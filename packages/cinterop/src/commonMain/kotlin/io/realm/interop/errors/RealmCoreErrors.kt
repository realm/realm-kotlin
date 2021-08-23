package io.realm.interop.errors

/**
 * RealmCoreException represents any recoverable, non-fatal, Realm-Core exception.
 */
class RealmCoreException(val id: Int, message: String?) : RuntimeException(message) {
    override fun toString(): String {
        return "[$id]: $message"
    }
}

/**
 * RealmCoreError represents any non-recoverable, fatal, Realm-Core exception.
 */
class RealmCoreError(val id: Int, message: String?) : Error(message) {
    override fun toString(): String {
        return "[$id]: $message"
    }
}
