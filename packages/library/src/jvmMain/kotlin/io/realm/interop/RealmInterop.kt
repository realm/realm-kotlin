package io.realm.interop

@Suppress("FunctionNaming", "LongParameterList")
actual class RealmInterop {

    actual fun realm_config_new(): Long {
        return realmc.realm_config_new()
    }

    actual fun realm_config_set_path(arg0: Long, arg1: String?): Boolean {
        return realmc.realm_config_set_path(arg0, arg1)
    }

    actual fun realm_open(config: Long): NativePointer? {
        return realmc.realm_open(config)
    }

    actual fun realm_close(arg0: NativePointer): Boolean {
        TODO("Not yet implemented")
    }
}
