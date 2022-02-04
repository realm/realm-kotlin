package io.realm.internal.platform

import io.realm.log.LogLevel
import io.realm.log.RealmLogger
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSProcessInfo
import platform.posix.pthread_threadid_np
import kotlin.native.concurrent.ensureNeverFrozen
import kotlin.native.concurrent.freeze
import kotlin.native.concurrent.isFrozen

@Suppress("MayBeConst") // Cannot make expect/actual const
internal actual val RUNTIME: String = "Native"
// These causes memory mapping rendering MemoryTests to fail, so only initialize them if actually needed
internal actual val OS_NAME: String by lazy { NSProcessInfo.Companion.processInfo().operatingSystemName() }
internal actual val OS_VERSION: String by lazy { NSProcessInfo.Companion.processInfo().operatingSystemVersionString }

internal actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    NSLogLogger(tag, logLevel)

internal actual fun threadId(): ULong {
    memScoped {
        val tidVar = alloc<ULongVar>()
        pthread_threadid_np(null, tidVar.ptr)
        return tidVar.value
    }
}

internal actual fun <T> T.freeze(): T = this.freeze()

internal actual val <T> T.isFrozen: Boolean
    get() = this.isFrozen

internal actual fun Any.ensureNeverFrozen() = this.ensureNeverFrozen()
