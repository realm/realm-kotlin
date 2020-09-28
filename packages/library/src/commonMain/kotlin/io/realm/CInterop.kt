package io.realm
import io.realm.runtimeapi.NativeCall
import io.realm.runtimeapi.NativePointer

expect object CInterop : NativeCall {
    override fun openRealm(path: String, schema: String): NativePointer
    override fun realmresultsQuery(pointer: NativePointer, objectType: String, query: String): NativePointer
    override fun addObject(pointer: NativePointer, objectType: String): NativePointer

    override fun beginTransaction(pointer: NativePointer)
    override fun commitTransaction(pointer: NativePointer)
    override fun cancelTransaction(pointer: NativePointer)

    override fun objectGetString(pointer: NativePointer, propertyName: String): String?
    override fun objectSetString(pointer: NativePointer, propertyName: String, value: String?)
    override fun objectGetInt64(pointer: NativePointer, propertyName: String): Long?
    override fun objectSetInt64(pointer: NativePointer, propertyName: String, value: Long)
    override fun queryGetSize(queryPointer: NativePointer) : Long
    override fun queryGetObjectAt(queryPointer: NativePointer, objectType: String, index: Int) : NativePointer



//    fun realmresultsQuery(pointer: BindingPointer, objectType: String, query: String): BindingPointer
//    fun addObject(pointer: BindingPointer, objectType: String): BindingPointer
//
//    fun beginTransaction(pointer: BindingPointer)
//    fun commitTransaction(pointer: BindingPointer)
//    fun cancelTransaction(pointer: BindingPointer)
//
//    fun objectGetString(pointer: BindingPointer, propertyName: String): String?
//    fun objectSetString(pointer: BindingPointer, propertyName: String, value: String?)
//    fun objectGetInt64(pointer: BindingPointer, propertyName: String): Long?
//    fun objectSetInt64(pointer: BindingPointer, propertyName: String, value: Long)
//    fun queryGetSize(queryPointer: BindingPointer) : Long
//    fun queryGetObjectAt(queryPointer: BindingPointer, objectType: String, index: Int) : BindingPointer
}