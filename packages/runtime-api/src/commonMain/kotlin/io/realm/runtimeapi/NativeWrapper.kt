package io.realm.runtimeapi

interface NativePointer

interface NativeWrapper {
    companion object {
        var instance: NativeWrapper = object : NativeWrapper {
            override fun openRealm(path: String, schema: String): NativePointer {
                TODO("Not yet implemented")
            }

            override fun realmresultsQuery(pointer: NativePointer, objectType: String, query: String): NativePointer {
                TODO("Not yet implemented")
            }

            override fun addObject(pointer: NativePointer, objectType: String): NativePointer {
                TODO("Not yet implemented")
            }

            override fun beginTransaction(pointer: NativePointer) {
                TODO("Not yet implemented")
            }

            override fun commitTransaction(pointer: NativePointer) {
                TODO("Not yet implemented")
            }

            override fun cancelTransaction(pointer: NativePointer) {
                TODO("Not yet implemented")
            }

            override fun objectGetString(pointer: NativePointer, propertyName: String): String? {
                TODO("Not yet implemented")
            }

            override fun objectSetString(pointer: NativePointer, propertyName: String, value: String?) {
                TODO("Not yet implemented")
            }

            override fun objectGetInt64(pointer: NativePointer, propertyName: String): Long? {
                TODO("Not yet implemented")
            }

            override fun objectSetInt64(pointer: NativePointer, propertyName: String, value: Long) {
                TODO("Not yet implemented")
            }

            override fun queryGetSize(queryPointer: NativePointer): Long {
                TODO("Not yet implemented")
            }

            override fun queryGetObjectAt(queryPointer: NativePointer, objectType: String, index: Int): NativePointer {
                TODO("Not yet implemented")
            }
        }
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
