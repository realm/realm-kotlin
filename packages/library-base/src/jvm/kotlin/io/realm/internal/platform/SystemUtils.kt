package io.realm.internal.platform

@Suppress("MayBeConst") // Cannot make expect/actual const
internal actual val RUNTIME: String = "JVM"

internal actual fun threadId(): ULong {
    return Thread.currentThread().id.toULong()
}

internal actual fun <T> T.freeze(): T = this

internal actual val <T> T.isFrozen: Boolean
    get() = false

internal actual fun Any.ensureNeverFrozen() {}
