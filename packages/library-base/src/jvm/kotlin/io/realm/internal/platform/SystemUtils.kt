package io.realm.internal.platform

@Suppress("MayBeConst") // Cannot make expect/actual const
public actual val RUNTIME: String = "JVM"

public actual fun threadId(): ULong {
    return Thread.currentThread().id.toULong()
}

public actual fun <T> T.freeze(): T = this

public actual val <T> T.isFrozen: Boolean
    get() = false

public actual fun Any.ensureNeverFrozen() {}
