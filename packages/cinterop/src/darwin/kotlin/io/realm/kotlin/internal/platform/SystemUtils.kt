package io.realm.kotlin.internal.platform

public actual fun <T> T.freeze(): T = this

public actual val <T> T.isFrozen: Boolean
    get() = false

public actual fun Any.ensureNeverFrozen() {
    /* Do nothing */
}
