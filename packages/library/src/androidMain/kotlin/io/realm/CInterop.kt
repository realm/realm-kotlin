package io.realm

import java.io.File
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.NativeWrapper

actual object CInterop : NativeWrapper{
    /* load the shared library on application startup. */
    init {
        System.loadLibrary("realm-objectstore-wrapper-android-dynamic")
        val tmpDir = File(RealmInitProvider.applicationContext.filesDir, ".realm.temp")
        tmpDir.mkdirs()
        JNI_initTmpDir(tmpDir.absolutePath)
        NativeWrapper.instance = this
    }

    actual override fun openRealm(path: String, schema: String): NativePointer {
        return BindingPointer(JNI_openRealm(path, schema))
    }

    actual override fun addObject(pointer: NativePointer, objectType: String): NativePointer {
        return BindingPointer(JNI_addObject((pointer as BindingPointer).ptr, objectType))
    }

    actual override fun beginTransaction(pointer: NativePointer) {
        JNI_beginTransaction((pointer as BindingPointer).ptr)
    }

    actual override fun commitTransaction(pointer: NativePointer) {
        JNI_commitTransaction((pointer as BindingPointer).ptr)
    }

    actual override fun cancelTransaction(pointer: NativePointer) {
        JNI_cancelTransaction((pointer as BindingPointer).ptr)
    }

    actual override fun realmresultsQuery(
        pointer: NativePointer,
        objectType: String,
        query: String
    ): NativePointer {
        return BindingPointer(JNI_realmresultsQuery((pointer as BindingPointer).ptr, objectType, query))
    }

    actual override fun objectGetString(pointer: NativePointer, propertyName: String) : String? {
        return JNI_objectGetString((pointer as BindingPointer).ptr, propertyName)
    }

    actual override fun objectSetString(pointer: NativePointer, propertyName: String, value: String?) {
        JNI_objectSetString((pointer as BindingPointer).ptr, propertyName, value!!)//TODO handle nullability
    }

    actual override fun objectGetInt64(pointer: NativePointer, propertyName: String) : Long? {
        return JNI_objectGetInt64((pointer as BindingPointer).ptr, propertyName)
    }

    actual override fun objectSetInt64(pointer: NativePointer, propertyName: String, value: Long) {
        JNI_objectSetInt64((pointer as BindingPointer).ptr, propertyName, value)
    }

    actual override fun queryGetSize(queryPointer: NativePointer): Long {
        return JNI_queryGetSize((queryPointer as BindingPointer).ptr)
    }

    actual override fun queryGetObjectAt(queryPointer: NativePointer, objectType: String, index: Int): NativePointer {
        return BindingPointer(JNI_queryGetObjectAt((queryPointer as BindingPointer).ptr, objectType, index))
    }


    // JNI functions implemented in the shared C++ library
    // This file is used to generate the header file as well

    external fun JNI_initTmpDir(tmpDir: String)
    external fun JNI_openRealm(path: String, schema: String): Long
    external fun JNI_addObject(pointer: Long, objectType: String): Long
    external fun JNI_beginTransaction(pointer: Long)
    external fun JNI_commitTransaction(pointer: Long)
    external fun JNI_cancelTransaction(pointer: Long)
    external fun JNI_realmresultsQuery(pointer: Long, objectType: String, query: String): Long

    external fun JNI_objectGetString(pointer: Long, propertyName: String) : String //TODO how to handle nullability (should we throw exception instead)
    external fun JNI_objectSetString(pointer: Long, propertyName: String, value: String)
    external fun JNI_objectGetInt64(pointer: Long, propertyName: String) : Long
    external fun JNI_objectSetInt64(pointer: Long, propertyName: String, value: Long)
    external fun JNI_queryGetSize(queryPointer: Long): Long
    external fun JNI_queryGetObjectAt(queryPointer: Long, objectType: String, index: Int): Long

}