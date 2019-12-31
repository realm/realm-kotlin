package realm

import kotlinx.cinterop.toKString
import objectstore_wrapper.object_get_int64
import objectstore_wrapper.object_get_string
import objectstore_wrapper.object_set_int64
import objectstore_wrapper.object_set_string

actual object CInterop {
    actual fun objectGetString(pointer: BindingPointer, propertyName: String) : String? {
        return object_get_string(pointer.ptr, propertyName)?.toKString()
    }

    actual fun objectSetString(pointer: BindingPointer, propertyName: String, value: String?) {
        object_set_string(pointer.ptr, propertyName, value)
    }

    actual fun objectGetInt64(pointer: BindingPointer, propertyName: String) : Long? {
        return object_get_int64(pointer.ptr, propertyName)
    }

    actual fun objectSetInt64(pointer: BindingPointer, propertyName: String, value: Long) {
        object_set_int64(pointer.ptr, propertyName, value)
    }
}