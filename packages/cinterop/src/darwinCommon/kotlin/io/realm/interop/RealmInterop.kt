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

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValue
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
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import realm_wrapper.realm_class_info_t
import realm_wrapper.realm_clear_last_error
import realm_wrapper.realm_config_t
import realm_wrapper.realm_error_t
import realm_wrapper.realm_find_property
import realm_wrapper.realm_get_last_error
import realm_wrapper.realm_link_t
import realm_wrapper.realm_property_info_t
import realm_wrapper.realm_release
import realm_wrapper.realm_string_t
import realm_wrapper.realm_value_t
import realm_wrapper.realm_value_type
import realm_wrapper.realm_version_id_t
import kotlin.native.concurrent.freeze
import kotlin.native.internal.createCleaner

private fun throwOnError() {
    memScoped {
        val error = alloc<realm_error_t>()
        if (realm_get_last_error(error.ptr)) {
            val runtimeException = RuntimeException(error.message?.toKString())
            realm_clear_last_error()
            // FIXME Extract all error information and throw exceptions based on type
            //  https://github.com/realm/realm-kotlin/issues/70
            throw runtimeException
        }
    }
}

private fun checkedBooleanResult(boolean: Boolean): Boolean {
    if (!boolean) throwOnError(); return boolean
}

private fun checkedPointerResult(pointer: CPointer<out CPointed>?): CPointer<out CPointed>? {
    if (pointer == null) throwOnError(); return pointer
}

// FIXME API-INTERNAL Consider making NativePointer/CPointerWrapper generic to enforce typing

class CPointerWrapper(ptr: CPointer<out CPointed>?, managed: Boolean = true) : NativePointer {
    val ptr: CPointer<out CPointed>? = checkedPointerResult(ptr)

    @OptIn(ExperimentalStdlibApi::class)
    val cleaner = if (managed) {
        createCleaner(ptr.freeze()) {
            realm_release(it)
        }
    } else null
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

    actual fun realm_get_version_id(realm: NativePointer): Pair<Long, Long> {
        memScoped {
            val info = alloc<realm_version_id_t>()
            val found = alloc<BooleanVar>()
            checkedBooleanResult(realm_wrapper.realm_get_version_id(realm.cptr(), found.ptr, info.ptr))
            return if (found.value) {
                Pair(info.version.toLong(), info.index.toLong())
            } else {
                throw IllegalStateException("No VersionId was available. Reading the VersionId requires a valid read transaction.")
            }
        }
    }

