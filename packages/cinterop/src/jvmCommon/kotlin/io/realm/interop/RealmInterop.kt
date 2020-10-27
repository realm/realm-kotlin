package io.realm.interop

import io.realm.interop.gen.realmc
import io.realm.runtimeapi.NativePointer

actual object RealmInterop {
    // TODO Maybe pull out into separate method
    init {
        System.loadLibrary("realmc")
    }

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
        return realmc.realm_open((config as LongPointerWrapper).ptr)
    }

    actual fun realm_close(realm: NativePointer) {
        realmc.realm_open((realm as LongPointerWrapper).ptr)
    }
}
