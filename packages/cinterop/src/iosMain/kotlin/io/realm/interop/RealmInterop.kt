package io.realm.interop

import io.realm.runtimeapi.NativePointer
import kotlinx.cinterop.toKString
import realm_wrapper.*

actual object RealmInterop {
    actual fun realm_get_library_version(): String {
        return realm_wrapper.realm_get_library_version()!!.toKString()
    }

    actual fun realm_config_new(): io.realm.interop.NativePointer {
        TODO()
    }

    actual fun realm_config_set_path(config: io.realm.interop.NativePointer, path: String): Boolean {
        TODO()
    }

    actual fun realm_open(config: io.realm.interop.NativePointer): io.realm.interop.NativePointer {
        TODO()
    }

    actual fun realm_close(realm: io.realm.interop.NativePointer) {
        TODO()
    }
}
