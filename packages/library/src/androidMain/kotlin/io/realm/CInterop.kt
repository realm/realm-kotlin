package io.realm

import java.io.File

actual object CInterop {
    /* load the shared library on application startup. */
    init {
        System.loadLibrary("realm-objectstore-wrapper-android-dynamic")
        val tmpDir = File(RealmInitProvider.applicationContext.filesDir, ".realm.temp")
        tmpDir.mkdirs()
        JNI_initTmpDir(tmpDir.absolutePath)
    }

    actual fun openRealm(path: String, schema: String): BindingPointer {
        return JNI_openRealm(path, schema)
    }

    actual fun addObject(pointer: BindingPointer, objectType: String): BindingPointer {
        return JNI_addObject(pointer, objectType)
    }

    actual fun beginTransaction(pointer: BindingPointer) {
        JNI_beginTransaction(pointer)
    }

    actual fun commitTransaction(pointer: BindingPointer) {
        JNI_commitTransaction(pointer)
    }

    actual fun cancelTransaction(pointer: BindingPointer) {
        JNI_cancelTransaction(pointer)
    }

    actual fun realmresultsQuery(
        pointer: BindingPointer,
        objectType: String,
        query: String
    ): BindingPointer {
        return JNI_realmresultsQuery(pointer, objectType, query)
    }

    actual fun objectGetString(pointer: BindingPointer, propertyName: String) : String? {
        return JNI_objectGetString(pointer, propertyName)
    }

    actual fun objectSetString(pointer: BindingPointer, propertyName: String, value: String?) {
        JNI_objectSetString(pointer, propertyName, value!!)//TODO handle nullability
    }

    actual fun objectGetInt64(pointer: BindingPointer, propertyName: String) : Long? {
        return JNI_objectGetInt64(pointer, propertyName)
    }

    actual fun objectSetInt64(pointer: BindingPointer, propertyName: String, value: Long) {
        JNI_objectSetInt64(pointer, propertyName, value)
    }

    actual fun queryGetSize(queryPointer: BindingPointer): Long {
        return JNI_queryGetSize(queryPointer)
    }

    actual fun queryGetObjectAt(queryPointer: BindingPointer, objectType: String, index: Int): BindingPointer {
        return JNI_queryGetObjectAt(queryPointer, objectType, index)
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