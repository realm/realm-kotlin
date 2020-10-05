package io.realm.runtimeapi

interface NativePointer

interface NativeWrapper {
    companion object {
        var instance: NativeWrapper? = null
    }

    fun openRealm(path: String, schema: String): NativePointer
    fun realmresultsQuery(pointer: NativePointer, objectType: String, query: String): NativePointer
    fun addObject(pointer: NativePointer, objectType: String): NativePointer

    fun beginTransaction(pointer: NativePointer)
    fun commitTransaction(pointer: NativePointer)
    fun cancelTransaction(pointer: NativePointer)

    fun objectGetString(pointer: NativePointer, propertyName: String): String?
    fun objectSetString(pointer: NativePointer, propertyName: String, value: String?)
    fun objectGetInt64(pointer: NativePointer, propertyName: String): Long?
    fun objectSetInt64(pointer: NativePointer, propertyName: String, value: Long)
    fun queryGetSize(queryPointer: NativePointer): Long
    fun queryGetObjectAt(queryPointer: NativePointer, objectType: String, index: Int): NativePointer

}
