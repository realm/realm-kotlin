package io.realm.internal.platform

/**
 * Platform agnostic _WeakReference_ wrapper.
 *
 * Basically just wrapping underlying platform implementations.
 */
expect class WeakReference<T : Any>(referred: T) {
    fun clear()
    fun get(): T?
}
