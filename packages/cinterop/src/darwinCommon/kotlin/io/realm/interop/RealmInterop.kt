package io.realm.interop

import io.realm.runtimeapi.NativePointer
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.getBytes
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.readValue
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import realm_wrapper.realm_class_info_t
import realm_wrapper.realm_config_t
import realm_wrapper.realm_error_t
import realm_wrapper.realm_find_property
import realm_wrapper.realm_get_last_error
import realm_wrapper.realm_property_info_t
import realm_wrapper.realm_schema_mode
import realm_wrapper.realm_string_t
import realm_wrapper.realm_table_key_t
import realm_wrapper.realm_value_t
import realm_wrapper.realm_value_type

private fun throwOnError() {
    memScoped {
        val error = alloc<realm_error_t>()
        if (realm_get_last_error(error.ptr)) {
            // FIXME Extract all error information and throw exceptions based on type
            throw RuntimeException(error.message.toKString())
        }
    }
}

private fun throwOnError(boolean: Boolean): Boolean {
    if (!boolean) throwOnError(); return boolean
}

private fun throwOnError(pointer: CPointer<out CPointed>?): CPointer<out CPointed>? {
    if (pointer == null) throwOnError(); return pointer
}

// FIXME Consider making NativePointer/CPointerWrapper generic to enforce typing
class CPointerWrapper(ptr: CPointer<out CPointed>?) : NativePointer {
    // FIXME Generic check for errors on null pointers returned from the C API. We probably have to
    //  do this more selectively, but for now just check all pointers.
    val ptr: CPointer<out CPointed>? = throwOnError(ptr)
}

// Convenience type cast
private inline fun <T : CPointed> NativePointer.cptr(): CPointer<T> {
    return (this as CPointerWrapper).ptr as CPointer<T>
}

// FIXME Do we need to handle data == null as String?
fun realm_string_t.toKString(): String {
    if (size == 0UL) {
        return ""
    }
    val data: CPointer<ByteVarOf<Byte>>? = this.data
    val readBytes: ByteArray? = data?.readBytes(this.size.toInt())
    return readBytes?.toKString()!!
}

fun realm_string_t.set(memScope: MemScope, s: String): realm_string_t {
    val cstr = s.cstr
    // FIXME Review/guard
    size = cstr.getBytes().size.toULong() - 1UL
    data = cstr.getPointer(memScope)
    return this
}

fun String.toRString(memScope: MemScope) = cValue<realm_string_t> {
    set(memScope, this@toRString)
}