    actual fun realm_get_num_versions(realm: NativePointer): Long {
        memScoped {
            val versionsCount = alloc<ULongVar>()
            checkedBooleanResult(realm_wrapper.realm_get_num_versions(realm.cptr(), versionsCount.ptr))
            return versionsCount.value.toLong()
        }
    }

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
                    name = clazz.name.cstr.ptr
                    primary_key = (clazz.primaryKey ?: "").cstr.ptr
                    num_properties = properties.size.toULong()
                    num_computed_properties = 0U
                    flags =
                        clazz.flags.fold(0) { flags, element -> flags or element.nativeValue.toInt() }
                }
                cproperties[i] =
                    allocArray<realm_property_info_t>(properties.size).getPointer(memScope)
                for ((j, property) in properties.withIndex()) {
                    cproperties[i]!![j].apply {
                        name = property.name.cstr.ptr
                        public_name = "".cstr.ptr
                        link_target = property.linkTarget.cstr.ptr
                        link_origin_property_name = "".cstr.ptr
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
        realm_wrapper.realm_config_set_path(config.cptr(), path)
    }

    actual fun realm_config_set_schema_mode(config: NativePointer, mode: SchemaMode) {
        realm_wrapper.realm_config_set_schema_mode(
            config.cptr(),
            mode.nativeValue
        )
    }

    actual fun realm_config_set_schema_version(config: NativePointer, version: Long) {
        realm_wrapper.realm_config_set_schema_version(
            config.cptr(),
            version.toULong()
        )
    }

    actual fun realm_config_set_max_number_of_active_versions(config: NativePointer, maxNumberOfVersions: Long) {
        realm_wrapper.realm_config_set_schema_version(config.cptr(), maxNumberOfVersions.toULong())
    }

    actual fun realm_config_set_schema(config: NativePointer, schema: NativePointer) {
        realm_wrapper.realm_config_set_schema(config.cptr(), schema.cptr())
    }

    actual fun realm_schema_validate(schema: NativePointer, mode: SchemaValidationMode): Boolean {
        return checkedBooleanResult(realm_wrapper.realm_schema_validate(schema.cptr(), mode.nativeValue.toULong()))
    }

    actual fun realm_open(config: NativePointer): NativePointer {
        val realmPtr = CPointerWrapper(realm_wrapper.realm_open(config.cptr<realm_config_t>()))
        realm_begin_read(realmPtr)
        return realmPtr
    }

    actual fun realm_close(realm: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_close(realm.cptr()))
    }

    actual fun realm_get_schema(realm: NativePointer): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_get_schema(realm.cptr()))
    }

    actual fun realm_get_num_classes(realm: NativePointer): Long {
        return realm_wrapper.realm_get_num_classes(realm.cptr()).toLong()
    }

    actual fun realm_release(p: NativePointer) {
        realm_wrapper.realm_release((p as CPointerWrapper).ptr)
    }

    actual fun realm_is_closed(realm: NativePointer): Boolean {
        return realm_wrapper.realm_is_closed(realm.cptr())
    }

    actual fun realm_begin_read(realm: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_begin_read(realm.cptr()))
    }

    actual fun realm_begin_write(realm: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_begin_write(realm.cptr()))
    }

    actual fun realm_commit(realm: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_commit(realm.cptr()))
    }

    actual fun realm_rollback(realm: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_rollback(realm.cptr()))
    }

    actual fun realm_find_class(realm: NativePointer, name: String): Long {
        memScoped {
            val found = alloc<BooleanVar>()
            val classInfo = alloc<realm_class_info_t>()
            checkedBooleanResult(
                realm_wrapper.realm_find_class(
                    realm.cptr(),
                    name,
                    found.ptr,
                    classInfo.ptr
                )
            )
            if (!found.value) {
                throw RuntimeException("Class \"$name\" not found")
            }
            return classInfo.key.toLong()
        }
    }

    actual fun realm_object_create(realm: NativePointer, key: Long): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_object_create(realm.cptr(), key.toUInt()))
    }
    actual fun realm_object_create_with_primary_key(realm: NativePointer, key: Long, primaryKey: Any?): NativePointer {
        memScoped {
            return CPointerWrapper(realm_wrapper.realm_object_create_with_primary_key_by_ref(realm.cptr(), key.toUInt(), to_realm_value(primaryKey).ptr))
        }
    }

    actual fun realm_object_as_link(obj: NativePointer): Link {
        val link: CValue<realm_link_t /* = realm_wrapper.realm_link */> = realm_wrapper.realm_object_as_link(obj.cptr())
        link.useContents {
            return Link(this.target_table.toLong(), this.target)
        }
    }

    actual fun realm_get_col_key(realm: NativePointer, table: String, col: String): ColumnKey {
        memScoped {
            return ColumnKey(propertyInfo(realm, classInfo(realm, table), col).key)
        }
    }

    actual fun <T> realm_get_value(obj: NativePointer, key: ColumnKey): T {
        memScoped {
            val value: realm_value_t = alloc()
            checkedBooleanResult(realm_wrapper.realm_get_value(obj.cptr(), key.key, value.ptr))
            return from_realm_value(value)
        }
    }

    private fun <T> from_realm_value(value: realm_value_t): T {
        return when (value.type) {
            realm_value_type.RLM_TYPE_NULL ->
                null as T
            realm_value_type.RLM_TYPE_INT ->
                value.integer
            realm_value_type.RLM_TYPE_BOOL ->
                value.boolean
            realm_value_type.RLM_TYPE_STRING ->
                value.string.toKString()
            realm_value_type.RLM_TYPE_FLOAT ->
                value.fnum
            realm_value_type.RLM_TYPE_DOUBLE ->
                value.dnum
            realm_value_type.RLM_TYPE_LINK ->
                value.asLink()
            else ->
                TODO("Unsupported type for from_realm_value ${value.type.name}")
        } as T
    }

    actual fun <T> realm_set_value(o: NativePointer, key: ColumnKey, value: T, isDefault: Boolean) {
        memScoped {
            checkedBooleanResult(realm_wrapper.realm_set_value_by_ref(o.cptr(), key.key, to_realm_value(value).ptr, isDefault))
        }
    }

    private fun <T> MemScope.to_realm_value(value: T): realm_value_t {
        val cvalue: realm_value_t = alloc()
        when (value) {
            null -> {
                cvalue.type = realm_value_type.RLM_TYPE_NULL
            }
            is Byte -> {
                cvalue.type = realm_value_type.RLM_TYPE_INT
                cvalue.integer = value.toLong()
            }
            is Char -> {
                cvalue.type = realm_value_type.RLM_TYPE_INT
                cvalue.integer = value.toLong()
            }
            is Short -> {
                cvalue.type = realm_value_type.RLM_TYPE_INT
                cvalue.integer = value.toLong()
            }
            is Int -> {
                cvalue.type = realm_value_type.RLM_TYPE_INT
                cvalue.integer = value.toLong()
            }
            is Long -> {
                cvalue.type = realm_value_type.RLM_TYPE_INT
                cvalue.integer = value as Long
            }
            is Boolean -> {
                cvalue.type = realm_value_type.RLM_TYPE_BOOL
                cvalue.boolean = value as Boolean
            }
            is String -> {
                cvalue.type = realm_value_type.RLM_TYPE_STRING
                cvalue.string.set(this, value as String)
            }
            is Float -> {
                cvalue.type = realm_value_type.RLM_TYPE_FLOAT
                cvalue.fnum = value as Float
            }
            is Double -> {
                cvalue.type = realm_value_type.RLM_TYPE_DOUBLE
                cvalue.dnum = value as Double
            }
            is RealmModelInternal -> {
                cvalue.type = realm_value_type.RLM_TYPE_LINK
                val nativePointer = value.`$realm$ObjectPointer` ?: error("Cannot set unmanaged object")
                realm_wrapper.realm_object_as_link(nativePointer?.cptr()).useContents {
                    cvalue.link.apply {
                        target_table = this@useContents.target_table
                        target = this@useContents.target
                    }
                }
            }
            //    RLM_TYPE_BINARY,
            //    RLM_TYPE_TIMESTAMP,
            //    RLM_TYPE_DECIMAL128,
            //    RLM_TYPE_OBJECT_ID,
            //    RLM_TYPE_UUID,
            else -> {
                TODO("Unsupported type for to_realm_value `${value!!::class.simpleName}`")
            }
        }
        return cvalue
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
                    classInfo(realm, table).key,
                    query,
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
            checkedBooleanResult(realm_wrapper.realm_query_find_first(realm.cptr(), value.ptr, found.ptr))
            if (!found.value) {
                return null
            }
            if (value.type != realm_value_type.RLM_TYPE_LINK) {
                error("Query did not return link but ${value.type}")
            }
            return Link(value.link.target, value.link.target_table.toLong())
        }
    }

    actual fun realm_query_find_all(query: NativePointer): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_query_find_all(query.cptr()))
    }

    actual fun realm_results_count(results: NativePointer): Long {
        memScoped {
            val count = alloc<ULongVar>()
            checkedBooleanResult(realm_wrapper.realm_results_count(results.cptr(), count.ptr))
            return count.value.toLong()
        }
    }

    actual fun <T> realm_results_get(results: NativePointer, index: Long): Link {
        memScoped {
            val value = alloc<realm_value_t>()
            checkedBooleanResult(realm_wrapper.realm_results_get(results.cptr(), index.toULong(), value.ptr))
            return value.asLink()
        }
    }

    actual fun realm_get_object(realm: NativePointer, link: Link): NativePointer {
        val ptr = checkedPointerResult(realm_wrapper.realm_get_object(realm.cptr(), link.tableKey.toUInt(), link.objKey))
        return CPointerWrapper(ptr)
    }

    actual fun realm_results_delete_all(results: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_results_delete_all(results.cptr()))
    }

    actual fun realm_object_delete(obj: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_object_delete(obj.cptr()))
    }

    actual fun realm_object_add_notification_callback(obj: NativePointer, callback: Callback): NativePointer {
        return CPointerWrapper(
            realm_wrapper.realm_object_add_notification_callback(
                obj.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction<COpaquePointer?, Unit> { userdata ->
                    userdata?.asStableRef<Callback>()?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                // Change callback
                staticCFunction<COpaquePointer?, CPointer<realm_wrapper.realm_object_changes_t>?, Unit> { userdata, change ->
                    userdata?.asStableRef<Callback>()?.get()?.onChange(CPointerWrapper(change, managed = false)) // FIXME use managed pointer https://github.com/realm/realm-kotlin/issues/147
                        ?: error("Notification callback data should never be null")
                },
                // FIXME API-NOTIFICATION Error callback, C-API realm_get_async_error not available yet
                staticCFunction<COpaquePointer?, CPointer<realm_wrapper.realm_async_error_t>?, Unit> { userdata, asyncError -> },
                // FIXME NOTIFICATION C-API currently uses the realm's default scheduler
                null
            ),
            managed = false
        )
    }

    actual fun realm_results_add_notification_callback(results: NativePointer, callback: Callback): NativePointer {
        return CPointerWrapper(
            realm_wrapper.realm_results_add_notification_callback(
                results.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction<COpaquePointer?, Unit> { userdata ->
                    userdata?.asStableRef<Callback>()?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                // Change callback
                staticCFunction<COpaquePointer?, CPointer<realm_wrapper.realm_collection_changes_t>?, Unit> { userdata, change ->
                    userdata?.asStableRef<Callback>()?.get()?.onChange(CPointerWrapper(change, managed = false)) // FIXME use managed pointer https://github.com/realm/realm-kotlin/issues/147
                        ?: error("Notification callback data should never be null")
                },
                // FIXME API-NOTIFICATION Error callback, C-API realm_get_async_error not available yet
                staticCFunction<COpaquePointer?, CPointer<realm_wrapper.realm_async_error_t>?, Unit> { userdata, asyncError -> },
                // FIXME NOTIFICATION C-API currently uses the realm's default scheduler
                null
            ),
            managed = false
        )
    }

    private fun MemScope.classInfo(realm: NativePointer, table: String): realm_class_info_t {
        val found = alloc<BooleanVar>()
        val classInfo = alloc<realm_class_info_t>()
        checkedBooleanResult(
            realm_wrapper.realm_find_class(
                realm.cptr(),
                table,
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
        checkedBooleanResult(
            realm_find_property(
                realm.cptr(),
                classInfo.key,
                col,
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
        return Link(this.link.target_table.toLong(), this.link.target)
    }
}
