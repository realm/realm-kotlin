package io.realm.internal.platform

import io.realm.log.RealmLogger
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.pthread_threadid_np
import kotlin.native.concurrent.ensureNeverFrozen
import kotlin.native.concurrent.freeze
import kotlin.native.concurrent.isFrozen

actual fun createDefaultSystemLogger(tag: String): RealmLogger = NSLogLogger(tag)

actual fun threadId(): ULong {
    memScoped {
        val tidVar = alloc<ULongVar>()
        pthread_threadid_np(null, tidVar.ptr)
        return tidVar.value
    }
}

actual fun <T> T.freeze(): T = this.freeze()

actual val <T> T.isFrozen: Boolean
    get() = this.isFrozen

actual fun Any.ensureNeverFrozen() = this.ensureNeverFrozen()
