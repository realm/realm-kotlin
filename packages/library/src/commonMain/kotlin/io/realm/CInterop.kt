package io.realm

import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.NativeWrapper

expect object CInterop : NativeWrapper {
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
    override fun queryGetSize(queryPointer: NativePointer): Long
    override fun queryGetObjectAt(queryPointer: NativePointer, objectType: String, index: Int): NativePointer
}