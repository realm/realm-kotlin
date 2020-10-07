package io.realm.interop

import io.realm.runtimeapi.NativePointer

expect class RealmInterop {
    // Config unwrapper pointer
    fun realm_config_new(): Long
    fun realm_config_set_path(arg0: Long, arg1: String?): kotlin.Boolean

    // Realm with wrapped pointer
    fun realm_open(config: Long): NativePointer?
    fun realm_close(arg0: NativePointer): kotlin.Boolean
}
