package io.realm

import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.toKString
import objectstore_wrapper.*

actual object CInterop {
    actual fun openRealm(path: String, schema: String): BindingPointer {
        return CPointerWrapper(create(path, schema)!!)//TODO !!
    }

    actual fun beginTransaction(pointer: BindingPointer) {
        begin_transaction(pointer.ptr as CValuesRef<database_t>)

    }

    actual fun commitTransaction(pointer: BindingPointer) {
        commit_transaction(pointer.ptr as CValuesRef<database_t>)
    }

    actual fun cancelTransaction(pointer: BindingPointer) {
        cancel_transaction(pointer.ptr as CValuesRef<database_t>)
    }

    actual fun realmresultsQuery(pointer: BindingPointer, objectType: String, query: String): BindingPointer {
        return CPointerWrapper(query(pointer.ptr as CValuesRef<database_t>, objectType, query)!!) //TODO !!
    }

    actual fun addObject(pointer: BindingPointer, objectType: String): BindingPointer {
        return CPointerWrapper(add_object(pointer.ptr as CValuesRef<database_t>, objectType)!!) //TODO !!
    }

    actual fun objectGetString(pointer: BindingPointer, propertyName: String) : String? {
        return object_get_string(pointer.ptr as CValuesRef<realm_object_t>, propertyName)?.toKString()
    }

    actual fun objectSetString(pointer: BindingPointer, propertyName: String, value: String?) {
        object_set_string(pointer.ptr as CValuesRef<realm_object_t>, propertyName, value)
    }

    actual fun objectGetInt64(pointer: BindingPointer, propertyName: String) : Long? {
        return object_get_int64(pointer.ptr as CValuesRef<realm_object_t>, propertyName)
    }

    actual fun objectSetInt64(pointer: BindingPointer, propertyName: String, value: Long) {
        object_set_int64(pointer.ptr as CValuesRef<realm_object_t>, propertyName, value)
    }

    @ExperimentalUnsignedTypes
    actual fun queryGetSize(queryPointer: BindingPointer): Long {
        return realmresults_size(queryPointer.ptr as CValuesRef<realm_results_t>).toLong()
    }

    @ExperimentalUnsignedTypes
    actual fun queryGetObjectAt(queryPointer: BindingPointer, objectType: String, index: Int): BindingPointer {
        return CPointerWrapper(
            realmresults_get(
                queryPointer.ptr as CValuesRef<realm_results_t>,
                objectType,
                index.toULong()
            )!!
        )
    }
}