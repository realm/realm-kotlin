package io.realm.kotlin.exceptions

/**
 * Top level class for all exceptions that are Realm specific and doesn't have a better
 * existing platform exception like [IllegalArgumentException] or [IllegalStateException].
 *
 * In most cases, a subclass of this type will be thrown. Exactly which, will will be documented by
 * the individual API entry points.
 */
public open class RealmException : RuntimeException {
    public constructor() : super()
    public constructor(message: String) : super(message)
    public constructor(message: String, cause: Throwable) : super(message, cause)
    public constructor(cause: Throwable) : super(cause)
}
