package io.realm

import io.realm.runtimeapi.NativePointer
import realm_value_type_e
import realmcJNI.*
import realmc.*
import io.realm.runtimeapi.NativeWrapper

actual object CInterop : NativeWrapper {
    init {
        // With pointers (directly JNI invocation)
        val pointer = realmcJNI.realm_config_new()
        realmcJNI.realm_config_set_path(pointer, "PATH")
        realmcJNI.realm_open(pointer)

        // With SWIG wrappers
        val realmConfigNew = realmc.realm_config_new()
        realmc.realm_config_set_path(realmConfigNew, "PATH")
        realmc.realm_open(realmConfigNew)


        realmcJNI.custom("Claus")


        // Enum example
        realm_value_type_e.RLM_TYPE_BINARY
        val swigToEnum: realm_value_type_e = realm_value_type_e.swigToEnum(5)

    }

    actual override fun openRealm(path: String, schema: String): NativePointer {
        TODO("Not yet implemented")
    }

    actual override fun realmresultsQuery(pointer: NativePointer, objectType: String, query: String): NativePointer {
        TODO("Not yet implemented")
    }

    actual override fun addObject(pointer: NativePointer, objectType: String): NativePointer {
        TODO("Not yet implemented")
    }

    actual override fun beginTransaction(pointer: NativePointer) {
    }

    actual override fun commitTransaction(pointer: NativePointer) {
    }

    actual override fun cancelTransaction(pointer: NativePointer) {
    }

    actual override fun objectGetString(pointer: NativePointer, propertyName: String): String? {
        TODO("Not yet implemented")
    }

    actual override fun objectSetString(pointer: NativePointer, propertyName: String, value: String?) {
    }

    actual override fun objectGetInt64(pointer: NativePointer, propertyName: String): Long? {
        TODO("Not yet implemented")
    }

    actual override fun objectSetInt64(pointer: NativePointer, propertyName: String, value: Long) {
    }

    actual override fun queryGetSize(queryPointer: NativePointer): Long {
        TODO("Not yet implemented")
    }

    actual override fun queryGetObjectAt(queryPointer: NativePointer, objectType: String, index: Int): NativePointer {
        TODO("Not yet implemented")
    }
}
