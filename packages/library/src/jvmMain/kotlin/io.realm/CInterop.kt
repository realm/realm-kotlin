package io.realm

import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.NativeWrapper

internal actual object CInterop : NativeWrapper {
    init {
        TODO()
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
