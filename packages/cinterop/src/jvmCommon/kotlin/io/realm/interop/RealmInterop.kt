package io.realm.interop

import io.realm.runtimeapi.NativePointer

// JVM/Android specific pointer wrapper
class LongPointerWrapper(val ptr : Long): NativePointer {
    // FIXME Maybe make private property with public getter, to make it directly accessible from Swig Java module
    fun ptr() :  Long{
        return ptr
    }
}

actual object RealmInterop {
    actual fun realm_get_library_version(): String {
        return realmc.realm_get_library_version()
    }

    actual fun realm_config_new(): NativePointer {
        return LongPointerWrapper(realmc.realm_config_new())
    }

    actual fun realm_config_set_path(config: NativePointer, path: String): Boolean {
        return realmc.realm_config_set_path((config as LongPointerWrapper).ptr, path)
    }

    actual fun realm_open(config: NativePointer): NativePointer {
        // Compiler complains without useless cast
        return realmc.realm_open((config as LongPointerWrapper).ptr) as NativePointer
    }

    actual fun realm_close(realm: NativePointer) {
        realmc.realm_open((realm as LongPointerWrapper).ptr)
    }
}