actual object RealmInterop {

    actual fun realm_get_library_version(): String {
        return realm_wrapper.realm_get_library_version()!!.toKString()
    }

    actual fun realm_schema_new(tables: List<Table>): NativePointer {
        val count = tables.size
        memScoped {
            val cclasses = allocArray<realm_class_info_t>(count)
            val cproperties = allocArray<CPointerVar<realm_property_info_t>>(count)
            for ((i, clazz) in tables.withIndex()) {
                val properties = clazz.properties
                // Class
                cclasses[i].apply {
                    name.set(memScope, clazz.name)
                    primary_key.set(memScope, clazz.primaryKey)
                    num_properties = properties.size.toULong()
                    num_computed_properties = 0U
                    flags = clazz.flags.fold(0) { flags, element -> flags or element.nativeValue.toInt() }
                }
                cproperties[i] = allocArray<realm_property_info_t>(properties.size).getPointer(memScope)
                for ((j, property) in properties.withIndex()) {
                    cproperties[i]!![j].apply {
                        name.set(memScope, property.name)
                        type = property.type.nativeValue
                        collection_type = property.collectionType.nativeValue
                        flags = property.flags.fold(0) { flags, element -> flags or element.nativeValue.toInt() }
                    }
                }
            }
            return CPointerWrapper(realm_wrapper.realm_schema_new(cclasses, count.toULong(), cproperties))
        }
    }

    actual fun realm_config_new(): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_config_new())
    }

    actual fun realm_config_set_path(config: NativePointer, path: String) {
        memScoped {
            throwOnError(realm_wrapper.realm_config_set_path(config.cptr(), path.toRString(memScope)))
        }
    }

    actual fun realm_config_set_schema_mode(config: NativePointer, mode: SchemaMode) {
        throwOnError(realm_wrapper.realm_config_set_schema_mode(config.cptr(), realm_schema_mode.RLM_SCHEMA_MODE_ADDITIVE))
    }

    actual fun realm_config_set_schema_version(config: NativePointer, version: Long) {
        throwOnError(realm_wrapper.realm_config_set_schema_version(config.cptr(), version.toULong()))
    }

    actual fun realm_config_set_schema(config: NativePointer, schema: NativePointer) {
        throwOnError(realm_wrapper.realm_config_set_schema(config.cptr(), schema.cptr()))
    }

    actual fun realm_schema_validate(schema: NativePointer): Boolean {
        return throwOnError(realm_wrapper.realm_schema_validate(schema.cptr()))
    }

    actual fun realm_open(config: NativePointer): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_open(config.cptr<realm_config_t>()))
    }

    actual fun realm_close(realm: NativePointer) {
        throwOnError(realm_wrapper.realm_close(realm.cptr()))
    }

    actual fun realm_get_schema(realm: NativePointer): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_get_schema(realm.cptr()))
    }

    actual fun realm_get_num_classes(realm: NativePointer): Long {
        return realm_wrapper.realm_get_num_classes(realm.cptr()).toLong()
    }

    actual fun realm_release(o: NativePointer) {
        // FIXME Can this one not throw
        realm_wrapper.realm_release((o as CPointerWrapper).ptr)
    }

    actual fun realm_begin_write(realm: NativePointer) {
        throwOnError(realm_wrapper.realm_begin_write(realm.cptr()))
    }

    actual fun realm_commit(realm: NativePointer) {
        throwOnError(realm_wrapper.realm_commit(realm.cptr()))
    }

    actual fun realm_find_class(realm: NativePointer, name: String): Long {
        memScoped {
            val found = alloc<BooleanVar>()
            val classInfo = alloc<realm_class_info_t>()
            throwOnError(realm_wrapper.realm_find_class(realm.cptr(), name.toRString(memScope), found.ptr, classInfo.ptr))
            if (!found.value) {
                throw RuntimeException("Class \"$name\" not found")
            }
            return classInfo.key.table_key.toLong()
        }
    }

    actual fun realm_object_create(realm: NativePointer, key: Long): NativePointer {
        val tableKey = cValue<realm_table_key_t> { table_key = key.toUInt() }
        return CPointerWrapper(realm_wrapper.realm_object_create(realm.cptr(), tableKey))
    }

    actual fun <T> realm_set_value(realm: NativePointer, o: NativePointer, table: String, col: String, value: T, isDefault: Boolean) {
        TODO()
        // Cannot pass realm_value_t by value to cinterop layer so added specialization in realm.def
        // Calling
        //     realm_wrapper.realm_set_value(o.cptr(), propertyInfo.key.readValue(), x.readValue(), false)
        // Will fail to compile with
        // e: .../realm/interop/RealmInterop.kt: (219, 85): type kotlinx.cinterop.CValue<realm_wrapper.realm_value{ realm_wrapper.realm_value_t }>  is not supported here: not a structure or too complex
    }

    actual fun <T> realm_get_value(realm: NativePointer, o: NativePointer, table: String, col: String, type: PropertyType): T {
        TODO("Not yet implemented")
    }

    actual fun objectGetString(realm: NativePointer, o: NativePointer, table: String, col: String): String {
        memScoped {
            val propertyInfo = propertyInfo(realm, classInfo(realm, table), col)
            val value = alloc<realm_value_t>()
            realm_wrapper.realm_get_value(o.cptr(), propertyInfo.key.readValue(), value.ptr)
            when (value.type) {
                realm_value_type.RLM_TYPE_STRING ->
                    return value.string.toKString()
                else ->
                    TODO("Only string is supported")
            }
        }
    }

    actual fun objectSetString(realm: NativePointer, o: NativePointer, table: String, col: String, value: String): String? {
        memScoped {
            val propertyInfo = propertyInfo(realm, classInfo(realm, table), col)
            realm_wrapper.realm_set_value_string(o.cptr(), propertyInfo.key.readValue(), value.toRString(memScope), false)
        }
        // FIXME Why a return value
        return "But, why?"
    }

    private fun MemScope.classInfo(realm: NativePointer, table: String): realm_class_info_t {
        val found = alloc<BooleanVar>()
        val classInfo = alloc<realm_class_info_t>()
        throwOnError(realm_wrapper.realm_find_class(realm.cptr(), table.toRString(memScope), found.ptr, classInfo.ptr))
        return classInfo
    }

    private fun MemScope.propertyInfo(realm: NativePointer, classInfo: realm_class_info_t, col: String): realm_property_info_t {
        val found = alloc<BooleanVar>()
        val propertyInfo = alloc<realm_property_info_t>()
        throwOnError(realm_find_property(realm.cptr(), classInfo.key.readValue(), col.toRString(memScope), found.ptr, propertyInfo.ptr))
        return propertyInfo
    }
}
