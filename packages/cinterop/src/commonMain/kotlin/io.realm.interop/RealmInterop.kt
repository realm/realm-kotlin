package io.realm.interop

import io.realm.runtimeapi.NativePointer

expect object RealmInterop {

    fun realm_get_library_version(): String
    fun realm_config_new(): NativePointer
    fun realm_config_set_path(config: NativePointer, path: String): Boolean
    fun realm_open(config: NativePointer): NativePointer
    fun realm_close(realm: NativePointer)
}
