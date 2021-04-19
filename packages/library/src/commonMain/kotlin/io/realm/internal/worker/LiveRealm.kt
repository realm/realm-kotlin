package io.realm.internal.worker

import io.realm.RealmConfiguration
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.jvm.Volatile


open class LiveRealm(val configuration: RealmConfiguration) {

    @Volatile
    internal var dbPointer: NativePointer? = null

    init {
        dbPointer = RealmInterop.realm_open(configuration.nativeConfig)
    }

    fun isClosed(): Boolean {
        return dbPointer == null
    }

    internal inline fun checkClosed() {
        if (dbPointer == null) {
            throw IllegalStateException("Realm has already been closed: ${configuration.path}")
        }
    }

    internal open fun isFrozen(): Boolean {
        return false
    }

    open fun close() {
        dbPointer?.let {
            RealmInterop.realm_close(it)
        }
        dbPointer = null
    }
}