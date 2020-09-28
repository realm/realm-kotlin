package io.realm

expect object CInterop {
    fun openRealm(path: String, schema: String): BindingPointer
    fun realmresultsQuery(pointer: BindingPointer, objectType: String, query: String): BindingPointer
    fun addObject(pointer: BindingPointer, objectType: String): BindingPointer

    fun beginTransaction(pointer: BindingPointer)
    fun commitTransaction(pointer: BindingPointer)
    fun cancelTransaction(pointer: BindingPointer)

    fun objectGetString(pointer: BindingPointer, propertyName: String): String?
    fun objectSetString(pointer: BindingPointer, propertyName: String, value: String?)
    fun objectGetInt64(pointer: BindingPointer, propertyName: String): Long?
    fun objectSetInt64(pointer: BindingPointer, propertyName: String, value: Long)
    fun queryGetSize(queryPointer: BindingPointer) : Long
    fun queryGetObjectAt(queryPointer: BindingPointer, objectType: String, index: Int) : BindingPointer
}