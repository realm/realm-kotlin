package io.realm.internal

import io.realm.interop.NativePointer

/**
 * TODO
 */
internal interface Freezable<T> {
    fun freeze(frozenPointer: NativePointer, frozenRealm: RealmReference): T
    fun thaw(livePointer: NativePointer, liveRealm: RealmReference): T
}
