package io.realm.errors

/**
 * Class for reporting problems when the primary key constraint is being broken.
 */
class RealmPrimaryKeyConstraintException : RuntimeException {
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}

/**
 * RealmError is Realm specific Error used when unrecoverable problems happen in the underlying storage engine. An
 * RealmError should never be caught or ignored. By doing so, the Realm could possibly get corrupted.
 */
class RealmError(message: String, cause: Throwable?) : Error(message, cause)
