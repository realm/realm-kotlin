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
public actual val RUNTIME: String = "Native"
// These causes memory mapping rendering MemoryTests to fail, so only initialize them if actually needed
public actual val OS_NAME: String by lazy { NSProcessInfo.Companion.processInfo().operatingSystemName() }
public actual val OS_VERSION: String by lazy { NSProcessInfo.Companion.processInfo().operatingSystemVersionString }

public actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    NSLogLogger(tag, logLevel)

public actual fun threadId(): ULong {
    memScoped {
        val tidVar = alloc<ULongVar>()
        pthread_threadid_np(null, tidVar.ptr)
        return tidVar.value
    }
}

public actual fun <T> T.freeze(): T = this.freeze()

public actual val <T> T.isFrozen: Boolean
    get() = this.isFrozen

public actual fun Any.ensureNeverFrozen(): Unit = this.ensureNeverFrozen()
