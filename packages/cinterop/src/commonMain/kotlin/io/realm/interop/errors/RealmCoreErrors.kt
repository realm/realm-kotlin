package io.realm.interop.errors

import io.realm.interop.ErrorType

/**
 * RealmCoreException represents any recoverable, non-fatal, Realm-Core exception.
 */
class RealmCoreException(val errorType: ErrorType, message: String?) : RuntimeException(message) {
    override fun toString(): String {
        return "[$errorType]: $message"
    }
}

/**
 * RealmCoreError represents any non-recoverable, fatal, Realm-Core exception.
 */
class RealmCoreError(val errorType: ErrorType, message: String?) : Error(message) {
    override fun toString(): String {
        return "[$errorType]: $message"
    }
}
