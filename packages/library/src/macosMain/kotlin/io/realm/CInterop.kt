package io.realm

import io.realm.runtimeapi.NativeWrapper
import io.realm.runtimeapi.NativePointer
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.toKString
import objectstore_wrapper.*

actual object CInterop : NativeWrapper {
    init {
        NativeWrapper.instance = this
    }
    actual override fun openRealm(path: String, schema: String): NativePointer {
        return CPointerWrapper(create(path, schema)!!)//TODO !!
    }

    actual override fun beginTransaction(pointer: NativePointer) {
        begin_transaction((pointer as BindingPointer).ptr as CValuesRef<database_t>)
    }

    actual override fun commitTransaction(pointer: NativePointer) {
        commit_transaction((pointer as BindingPointer).ptr as CValuesRef<database_t>)
    }

    actual override fun cancelTransaction(pointer: NativePointer) {
        cancel_transaction((pointer as BindingPointer).ptr as CValuesRef<database_t>)
    }

    actual override fun realmresultsQuery(pointer: NativePointer, objectType: String, query: String): NativePointer {
        return CPointerWrapper(query((pointer as BindingPointer).ptr as CValuesRef<database_t>, objectType, query)!!) //TODO !!
    }

    actual override fun addObject(pointer: NativePointer, objectType: String): NativePointer {
        return CPointerWrapper(add_object((pointer as BindingPointer).ptr as CValuesRef<database_t>, objectType)!!) //TODO !!
    }

    actual override fun objectGetString(pointer: NativePointer, propertyName: String) : String? {
        return object_get_string((pointer as BindingPointer).ptr as CValuesRef<realm_object_t>, propertyName)?.toKString()
    }

    actual override fun objectSetString(pointer: NativePointer, propertyName: String, value: String?) {
        object_set_string((pointer as BindingPointer).ptr as CValuesRef<realm_object_t>, propertyName, value)
    }

    actual override fun objectGetInt64(pointer: NativePointer, propertyName: String) : Long? {
        return object_get_int64((pointer as BindingPointer).ptr as CValuesRef<realm_object_t>, propertyName)
    }

    actual override fun objectSetInt64(pointer: NativePointer, propertyName: String, value: Long) {
        object_set_int64((pointer as BindingPointer).ptr as CValuesRef<realm_object_t>, propertyName, value)
    }

    @ExperimentalUnsignedTypes
    actual override fun queryGetSize(queryPointer: NativePointer): Long {
        return realmresults_size((queryPointer as BindingPointer).ptr as CValuesRef<realm_results_t>).toLong()
    }

    @ExperimentalUnsignedTypes
    actual override fun queryGetObjectAt(queryPointer: NativePointer, objectType: String, index: Int): NativePointer {
        return CPointerWrapper(
            realmresults_get(
                    (queryPointer as BindingPointer).ptr as CValuesRef<realm_results_t>,
                objectType,
                index.toULong()
            )!!
        )
    }
}
