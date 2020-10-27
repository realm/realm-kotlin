package io.realm.interop

import io.realm.runtimeapi.NativePointer
import kotlinx.cinterop.toKString

actual object RealmInterop {
    actual fun realm_get_library_version(): String {
        return realm_wrapper.realm_get_library_version()!!.toKString()
    }

    actual fun realm_config_new(): NativePointer {
        TODO()
    }

    actual fun realm_config_set_path(config: NativePointer, path: String): Boolean {
        TODO()
    }

    actual fun realm_open(config: NativePointer): NativePointer {
        TODO()
    }

    actual fun realm_close(realm: NativePointer) {
        TODO()
    }
}
