package io.realm.interop

import io.realm.runtimeapi.NativePointer

// JVM/Android specific pointer wrapper
class LongPointerWrapper(val ptr : Long): NativePointer {
    fun ptr() :  Long{
        return ptr
    }
}

actual object RealmInterop {
    actual fun realm_get_library_version(): String {
        realm_wrapper.
        realm_get_library_version()
        return realmc.realm_get_library_version()
    }

    actual fun realm_config_new(): io.realm.interop.NativePointer {
        return LongPointerWrapper(realmc.realm_config_new()) as io.realm.interop.NativePointer
    }

    actual fun realm_config_set_path(config: io.realm.interop.NativePointer, path: String): Boolean {
        return realmc.realm_config_set_path((config as LongPointerWrapper).ptr, path)
    }

    actual fun realm_open(config: io.realm.interop.NativePointer): io.realm.interop.NativePointer {
        // Compiler complains without useless cast
        return realmc.realm_open((config as LongPointerWrapper).ptr) as io.realm.interop.NativePointer
    }

    actual fun realm_close(realm: io.realm.interop.NativePointer) {
        realmc.realm_open((realm as LongPointerWrapper).ptr)
    }
}
