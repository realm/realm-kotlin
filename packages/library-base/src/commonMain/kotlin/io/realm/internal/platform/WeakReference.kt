package io.realm.internal.platform

/**
 * Platform agnostic _WeakReference_ wrapper.
 *
 * Basically just wrapping underlying platform implementations.
 */
// TODO This is currently public because making it public breaks something in Kotlins type check for Strict mode
public expect class WeakReference<T : Any>(referred: T) {
    public fun clear()
    public fun get(): T?
}
