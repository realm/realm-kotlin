package io.realm

actual object CInterop {
    init {
        TODO()
    }

    actual fun openRealm(path: String, schema: String): BindingPointer {
        TODO()
    }

    actual fun addObject(pointer: BindingPointer, objectType: String): BindingPointer {
        TODO()
    }

    actual fun beginTransaction(pointer: BindingPointer) {
        TODO()
    }

    actual fun commitTransaction(pointer: BindingPointer) {
        TODO()
    }

    actual fun cancelTransaction(pointer: BindingPointer) {
        TODO()
    }

    actual fun realmresultsQuery(
        pointer: BindingPointer,
        objectType: String,
        query: String
    ): BindingPointer {
        TODO()
    }

    actual fun objectGetString(pointer: BindingPointer, propertyName: String) : String? {
        TODO()
    }

    actual fun objectSetString(pointer: BindingPointer, propertyName: String, value: String?) {
        TODO()
    }

    actual fun objectGetInt64(pointer: BindingPointer, propertyName: String) : Long? {
        TODO()
    }

    actual fun objectSetInt64(pointer: BindingPointer, propertyName: String, value: Long) {
        TODO()
    }

    actual fun queryGetSize(queryPointer: BindingPointer): Long {
        TODO()
    }

    actual fun queryGetObjectAt(queryPointer: BindingPointer, objectType: String, index: Int): BindingPointer {
        TODO()
    }

}
