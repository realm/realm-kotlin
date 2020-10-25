package io.realm.interop

import io.realm.runtimeapi.NativePointer
import kotlinx.cinterop.*
import realm_wrapper.*

class CPointerWrapper(val ptr : CPointer<*>) : NativePointer//TODO maybe use <out CPointed> instead of *

actual object RealmInterop {
    actual fun realm_get_library_version(): String {
        return realm_wrapper.realm_get_library_version()!!.toKString()
    }

    actual fun realm_config_new(): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_config_new()!!)
    }

    actual fun realm_schema_new(classes: List<Class>): NativePointer {
        TODO("Not yet implemented")
    }

    actual fun realm_config_set_path(config: NativePointer, path: String) {
        TODO("Not yet implemented")
    }

    actual fun realm_config_set_schema_mode(config: NativePointer, mode: SchemaMode) {
        TODO("Not yet implemented")
    }

    actual fun realm_config_set_schema_version(config: NativePointer, version: Long) {
        TODO("Not yet implemented")
    }

    actual fun realm_config_set_schema(config: NativePointer, schema: List<Class>) {
        TODO("Not yet implemented")
    }

    actual fun realm_schema_validate(Schema: NativePointer) {
        TODO("Not yet implemented")
    }

    actual fun realm_open(config: NativePointer): NativePointer {
        TODO()
    }

    actual fun realm_close(realm: NativePointer) {
        TODO()
    }

    actual fun realm_config_set_schema(config: NativePointer, schema: NativePointer) {
    }

    actual fun realm_schema_validate(schema: NativePointer): Boolean {
        TODO("Not yet implemented")
    }

    actual fun realm_get_schema(realm: NativePointer): NativePointer {
        TODO("Not yet implemented")
    }

    actual fun realm_get_num_classes(realm: NativePointer): Long {
        TODO("Not yet implemented")
    }

    actual fun realm_release(o: NativePointer) {
        TODO()
    }

    actual fun realm_begin_write(realm: NativePointer) {
        TODO()
    }

    actual fun realm_commit(realm: NativePointer) {
        TODO()
    }

    actual fun realm_object_create(realm: NativePointer, key: Long): NativePointer {
        TODO("Not yet implemented")
    }

    actual fun realm_find_class(realm: NativePointer, name: String): Long {
        TODO("Not yet implemented")
    }

    actual fun <T> realm_set_value(o: NativePointer, key: Long, value: T) {
    }

    actual inline fun <T> realm_get_value(o: NativePointer, key: Long): T {
        TODO("Not yet implemented")
    }

    actual fun <T> realm_set_value(realm: NativePointer, o: NativePointer, table: String, col: String, value: T, isDefault: Boolean) {
    }

    actual fun <T> realm_get_value(realm: NativePointer, o: NativePointer, table: String, col: String, type: PropertyType): T {
        TODO("Not yet implemented")
    }

}
