package realm

actual object CInterop {
    actual fun objectGetString(pointer: BindingPointer, propertyName: String) : String? {
        // TODO use JNI to lookup the property 'propertyName' from the Obj pointed to by pointer
        return ""
    }

    actual fun objectSetString(pointer: BindingPointer, propertyName: String, value: String?) {
    }

    actual fun objectGetInt64(pointer: BindingPointer, propertyName: String) : Long? {
        return 0
    }

    actual fun objectSetInt64(pointer: BindingPointer, propertyName: String, value: Long) {
    }
}