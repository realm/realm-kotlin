/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.interop

import io.realm.runtimeapi.Link
import io.realm.runtimeapi.NativePointer
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.getBytes
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.readValue
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import realm_wrapper.realm_class_info_t
import realm_wrapper.realm_clear_last_error
import realm_wrapper.realm_col_key
import realm_wrapper.realm_config_t
import realm_wrapper.realm_error_t
import realm_wrapper.realm_find_property
import realm_wrapper.realm_get_last_error
import realm_wrapper.realm_obj_key
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
            val runtimeException = RuntimeException(error.message.toKString())
            realm_clear_last_error()
            // FIXME Extract all error information and throw exceptions based on type
            //  https://github.com/realm/realm-kotlin/issues/70
            throw runtimeException
        }
    }
}

private fun throwOnError(boolean: Boolean): Boolean {
    if (!boolean) throwOnError(); return boolean
}

private fun throwOnError(pointer: CPointer<out CPointed>?): CPointer<out CPointed>? {
    if (pointer == null) throwOnError(); return pointer
}

// FIXME API-INTERNAL Consider making NativePointer/CPointerWrapper generic to enforce typing
class CPointerWrapper(ptr: CPointer<out CPointed>?) : NativePointer {
    val ptr: CPointer<out CPointed>? = throwOnError(ptr)
}

// Convenience type cast
private inline fun <T : CPointed> NativePointer.cptr(): CPointer<T> {
    return (this as CPointerWrapper).ptr as CPointer<T>
}

fun realm_string_t.set(memScope: MemScope, s: String): realm_string_t {
    if (s.isEmpty()) {
        data = null
        size = 0UL
    } else {
        val cstr = s.cstr
        data = cstr.getPointer(memScope)
        size = cstr.getBytes().size.toULong() - 1UL // realm_string_t is not zero-terminated
    }
    return this
}

fun realm_value_t.set(memScope: MemScope, value: Any): realm_value_t {
    when (value) {
        is String -> {
            type = realm_value_type.RLM_TYPE_STRING
            string.set(memScope, value)
        }
        is Byte, is Short, is Int, is Long -> {
            type = realm_value_type.RLM_TYPE_INT
            integer = (value as Number).toLong()
        }
        is Char -> {
            type = realm_value_type.RLM_TYPE_INT
            integer = value.toLong()
        }
        is Float -> {
            type = realm_value_type.RLM_TYPE_FLOAT
            fnum = value
        }
        is Double -> {
            type = realm_value_type.RLM_TYPE_DOUBLE
            dnum = value
        }
        else ->
            TODO("Value conversion not yet implemented for : ${value::class.simpleName}")
    }
    return this
}

