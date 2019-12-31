package realm

expect object CInterop {
    fun objectGetString(pointer: BindingPointer, propertyName: String): String?
    fun objectSetString(pointer: BindingPointer, propertyName: String, value: String?)
    fun objectGetInt64(pointer: BindingPointer, propertyName: String): Long?
    fun objectSetInt64(pointer: BindingPointer, propertyName: String, value: Long)
}