fun realm_string_t.toKString(): String {
    if (size == 0UL) {
        return ""
    }
    val data: CPointer<ByteVarOf<Byte>>? = this.data
    val readBytes: ByteArray? = data?.readBytes(this.size.toInt())
    return readBytes?.toKString()!!
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
                    flags =
                        clazz.flags.fold(0) { flags, element -> flags or element.nativeValue.toInt() }
                }
                cproperties[i] =
                    allocArray<realm_property_info_t>(properties.size).getPointer(memScope)
                for ((j, property) in properties.withIndex()) {
                    cproperties[i]!![j].apply {
                        name.set(memScope, property.name)
                        type = property.type.nativeValue
                        collection_type = property.collectionType.nativeValue
                        flags =
                            property.flags.fold(0) { flags, element -> flags or element.nativeValue.toInt() }
                    }
                }
            }
            return CPointerWrapper(
                realm_wrapper.realm_schema_new(
                    cclasses,
                    count.toULong(),
                    cproperties
                )
            )
        }
    }

    actual fun realm_config_new(): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_config_new())
    }

    actual fun realm_config_set_path(config: NativePointer, path: String) {
        memScoped {
            throwOnError(
                realm_wrapper.realm_config_set_path(
                    config.cptr(),
                    path.toRString(memScope)
                )
            )
        }
    }

    actual fun realm_config_set_schema_mode(config: NativePointer, mode: SchemaMode) {
        throwOnError(
            realm_wrapper.realm_config_set_schema_mode(
                config.cptr(),
                realm_schema_mode.RLM_SCHEMA_MODE_ADDITIVE
            )
        )
    }

    actual fun realm_config_set_schema_version(config: NativePointer, version: Long) {
        throwOnError(
            realm_wrapper.realm_config_set_schema_version(
                config.cptr(),
                version.toULong()
            )
        )
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
            throwOnError(
                realm_wrapper.realm_find_class(
                    realm.cptr(),
                    name.toRString(memScope),
                    found.ptr,
                    classInfo.ptr
                )
            )
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

    // FIXME API-INTERNAL How should we support the various types. Through generic dispatching
    //  getter/setter, or through specialized methods.
    //  https://github.com/realm/realm-kotlin/issues/69
    actual fun <T> realm_set_value(
        realm: NativePointer?,
        obj: NativePointer?,
        table: String,
        col: String,
        value: T,
        isDefault: Boolean
    ) {
        TODO("Unsupported until https://youtrack.jetbrains.com/issue/KT-43833 is fixed")
        // Anonymous union are not supported in Kotlin/Native https://youtrack.jetbrains.com/issue/KT-43833
        // which will call to `realm_wrapper.realm_set_value` using a `cValue<realm_value>` throw a
        // type kotlinx.cinterop.CValue<realm_wrapper.realm_value{ realm_wrapper.realm_value_t }>  is not supported here: not a structure or too complex
    }

    // FIXME API-INTERNAL How should we support the various types. Through generic dispatching
    //  getter/setter, or through specialized methods.
    //  https://github.com/realm/realm-kotlin/issues/69
    actual fun <T> realm_get_value(
        realm: NativePointer?,
        obj: NativePointer?,
        table: String,
        col: String,
        type: PropertyType
    ): T {
        TODO("Not yet implemented") // https://github.com/realm/realm-kotlin/issues/69
    }

    // Invoked from compiler plugin generated code
    actual fun objectGetString(
        realm: NativePointer?,
        obj: NativePointer?,
        table: String,
        col: String
    ): String {
        if (realm == null || obj == null) {
            throw IllegalStateException("Invalid/deleted object")
        }
        memScoped {
            val propertyInfo = propertyInfo(realm, classInfo(realm, table), col)
            val value = alloc<realm_value_t>()
            realm_wrapper.realm_get_value(obj.cptr(), propertyInfo.key.readValue(), value.ptr)
            when (value.type) {
                realm_value_type.RLM_TYPE_STRING ->
                    return value.string.toKString()
                // FIXME Where should we handle nullability. Current prototype does not allow nulls
                //  realm_value_type.RLM_TYPE_NULL ->
                //      return null
                //  https://github.com/realm/realm-kotlin/issues/71
                else ->
                    error("Expected String property got ${value.type.name}")
            }
        }
    }

    // Invoked from compiler plugin generated code
    actual fun objectSetString(
        realm: NativePointer?,
        obj: NativePointer?,
        table: String,
        col: String,
        value: String
    ) {
        if (realm == null || obj == null) {
            throw IllegalStateException("Invalid/deleted object")
        }
        memScoped {
            val propertyInfo = propertyInfo(realm, classInfo(realm, table), col)
            realm_wrapper.realm_set_value_string(
                obj.cptr(),
                propertyInfo.key.readValue(),
                value.toRString(memScope),
                false
            )
        }
    }

    actual fun realm_query_parse(
        realm: NativePointer,
        table: String,
        query: String,
        vararg args: Any
    ): NativePointer {
        memScoped {
            val count = args.size
            val cArgs = allocArray<realm_value_t>(count)
            args.mapIndexed { i, arg ->
                cArgs[i].apply {
                    set(memScope, arg)
                }
            }
            return CPointerWrapper(
                realm_wrapper.realm_query_parse(
                    realm.cptr(),
                    classInfo(realm, table).key.readValue(),
                    query.toRString(memScope),
                    count.toULong(),
                    cArgs
                )
            )
        }
    }

    actual fun realm_query_find_first(realm: NativePointer): Link? {
        memScoped {
            val found = alloc<BooleanVar>()
            val value = alloc<realm_value_t>()
            throwOnError(realm_wrapper.realm_query_find_first(realm.cptr(), value.ptr, found.ptr))
            if (!found.value) {
                return null
            }
            if (value.type != realm_value_type.RLM_TYPE_LINK) {
                error("Query did not return link but ${value.type}")
            }
            return Link(value.link.target.obj_key, value.link.target_table.table_key.toLong())
        }
    }

    actual fun realm_query_find_all(query: NativePointer): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_query_find_all(query.cptr()))
    }

    actual fun realm_results_count(results: NativePointer): Long {
        memScoped {
            val count = alloc<ULongVar>()
            throwOnError(realm_wrapper.realm_results_count(results.cptr(), count.ptr))
            return count.value.toLong()
        }
    }

    actual fun <T> realm_results_get(results: NativePointer, index: Long): Link {
        memScoped {
            val value = alloc<realm_value_t>()
            throwOnError(realm_wrapper.realm_results_get(results.cptr(), index.toULong(), value.ptr))
            return value.asLink()
        }
    }

    actual fun realm_get_object(realm: NativePointer, link: Link): NativePointer {
        val tableKey = cValue<realm_table_key_t> { table_key = link.tableKey.toUInt() }
        val objKey = cValue<realm_obj_key> { obj_key = link.objKey }
        val ptr = throwOnError(realm_wrapper.realm_get_object(realm.cptr(), tableKey, objKey))
        return CPointerWrapper(ptr)
    }

    actual fun realm_results_delete_all(results: NativePointer) {
        throwOnError(realm_wrapper.realm_results_delete_all(results.cptr()))
    }

    actual fun objectGetInteger(realm: NativePointer?, o: NativePointer?, table: String, col: String): Long {
        if (realm == null || o == null) {
            throw IllegalStateException("Invalid/deleted object")
        }
        memScoped {
            val propertyInfo = propertyInfo(realm, classInfo(realm, table), col)
            val value = alloc<realm_value_t>()
            realm_wrapper.realm_get_value(o.cptr(), propertyInfo.key.readValue(), value.ptr)
            when (value.type) {
                realm_value_type.RLM_TYPE_INT ->
                    return value.integer
                // FIXME Where should we handle nullability. Current prototype does not allow nulls
                // realm_value_type.RLM_TYPE_NULL ->
                //     return null
                else ->
                    error("Expected Int property got ${value.type.name}")
            }
        }
    }

    actual fun objectSetInteger(realm: NativePointer?, o: NativePointer?, table: String, col: String, value: Long) {
        if (realm == null || o == null) {
            throw IllegalStateException("Cannot update deleted object")
        }
        memScoped {
            val propertyInfo = propertyInfo(realm, classInfo(realm, table), col)
            realm_wrapper.realm_set_value_int64(o.cptr(), propertyInfo.key.readValue(), value, false)
        }
    }

    actual fun objectGetBoolean(realm: NativePointer?, o: NativePointer?, table: String, col: String): Boolean {
        if (realm == null || o == null) {
            throw IllegalStateException("Invalid/deleted object")
        }
        memScoped {
            val propertyInfo = propertyInfo(realm, classInfo(realm, table), col)
            val value = alloc<realm_value_t>()
            realm_wrapper.realm_get_value(o.cptr(), propertyInfo.key.readValue(), value.ptr)
            when (value.type) {
                realm_value_type.RLM_TYPE_BOOL ->
                    return value.boolean
                // FIXME Where should we handle nullability. Current prototype does not allow nulls
                // realm_value_type.RLM_TYPE_NULL ->
                //     return null
                else ->
                    error("Expected Boolean property got ${value.type.name}")
            }
        }
    }

    actual fun objectSetBoolean(realm: NativePointer?, o: NativePointer?, table: String, col: String, value: Boolean) {
        if (realm == null || o == null) {
            throw IllegalStateException("Cannot update deleted object")
        }
        memScoped {
            val propertyInfo = propertyInfo(realm, classInfo(realm, table), col)
            realm_wrapper.realm_set_value_boolean(o.cptr(), propertyInfo.key.readValue(), value, false)
        }
    }

    actual fun objectGetFloat(realm: NativePointer?, o: NativePointer?, table: String, col: String): Float {
        if (realm == null || o == null) {
            throw IllegalStateException("Invalid/deleted object")
        }
        memScoped {
            val propertyInfo = propertyInfo(realm, classInfo(realm, table), col)
            val value = alloc<realm_value_t>()
            realm_wrapper.realm_get_value(o.cptr(), propertyInfo.key.readValue(), value.ptr)
            when (value.type) {
                realm_value_type.RLM_TYPE_FLOAT ->
                    return value.fnum
                // FIXME Where should we handle nullability. Current prototype does not allow nulls
                // realm_value_type.RLM_TYPE_NULL ->
                //     return null
                else ->
                    error("Expected Float property got ${value.type.name}")
            }
        }
    }

    actual fun objectSetFloat(realm: NativePointer?, o: NativePointer?, table: String, col: String, value: Float) {
        if (realm == null || o == null) {
            throw IllegalStateException("Cannot update deleted object")
        }
        memScoped {
            val propertyInfo = propertyInfo(realm, classInfo(realm, table), col)
            realm_wrapper.realm_set_value_float(o.cptr(), propertyInfo.key.readValue(), value, false)
        }
    }

    actual fun objectGetDouble(realm: NativePointer?, o: NativePointer?, table: String, col: String): Double {
        if (realm == null || o == null) {
            throw IllegalStateException("Invalid/deleted object")
        }
        memScoped {
            val propertyInfo = propertyInfo(realm, classInfo(realm, table), col)
            val value = alloc<realm_value_t>()
            realm_wrapper.realm_get_value(o.cptr(), propertyInfo.key.readValue(), value.ptr)
            when (value.type) {
                realm_value_type.RLM_TYPE_DOUBLE ->
                    return value.dnum
                // FIXME Where should we handle nullability. Current prototype does not allow nulls
                // realm_value_type.RLM_TYPE_NULL ->
                //     return null
                else ->
                    error("Expected Double property got ${value.type.name}")
            }
        }
    }

    actual fun objectSetDouble(realm: NativePointer?, o: NativePointer?, table: String, col: String, value: Double) {
        if (realm == null || o == null) {
            throw IllegalStateException("Cannot update deleted object")
        }
        memScoped {
            val propertyInfo = propertyInfo(realm, classInfo(realm, table), col)
            realm_wrapper.realm_set_value_double(o.cptr(), propertyInfo.key.readValue(), value, false)
        }
    }

    actual fun realm_object_delete(obj: NativePointer) {
        throwOnError(realm_wrapper.realm_object_delete(obj.cptr()))
    }

    actual fun realm_object_add_notification_callback(obj: NativePointer, callback: Callback) {
        // FIXME NOTIFICATION Handle returned notification token
        // FIXME Clean up debug output
        val scheduler = realm_wrapper.realm_scheduler_make_default()
        println("Scheduler: ${realm_wrapper.realm_scheduler_has_default_factory()}")
        println("Scheduler: ${realm_wrapper.realm_scheduler_is_on_thread(scheduler)}")
        println("Scheduler: ${realm_wrapper.realm_scheduler_can_deliver_notifications(scheduler)}")

        realm_wrapper.realm_object_add_notification_callback(
            obj.cptr(),
            // Use the callback as user data
            StableRef.create(callback).asCPointer(),
            // FIXME NOTIFICATION Free userdata callback
            staticCFunction<COpaquePointer?, Unit> { },
            // Change callback
            staticCFunction<COpaquePointer?, CPointer<realm_wrapper.realm_object_changes_t>?, Unit> { userdata, change ->
                val asStableRef: StableRef<Callback> = userdata!!.asStableRef()
                // FIXME NOTIFICATION Verify memory scope of change
                asStableRef.get().onChange(CPointerWrapper(change))
            },
            // FIXME NOTIFICATION Error callback
            staticCFunction<COpaquePointer?, CPointer<realm_wrapper.realm_async_error_t>?, Unit> { userdata, error -> },
            // FIXME NOTIFICATION C-API currently uses the realm's default scheduler
            null
        )
    }

    actual fun realm_results_add_notification_callback(results: NativePointer, callback: Callback) {
        realm_wrapper.realm_results_add_notification_callback(
            results.cptr(),
            // Use the callback as user data
            StableRef.create(callback).asCPointer(),
            // FIXME NOTIFICATION Free userdata callback
            staticCFunction<COpaquePointer?, Unit> { },
            // Change callback
            staticCFunction<COpaquePointer?, CPointer<realm_wrapper.realm_collection_changes_t>?, Unit> { userdata, change ->
                val asStableRef: StableRef<Callback> = userdata!!.asStableRef()
                // FIXME NOTIFICATION Verify memory scope of change
                asStableRef.get().onChange(CPointerWrapper(change))
            },
            // FIXME NOTIFICATION Error callback
            staticCFunction<COpaquePointer?, CPointer<realm_wrapper.realm_async_error_t>?, Unit> { userdata, error -> },
            // FIXME NOTIFICATION C-API currently uses the realm's default scheduler
            null
        )
    }

    private fun MemScope.classInfo(realm: NativePointer, table: String): realm_class_info_t {
        val found = alloc<BooleanVar>()
        val classInfo = alloc<realm_class_info_t>()
        throwOnError(
            realm_wrapper.realm_find_class(
                realm.cptr(),
                table.toRString(memScope),
                found.ptr,
                classInfo.ptr
            )
        )
        return classInfo
    }

    private fun MemScope.propertyInfo(
        realm: NativePointer,
        classInfo: realm_class_info_t,
        col: String
    ): realm_property_info_t {
        val found = alloc<BooleanVar>()
        val propertyInfo = alloc<realm_property_info_t>()
        throwOnError(
            realm_find_property(
                realm.cptr(),
                classInfo.key.readValue(),
                col.toRString(memScope),
                found.ptr,
                propertyInfo.ptr
            )
        )
        return propertyInfo
    }

    private fun realm_value_t.asLink(): Link {
        if (this.type != realm_value_type.RLM_TYPE_LINK) {
            error("Value is not of type link: $this.type")
        }
        return Link(this.link.target.obj_key, this.link.target_table.table_key.toLong())
    }
}
